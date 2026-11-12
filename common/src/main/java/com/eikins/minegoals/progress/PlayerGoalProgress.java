package com.eikins.minegoals.progress;

import com.eikins.minegoals.Constants;
import com.eikins.minegoals.data.Category;
import com.eikins.minegoals.data.Goal;
import com.eikins.minegoals.data.GoalDefinitions;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One player's progress. Tier points and tiers are derived from claimed goals (each tier counts only its own goals),
 * so they stay consistent when a datapack changes point values. Rewards are claimed by the player from the
 * goals screen; claimed goals and tiers are stored so a reward is never granted twice. A tier's reward is earned
 * by completing the tier, and claiming it unlocks the next tier. The highest tier
 * already announced per category is stored so the "tier reached" notification fires once. Tracked goals are the ones
 * the player chose to follow: the client shows a toast when they progress. A goal stops being tracked once completed.
 * Pending items are reward items that did not fit in the player's inventory when claimed; the player collects them
 * from the goals screen.
 */
public final class PlayerGoalProgress {

    public static final Codec<PlayerGoalProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Identifier.CODEC, Codec.INT).optionalFieldOf("counts", Map.of()).forGetter(p -> p.counts),
            Identifier.CODEC.listOf().optionalFieldOf("completed", List.of()).forGetter(p -> List.copyOf(p.completed)),
            Codec.unboundedMap(Identifier.CODEC, Codec.INT).optionalFieldOf("announced_tiers", Map.of()).forGetter(p -> p.announcedTiers),
            Identifier.CODEC.listOf().optionalFieldOf("claimed_goals", List.of()).forGetter(p -> List.copyOf(p.claimedGoals)),
            Codec.unboundedMap(Identifier.CODEC, Codec.INT.listOf()).optionalFieldOf("claimed_tiers", Map.of())
                    .forGetter(p -> copyTiers(p.claimedTiers)),
            Codec.unboundedMap(Identifier.CODEC, Codec.BOOL).optionalFieldOf("category_unlocks", Map.of())
                    .forGetter(p -> p.categoryUnlocks),
            Identifier.CODEC.listOf().optionalFieldOf("tracked", List.of()).forGetter(p -> List.copyOf(p.tracked)),
            ItemStack.CODEC.listOf().optionalFieldOf("pending_items", List.of()).forGetter(p -> List.copyOf(p.pendingItems))
    ).apply(i, PlayerGoalProgress::new));

    private final Map<Identifier, Integer> counts;
    private final Set<Identifier> completed;
    private final Map<Identifier, Integer> announcedTiers;
    private final Set<Identifier> claimedGoals;
    private final Map<Identifier, Set<Integer>> claimedTiers;
    /** Explicit category lock state, overriding the category's default (set by unlock conditions or commands). */
    private final Map<Identifier, Boolean> categoryUnlocks;
    private final Set<Identifier> tracked;
    private final List<ItemStack> pendingItems;

    public PlayerGoalProgress() {
        this(Map.of(), List.of(), Map.of(), List.of(), Map.of(), Map.of(), List.of(), List.of());
    }

    private PlayerGoalProgress(Map<Identifier, Integer> counts, List<Identifier> completed, Map<Identifier, Integer> announcedTiers,
                               List<Identifier> claimedGoals, Map<Identifier, List<Integer>> claimedTiers,
                               Map<Identifier, Boolean> categoryUnlocks, List<Identifier> tracked,
                               List<ItemStack> pendingItems) {
        this.counts = new HashMap<>(counts);
        this.completed = new HashSet<>(completed);
        this.announcedTiers = new HashMap<>(announcedTiers);
        this.claimedGoals = new HashSet<>(claimedGoals);
        this.claimedTiers = new HashMap<>();
        claimedTiers.forEach((category, tiers) -> this.claimedTiers.put(category, new HashSet<>(tiers)));
        this.categoryUnlocks = new HashMap<>(categoryUnlocks);
        this.tracked = new HashSet<>(tracked);
        this.pendingItems = new ArrayList<>();
        pendingItems.forEach(this::addPendingItem);
    }

    private static Map<Identifier, List<Integer>> copyTiers(Map<Identifier, Set<Integer>> tiers) {
        Map<Identifier, List<Integer>> copy = new HashMap<>();
        tiers.forEach((category, set) -> copy.put(category, set.stream().sorted().toList()));
        return copy;
    }

    public PlayerGoalProgress copy() {
        return new PlayerGoalProgress(this.counts, List.copyOf(this.completed), this.announcedTiers,
                List.copyOf(this.claimedGoals), copyTiers(this.claimedTiers), this.categoryUnlocks,
                List.copyOf(this.tracked), this.pendingItems);
    }

    // ---- raw state -------------------------------------------------------------------------------

    public int count(Identifier goal) {
        return this.counts.getOrDefault(goal, 0);
    }

    public boolean isCompleted(Identifier goal) {
        return this.completed.contains(goal);
    }

    public int announcedTier(Identifier category) {
        return this.announcedTiers.getOrDefault(category, 0);
    }

    public boolean isGoalClaimed(Identifier goal) {
        return this.claimedGoals.contains(goal);
    }

    public boolean isTierClaimed(Identifier category, int tier) {
        return this.claimedTiers.getOrDefault(category, Set.of()).contains(tier);
    }

    public boolean isTracked(Identifier goal) {
        return this.tracked.contains(goal);
    }

    /** Starts or stops following a goal. Returns true if it changed. */
    public boolean setTracked(Identifier goal, boolean track) {
        return track ? this.tracked.add(goal) : this.tracked.remove(goal);
    }

    /** Goals of a tier that can be tracked: the ones not completed yet. */
    public List<Identifier> trackableGoals(GoalDefinitions defs, Identifier category, int tier) {
        List<Identifier> goals = new ArrayList<>();
        for (Identifier goal : defs.goalsIn(category)) {
            if (defs.goals().get(goal).tier() == tier && !this.isCompleted(goal)) {
                goals.add(goal);
            }
        }
        return goals;
    }

    /** True when the tier has goals left to complete and every one of them is tracked. */
    public boolean isTierTracked(GoalDefinitions defs, Identifier category, int tier) {
        List<Identifier> goals = this.trackableGoals(defs, category, tier);
        return !goals.isEmpty() && this.tracked.containsAll(goals);
    }

    /**
     * Starts following every goal of a tier that is not completed yet, or stops following every goal of the tier.
     * Returns true if anything changed.
     */
    public boolean setTierTracked(GoalDefinitions defs, Identifier category, int tier, boolean track) {
        boolean changed = false;
        for (Identifier goal : this.trackableGoals(defs, category, tier)) {
            changed |= this.setTracked(goal, track);
        }
        return changed;
    }

    /** Reward items waiting to be collected. Read-only: use {@link #addPendingItem} and {@link #takePendingItems}. */
    public List<ItemStack> pendingItems() {
        return java.util.Collections.unmodifiableList(this.pendingItems);
    }

    /** Total number of pending items (sum of stack counts). */
    public int pendingItemCount() {
        int count = 0;
        for (ItemStack stack : this.pendingItems) {
            count += stack.getCount();
        }
        return count;
    }

    /**
     * Stores a copy of a reward stack that did not fit in the inventory, merged into existing pending stacks of the
     * same item and components up to their max stack size.
     */
    public void addPendingItem(ItemStack stack) {
        ItemStack rest = stack.copy();
        for (ItemStack pending : this.pendingItems) {
            if (rest.isEmpty()) {
                return;
            }
            if (ItemStack.isSameItemSameComponents(pending, rest) && pending.getCount() < pending.getMaxStackSize()) {
                int moved = Math.min(rest.getCount(), pending.getMaxStackSize() - pending.getCount());
                pending.grow(moved);
                rest.shrink(moved);
            }
        }
        if (!rest.isEmpty()) {
            this.pendingItems.add(rest);
        }
    }

    /** Removes and returns every pending stack; whatever cannot be delivered goes back with {@link #addPendingItem}. */
    public List<ItemStack> takePendingItems() {
        List<ItemStack> taken = List.copyOf(this.pendingItems);
        this.pendingItems.clear();
        return taken;
    }

    /** Sets the count, clamped to [0, max]. Returns true if it changed. */
    public boolean setCount(Identifier goal, int value, int max) {
        int clamped = Math.max(0, Math.min(value, max));
        Integer previous = this.counts.put(goal, clamped);
        return previous == null ? clamped != 0 : previous != clamped;
    }

    /** Marks the goal completed; a completed goal is no longer tracked. */
    public void markCompleted(Identifier goal) {
        this.completed.add(goal);
        this.tracked.remove(goal);
    }

    public void setAnnouncedTier(Identifier category, int tier) {
        this.announcedTiers.put(category, tier);
    }

    public void markGoalClaimed(Identifier goal) {
        this.claimedGoals.add(goal);
    }

    public void markTierClaimed(Identifier category, int tier) {
        this.claimedTiers.computeIfAbsent(category, c -> new HashSet<>()).add(tier);
    }

    public void resetGoal(Identifier goal) {
        this.counts.remove(goal);
        this.completed.remove(goal);
        this.claimedGoals.remove(goal);
        this.tracked.remove(goal);
    }

    public void resetCategory(GoalDefinitions defs, Identifier category) {
        defs.goalsIn(category).forEach(this::resetGoal);
        this.announcedTiers.remove(category);
        this.claimedTiers.remove(category);
    }

    /** Locks or unlocks a category for this player, overriding its default. */
    public void setCategoryUnlocked(Identifier category, boolean unlocked) {
        this.categoryUnlocks.put(category, unlocked);
    }

    /** Whether the player explicitly has a lock state for the category (from conditions or commands). */
    public boolean hasCategoryOverride(Identifier category) {
        return this.categoryUnlocks.containsKey(category);
    }

    /** Resets all progress. Pending items are kept: they are rewards already earned, not progress. */
    public void resetAll() {
        this.counts.clear();
        this.completed.clear();
        this.announcedTiers.clear();
        this.claimedGoals.clear();
        this.claimedTiers.clear();
        this.categoryUnlocks.clear();
        this.tracked.clear();
    }

    // ---- derived values --------------------------------------------------------------------------

    /** Whether the category is available to this player: its explicit state if any, else its default. */
    public boolean isCategoryUnlocked(GoalDefinitions defs, Identifier category) {
        Boolean explicit = this.categoryUnlocks.get(category);
        if (explicit != null) {
            return explicit;
        }
        Category cat = defs.categories().get(category);
        return cat != null && cat.unlock().byDefault();
    }

    /** Total points earned in a category (all tiers): a goal's points count once the player has claimed it. */
    public int categoryPoints(GoalDefinitions defs, Identifier category) {
        int points = 0;
        for (Identifier id : defs.goalsIn(category)) {
            if (this.claimedGoals.contains(id)) {
                points += defs.goals().get(id).points();
            }
        }
        return points;
    }

    /**
     * Unlocked tier (1-based). Tier N+1 unlocks once tier N is complete and its reward is claimed (or it has none),
     * so the player moves through the track by claiming each tier's reward.
     */
    public int tier(GoalDefinitions defs, Identifier category) {
        Category cat = defs.categories().get(category);
        if (cat == null) {
            return 0;
        }
        int tier = 1;
        while (tier < cat.tiers().size() && this.isTierComplete(defs, category, tier)
                && (cat.tiers().get(tier - 1).rewards().isEmpty() || this.isTierClaimed(category, tier))) {
            tier++;
        }
        return tier;
    }

    /** Points earned in one tier (1-based): claimed goals of that tier only. */
    public int tierPoints(GoalDefinitions defs, Identifier category, int tier) {
        int points = 0;
        for (Identifier id : defs.goalsIn(category)) {
            Goal goal = defs.goals().get(id);
            if (goal.tier() == tier && this.claimedGoals.contains(id)) {
                points += goal.points();
            }
        }
        return points;
    }

    /** Whether tier {@code tier} (1-based) is complete: its own claimed goals reach the points it needs. */
    public boolean isTierComplete(GoalDefinitions defs, Identifier category, int tier) {
        Category cat = defs.categories().get(category);
        if (cat == null || tier < 1 || tier > cat.tiers().size()) {
            return false;
        }
        return this.tierPoints(defs, category, tier) >= cat.pointsNeeded(tier);
    }

    public int totalPoints(GoalDefinitions defs) {
        int total = 0;
        for (Identifier category : defs.categories().keySet()) {
            total += this.categoryPoints(defs, category);
        }
        return total;
    }


    public boolean isUnlocked(GoalDefinitions defs, Identifier goalId) {
        Goal goal = defs.goals().get(goalId);
        return goal != null && this.isCategoryUnlocked(defs, goal.category())
                && this.tier(defs, goal.category()) >= goal.tier() && this.requirementsMet(goal);
    }

    /**
     * Completed, not claimed yet, and its tier is unlocked. Claiming grants the goal's rewards and its points. A goal
     * that tracks before unlocking can be completed in a locked tier; it waits until the tier unlocks.
     */
    public boolean canClaimGoal(GoalDefinitions defs, Identifier goalId) {
        Goal goal = defs.goals().get(goalId);
        // Every completed goal is claimed, even without rewards, since claiming is what grants its points.
        return goal != null && this.completed.contains(goalId) && !this.claimedGoals.contains(goalId)
                && this.isCategoryUnlocked(defs, goal.category())
                && this.tier(defs, goal.category()) >= goal.tier();
    }

    /** Unlocked, complete, has rewards, and not claimed yet. Tiers are 1-based. Claiming unlocks the next tier. */
    public boolean canClaimTier(GoalDefinitions defs, Identifier categoryId, int tier) {
        Category category = defs.categories().get(categoryId);
        return category != null && tier >= 1 && tier <= category.tiers().size()
                && this.isCategoryUnlocked(defs, categoryId)
                && !category.tiers().get(tier - 1).rewards().isEmpty()
                && !this.isTierClaimed(categoryId, tier)
                && tier <= this.tier(defs, categoryId) && this.isTierComplete(defs, categoryId, tier);
    }

    /** Number of goal and tier rewards waiting to be claimed. */
    public int claimableCount(GoalDefinitions defs) {
        int count = 0;
        for (Identifier goal : defs.goals().keySet()) {
            if (this.canClaimGoal(defs, goal)) {
                count++;
            }
        }
        for (Map.Entry<Identifier, Category> entry : defs.categories().entrySet()) {
            for (int tier = 1; tier <= entry.getValue().tiers().size(); tier++) {
                if (this.canClaimTier(defs, entry.getKey(), tier)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Whether progress should be counted for this goal: not completed, requirements met, and either the goal is
     * unlocked (category and tier) or it tracks progress before unlocking ({@code track_before_unlock}).
     */
    public boolean isTracking(GoalDefinitions defs, Identifier goalId) {
        Goal goal = defs.goals().get(goalId);
        if (goal == null || this.completed.contains(goalId) || !this.requirementsMet(goal)) {
            return false;
        }
        return goal.trackBeforeUnlock()
                || (this.isCategoryUnlocked(defs, goal.category()) && this.tier(defs, goal.category()) >= goal.tier());
    }

    private boolean requirementsMet(Goal goal) {
        for (Identifier required : goal.requires()) {
            if (!this.completed.contains(required)) {
                return false;
            }
        }
        return true;
    }

    /** Unlocked and not yet completed. */
    public boolean isActive(GoalDefinitions defs, Identifier goalId) {
        return !this.completed.contains(goalId) && this.isUnlocked(defs, goalId);
    }
}
