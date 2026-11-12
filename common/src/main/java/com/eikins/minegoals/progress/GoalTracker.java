package com.eikins.minegoals.progress;

import com.eikins.minegoals.FeatureFlags;
import com.eikins.minegoals.data.Category;
import com.eikins.minegoals.data.Goal;
import com.eikins.minegoals.data.GoalDefinitions;
import com.eikins.minegoals.data.Rewards;
import com.eikins.minegoals.network.MineGoalsNetwork;
import com.eikins.minegoals.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatType;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.ToIntFunction;

/** Server-side goal logic: counts progress, completes goals, hands out claimed rewards and syncs clients. */
public final class GoalTracker {

    private static final Set<StatType<?>> TRACKED_STATS =
            Set.of(Stats.BLOCK_MINED, Stats.ENTITY_KILLED, Stats.ITEM_PICKED_UP, Stats.ITEM_CRAFTED, Stats.ITEM_USED,
                    Stats.CUSTOM);

    private GoalTracker() {
    }

    /** Called for every vanilla stat increment of a player. */
    public static void onStat(ServerPlayer player, Stat<?> stat, int delta) {
        if (delta <= 0 || !TRACKED_STATS.contains(stat.getType())) {
            return;
        }
        addProgress(player, criterion -> criterion.onStat(stat, delta));
    }

    /** Called when a player takes a brewed potion out of a brewing stand. */
    public static void onBrew(ServerPlayer player, PotionContents contents) {
        addProgress(player, criterion -> criterion.onBrew(contents));
    }

    /** Adds the progress each goal's criterion reports for an event to the goals being tracked. */
    private static void addProgress(ServerPlayer player, ToIntFunction<com.eikins.minegoals.data.criterion.GoalCriterion> gainOf) {
        GoalDefinitions defs = GoalDefinitions.server();
        GoalProgressData data = GoalProgressData.get(player.level().getServer());
        PlayerGoalProgress progress = data.get(player.getUUID());

        boolean changed = false;
        for (Map.Entry<Identifier, Goal> entry : defs.goals().entrySet()) {
            Goal goal = entry.getValue();
            int gain = gainOf.applyAsInt(goal.criterion());
            if (gain > 0 && progress.isTracking(defs, entry.getKey())) {
                changed |= progress.setCount(entry.getKey(), progress.count(entry.getKey()) + gain, goal.count());
            }
        }
        if (changed) {
            evaluate(player, data, progress, defs, true);
        }
    }

    /** Called once per second for each player, for criteria that poll the player's state. */
    public static void tickPlayer(ServerPlayer player) {
        GoalDefinitions defs = GoalDefinitions.server();
        GoalProgressData data = GoalProgressData.get(player.level().getServer());
        PlayerGoalProgress progress = data.get(player.getUUID());

        boolean changed = unlockCategories(player, progress, defs);
        for (Map.Entry<Identifier, Goal> entry : defs.goals().entrySet()) {
            Goal goal = entry.getValue();
            OptionalInt value = goal.criterion().poll(player);
            if (value.isPresent() && progress.isTracking(defs, entry.getKey())) {
                changed |= progress.setCount(entry.getKey(), value.getAsInt(), goal.count());
            }
        }
        if (changed) {
            evaluate(player, data, progress, defs, true);
        }
    }

    /**
     * Unlocks every locked category whose advancement conditions the player now meets. Categories locked by a
     * command stay locked. Returns whether anything changed.
     */
    private static boolean unlockCategories(ServerPlayer player, PlayerGoalProgress progress, GoalDefinitions defs) {
        boolean changed = false;
        for (Map.Entry<Identifier, Category> entry : defs.categories().entrySet()) {
            Identifier categoryId = entry.getKey();
            if (progress.isCategoryUnlocked(defs, categoryId) || progress.hasCategoryOverride(categoryId)) {
                continue;
            }
            if (entry.getValue().unlock().conditionsMet(advancement -> hasAdvancement(player, advancement))) {
                progress.setCategoryUnlocked(categoryId, true);
                notifyCategoryUnlocked(player, entry.getValue());
                changed = true;
            }
        }
        return changed;
    }

    private static boolean hasAdvancement(ServerPlayer player, Identifier id) {
        AdvancementHolder holder = player.level().getServer().getAdvancements().get(id);
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    /** Locks or unlocks a category for a player. Used by {@code /minegoals lock|unlock}. */
    public static void setCategoryUnlocked(ServerPlayer player, Identifier category, boolean unlocked) {
        GoalDefinitions defs = GoalDefinitions.server();
        GoalProgressData data = GoalProgressData.get(player.level().getServer());
        PlayerGoalProgress progress = data.get(player.getUUID());
        boolean wasUnlocked = progress.isCategoryUnlocked(defs, category);
        progress.setCategoryUnlocked(category, unlocked);
        if (unlocked && !wasUnlocked && defs.categories().containsKey(category)) {
            notifyCategoryUnlocked(player, defs.categories().get(category));
        }
        evaluate(player, data, progress, defs, true);
    }

    /** Always shows a toast with the category's icon, even when {@link FeatureFlags#NOTIFICATIONS} is off. */
    private static void notifyCategoryUnlocked(ServerPlayer player, Category category) {
        Services.NETWORK.sendToPlayer(player, new MineGoalsNetwork.Notify(
                Component.translatable("minegoals.notify.category_unlocked"), category.title(),
                category.icon().builtInRegistryHolder()));
        if (FeatureFlags.NOTIFICATIONS) {
            sendChat(player, Component.translatable("minegoals.notify.category_unlocked"), category.title(), false);
        }
    }

    /** Sends definitions and progress to a player (on join and after reload), then catches up on completions. */
    public static void syncPlayer(ServerPlayer player) {
        GoalDefinitions defs = GoalDefinitions.server();
        GoalProgressData data = GoalProgressData.get(player.level().getServer());
        Services.NETWORK.sendToPlayer(player, new MineGoalsNetwork.SyncDefinitions(defs));
        evaluate(player, data, data.get(player.getUUID()), defs, true);
    }

    /** Completes a goal immediately, even if locked; its rewards become claimable. Used by {@code /minegoals complete}. */
    public static boolean forceComplete(ServerPlayer player, Identifier goalId) {
        GoalDefinitions defs = GoalDefinitions.server();
        Goal goal = defs.goals().get(goalId);
        GoalProgressData data = GoalProgressData.get(player.level().getServer());
        PlayerGoalProgress progress = data.get(player.getUUID());
        if (goal == null || progress.isCompleted(goalId)) {
            return false;
        }
        progress.setCount(goalId, goal.count(), goal.count());
        complete(player, progress, goalId, goal);
        evaluate(player, data, progress, defs, true);
        return true;
    }

    /**
     * Completes every given goal that is not completed yet, even if locked; their rewards become claimable.
     * Goals are completed silently (no per-goal notification) to avoid flooding the player; tier
     * notifications still fire. Used by {@code /minegoals complete <player> [category]}.
     *
     * @return the number of goals newly completed
     */
    public static int forceCompleteAll(ServerPlayer player, Collection<Identifier> goalIds) {
        GoalDefinitions defs = GoalDefinitions.server();
        GoalProgressData data = GoalProgressData.get(player.level().getServer());
        PlayerGoalProgress progress = data.get(player.getUUID());
        int completed = 0;
        for (Identifier goalId : goalIds) {
            Goal goal = defs.goals().get(goalId);
            if (goal == null || progress.isCompleted(goalId)) {
                continue;
            }
            progress.setCount(goalId, goal.count(), goal.count());
            progress.markCompleted(goalId);
            completed++;
        }
        evaluate(player, data, progress, defs, true);
        return completed;
    }

    /** Re-sends progress after an external change, such as a reset command. */
    public static void refresh(ServerPlayer player) {
        GoalProgressData data = GoalProgressData.get(player.level().getServer());
        evaluate(player, data, data.get(player.getUUID()), GoalDefinitions.server(), true);
    }

    /**
     * Grants the rewards of a completed goal ({@code tier == 0}) or of a reached tier, if the player can claim
     * them. Called from the {@code ClaimReward} payload; every condition is checked here, never trusted from the client.
     */
    public static void claim(ServerPlayer player, Identifier id, int tier) {
        GoalDefinitions defs = GoalDefinitions.server();
        GoalProgressData data = GoalProgressData.get(player.level().getServer());
        PlayerGoalProgress progress = data.get(player.getUUID());
        if (tier == 0) {
            if (!progress.canClaimGoal(defs, id)) {
                return;
            }
            progress.markGoalClaimed(id);
            keepLeftovers(player, progress, defs.goals().get(id).rewards().grant(player));
        } else {
            if (!progress.canClaimTier(defs, id, tier)) {
                return;
            }
            progress.markTierClaimed(id, tier);
            keepLeftovers(player, progress, defs.categories().get(id).tiers().get(tier - 1).rewards().grant(player));
        }
        // Claiming a tier unlocks the next one, whose goals may already be done when they track early.
        evaluate(player, data, progress, defs, true);
    }

    /** Stores reward items that did not fit in the inventory and tells the player where to collect them. */
    private static void keepLeftovers(ServerPlayer player, PlayerGoalProgress progress, List<ItemStack> leftovers) {
        if (leftovers.isEmpty()) {
            return;
        }
        int count = 0;
        for (ItemStack stack : leftovers) {
            progress.addPendingItem(stack);
            count += stack.getCount();
        }
        player.sendSystemMessage(Component.empty()
                .append(Component.literal("[MineGoals] ").withStyle(ChatFormatting.GOLD))
                .append(Component.translatable("minegoals.notify.inventory_full", count).withStyle(ChatFormatting.YELLOW)));
    }

    /**
     * Moves pending reward items into the player's inventory, as far as they fit; the rest stays pending. Called from
     * the {@code CollectPendingItems} payload.
     */
    public static void collectPendingItems(ServerPlayer player) {
        GoalProgressData data = GoalProgressData.get(player.level().getServer());
        PlayerGoalProgress progress = data.get(player.getUUID());
        List<ItemStack> pending = progress.takePendingItems();
        if (pending.isEmpty()) {
            return;
        }
        for (ItemStack stack : pending) {
            ItemStack rest = Rewards.giveItem(player, stack.copy());
            if (!rest.isEmpty()) {
                progress.addPendingItem(rest);
            }
        }
        data.setDirty();
        Services.NETWORK.sendToPlayer(player, new MineGoalsNetwork.SyncProgress(progress.copy()));
    }

    /**
     * Starts or stops tracking a goal for the player. Called from the {@code TrackGoal} payload; completed or unknown
     * goals cannot be tracked.
     */
    public static void setTracked(ServerPlayer player, Identifier goalId, boolean tracked) {
        GoalDefinitions defs = GoalDefinitions.server();
        GoalProgressData data = GoalProgressData.get(player.level().getServer());
        PlayerGoalProgress progress = data.get(player.getUUID());
        if (tracked && (!defs.goals().containsKey(goalId) || progress.isCompleted(goalId))) {
            return;
        }
        if (progress.setTracked(goalId, tracked)) {
            data.setDirty();
        }
        // Always sync, so a client that toggled optimistically gets the real state back.
        Services.NETWORK.sendToPlayer(player, new MineGoalsNetwork.SyncProgress(progress.copy()));
    }

    /**
     * Starts or stops tracking every goal of a tier that is not completed yet. Called from the {@code TrackTier}
     * payload; unknown categories and tiers are ignored.
     */
    public static void setTierTracked(ServerPlayer player, Identifier categoryId, int tier, boolean tracked) {
        GoalDefinitions defs = GoalDefinitions.server();
        Category category = defs.categories().get(categoryId);
        if (category == null || tier < 1 || tier > category.tiers().size()) {
            return;
        }
        GoalProgressData data = GoalProgressData.get(player.level().getServer());
        PlayerGoalProgress progress = data.get(player.getUUID());
        if (progress.setTierTracked(defs, categoryId, tier, tracked)) {
            data.setDirty();
        }
        // Always sync, so a client that toggled optimistically gets the real state back.
        Services.NETWORK.sendToPlayer(player, new MineGoalsNetwork.SyncProgress(progress.copy()));
    }

    /**
     * Completes every goal that reached its count and announces newly reached tiers, repeating until nothing
     * changes (a new tier can unlock goals). Rewards are not granted here: the player claims them from the
     * goals screen. Then saves and syncs.
     */
    private static void evaluate(ServerPlayer player, GoalProgressData data, PlayerGoalProgress progress,
                                 GoalDefinitions defs, boolean sync) {
        boolean progressed = true;
        while (progressed) {
            progressed = false;
            for (Map.Entry<Identifier, Goal> entry : defs.goals().entrySet()) {
                Identifier id = entry.getKey();
                Goal goal = entry.getValue();
                OptionalInt derived = goal.criterion().fromProgress(progress, defs);
                if (derived.isPresent() && progress.isTracking(defs, id)) {
                    progress.setCount(id, derived.getAsInt(), goal.count());
                }
                if (progress.isActive(defs, id) && progress.count(id) >= goal.count()) {
                    complete(player, progress, id, goal);
                    progressed = true;
                }
            }
            for (Map.Entry<Identifier, Category> entry : defs.categories().entrySet()) {
                Identifier categoryId = entry.getKey();
                Category category = entry.getValue();
                int reached = progress.tier(defs, categoryId);
                int announced = progress.announcedTier(categoryId);
                for (int tier = Math.max(2, announced + 1); tier <= reached; tier++) {
                    notify(player, Component.translatable("minegoals.notify.tier_reached", tier), category.title(),
                            !category.tiers().get(tier - 1).rewards().isEmpty());
                }
                if (reached > announced) {
                    progress.setAnnouncedTier(categoryId, reached);
                    progressed = true;
                }
            }
        }
        data.setDirty();
        if (sync) {
            Services.NETWORK.sendToPlayer(player, new MineGoalsNetwork.SyncProgress(progress.copy()));
        }
    }

    private static void complete(ServerPlayer player, PlayerGoalProgress progress, Identifier id, Goal goal) {
        progress.markCompleted(id);
        notify(player, Component.translatable("minegoals.notify.goal_completed"), goal.title(), !goal.rewards().isEmpty());
    }

    /** Goal and tier notifications (toast and chat), only when {@link FeatureFlags#NOTIFICATIONS} is on. */
    private static void notify(ServerPlayer player, Component title, Component message, boolean hasReward) {
        if (!FeatureFlags.NOTIFICATIONS) {
            return;
        }
        sendChat(player, title, message, hasReward);
        Services.NETWORK.sendToPlayer(player,
                new MineGoalsNetwork.Notify(title, message, Items.GOLDEN_APPLE.builtInRegistryHolder()));
    }

    private static void sendChat(ServerPlayer player, Component title, Component message, boolean hasReward) {
        MutableComponent chat = Component.empty()
                .append(Component.literal("[MineGoals] ").withStyle(ChatFormatting.GOLD))
                .append(title.copy().withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" — "))
                .append(message);
        if (hasReward) {
            chat.append(Component.literal(" "))
                    .append(Component.translatable("minegoals.notify.claim_hint").withStyle(ChatFormatting.GREEN));
        }
        player.sendSystemMessage(chat);
    }
}
