package com.eikins.minegoals.progress;

import com.eikins.minegoals.data.Category;
import com.eikins.minegoals.data.Goal;
import com.eikins.minegoals.data.GoalDefinitions;
import com.eikins.minegoals.network.MineGoalsNetwork;
import com.eikins.minegoals.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatType;
import net.minecraft.stats.Stats;

import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/** Server-side goal logic: counts progress, completes goals, hands out claimed rewards and syncs clients. */
public final class GoalTracker {

    private static final Set<StatType<?>> TRACKED_STATS =
            Set.of(Stats.BLOCK_MINED, Stats.ENTITY_KILLED, Stats.ITEM_PICKED_UP, Stats.ITEM_CRAFTED);

    private GoalTracker() {
    }

    /** Called for every vanilla stat increment of a player. */
    public static void onStat(ServerPlayer player, Stat<?> stat, int delta) {
        if (delta <= 0 || !TRACKED_STATS.contains(stat.getType())) {
            return;
        }
        GoalDefinitions defs = GoalDefinitions.server();
        GoalProgressData data = GoalProgressData.get(player.level().getServer());
        PlayerGoalProgress progress = data.get(player.getUUID());

        boolean changed = false;
        for (Map.Entry<Identifier, Goal> entry : defs.goals().entrySet()) {
            Goal goal = entry.getValue();
            int gain = goal.criterion().onStat(stat, delta);
            if (gain > 0 && progress.isActive(defs, entry.getKey())) {
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

        boolean changed = false;
        for (Map.Entry<Identifier, Goal> entry : defs.goals().entrySet()) {
            Goal goal = entry.getValue();
            OptionalInt value = goal.criterion().poll(player);
            if (value.isPresent() && progress.isActive(defs, entry.getKey())) {
                changed |= progress.setCount(entry.getKey(), value.getAsInt(), goal.count());
            }
        }
        if (changed) {
            evaluate(player, data, progress, defs, true);
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
            defs.goals().get(id).rewards().grant(player);
        } else {
            if (!progress.canClaimTier(defs, id, tier)) {
                return;
            }
            progress.markTierClaimed(id, tier);
            defs.categories().get(id).tiers().get(tier - 1).rewards().grant(player);
        }
        data.setDirty();
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

    private static void notify(ServerPlayer player, Component title, Component message, boolean hasReward) {
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
        Services.NETWORK.sendToPlayer(player, new MineGoalsNetwork.Notify(title, message));
    }
}
