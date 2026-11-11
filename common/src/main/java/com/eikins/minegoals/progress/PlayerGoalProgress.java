package com.eikins.minegoals.progress;

import com.eikins.minegoals.Constants;
import com.eikins.minegoals.data.Category;
import com.eikins.minegoals.data.Goal;
import com.eikins.minegoals.data.GoalDefinitions;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One player's progress. Category points, tiers and the global level are derived from completed goals,
 * so they stay consistent when a datapack changes point values. Rewards are claimed by the player from the
 * goals screen; claimed goals and tiers are stored so a reward is never granted twice. The highest tier
 * already announced per category is stored so the "tier reached" notification fires once.
 */
public final class PlayerGoalProgress {

    public static final Codec<PlayerGoalProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Identifier.CODEC, Codec.INT).optionalFieldOf("counts", Map.of()).forGetter(p -> p.counts),
            Identifier.CODEC.listOf().optionalFieldOf("completed", List.of()).forGetter(p -> List.copyOf(p.completed)),
            Codec.unboundedMap(Identifier.CODEC, Codec.INT).optionalFieldOf("announced_tiers", Map.of()).forGetter(p -> p.announcedTiers),
            Identifier.CODEC.listOf().optionalFieldOf("claimed_goals", List.of()).forGetter(p -> List.copyOf(p.claimedGoals)),
            Codec.unboundedMap(Identifier.CODEC, Codec.INT.listOf()).optionalFieldOf("claimed_tiers", Map.of())
                    .forGetter(p -> copyTiers(p.claimedTiers))
    ).apply(i, PlayerGoalProgress::new));

    private final Map<Identifier, Integer> counts;
    private final Set<Identifier> completed;
    private final Map<Identifier, Integer> announcedTiers;
    private final Set<Identifier> claimedGoals;
    private final Map<Identifier, Set<Integer>> claimedTiers;

    public PlayerGoalProgress() {
        this(Map.of(), List.of(), Map.of(), List.of(), Map.of());
    }

    private PlayerGoalProgress(Map<Identifier, Integer> counts, List<Identifier> completed, Map<Identifier, Integer> announcedTiers,
                               List<Identifier> claimedGoals, Map<Identifier, List<Integer>> claimedTiers) {
        this.counts = new HashMap<>(counts);
        this.completed = new HashSet<>(completed);
        this.announcedTiers = new HashMap<>(announcedTiers);
        this.claimedGoals = new HashSet<>(claimedGoals);
        this.claimedTiers = new HashMap<>();
        claimedTiers.forEach((category, tiers) -> this.claimedTiers.put(category, new HashSet<>(tiers)));
    }

    private static Map<Identifier, List<Integer>> copyTiers(Map<Identifier, Set<Integer>> tiers) {
        Map<Identifier, List<Integer>> copy = new HashMap<>();
        tiers.forEach((category, set) -> copy.put(category, set.stream().sorted().toList()));
        return copy;
    }

    public PlayerGoalProgress copy() {
        return new PlayerGoalProgress(this.counts, List.copyOf(this.completed), this.announcedTiers,
                List.copyOf(this.claimedGoals), copyTiers(this.claimedTiers));
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

    /** Sets the count, clamped to [0, max]. Returns true if it changed. */
    public boolean setCount(Identifier goal, int value, int max) {
        int clamped = Math.max(0, Math.min(value, max));
        Integer previous = this.counts.put(goal, clamped);
        return previous == null ? clamped != 0 : previous != clamped;
    }

    public void markCompleted(Identifier goal) {
        this.completed.add(goal);
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
    }

    public void resetCategory(GoalDefinitions defs, Identifier category) {
        defs.goalsIn(category).forEach(this::resetGoal);
        this.announcedTiers.remove(category);
        this.claimedTiers.remove(category);
    }

    public void resetAll() {
        this.counts.clear();
        this.completed.clear();
        this.announcedTiers.clear();
        this.claimedGoals.clear();
        this.claimedTiers.clear();
    }

    // ---- derived values --------------------------------------------------------------------------

    public int categoryPoints(GoalDefinitions defs, Identifier category) {
        int points = 0;
        for (Identifier id : defs.goalsIn(category)) {
            if (this.completed.contains(id)) {
                points += defs.goals().get(id).points();
            }
        }
        return points;
    }

    public int tier(GoalDefinitions defs, Identifier category) {
        Category cat = defs.categories().get(category);
        return cat == null ? 0 : cat.tierFor(this.categoryPoints(defs, category));
    }

    public int totalPoints(GoalDefinitions defs) {
        int total = 0;
        for (Identifier category : defs.categories().keySet()) {
            total += this.categoryPoints(defs, category);
        }
        return total;
    }

    /** Global level, starting at 1. */
    public int level(GoalDefinitions defs) {
        return 1 + this.totalPoints(defs) / Constants.POINTS_PER_LEVEL;
    }

    public boolean isUnlocked(GoalDefinitions defs, Identifier goalId) {
        Goal goal = defs.goals().get(goalId);
        if (goal == null || this.tier(defs, goal.category()) < goal.tier()) {
            return false;
        }
        for (Identifier required : goal.requires()) {
            if (!this.completed.contains(required)) {
                return false;
            }
        }
        return true;
    }

    /** Completed, has rewards, and not claimed yet. */
    public boolean canClaimGoal(GoalDefinitions defs, Identifier goalId) {
        Goal goal = defs.goals().get(goalId);
        return goal != null && !goal.rewards().isEmpty() && this.completed.contains(goalId) && !this.claimedGoals.contains(goalId);
    }

    /** Reached, has rewards, and not claimed yet. Tiers are 1-based. */
    public boolean canClaimTier(GoalDefinitions defs, Identifier categoryId, int tier) {
        Category category = defs.categories().get(categoryId);
        return category != null && tier >= 1 && tier <= category.tiers().size()
                && !category.tiers().get(tier - 1).rewards().isEmpty()
                && tier <= this.tier(defs, categoryId) && !this.isTierClaimed(categoryId, tier);
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

    /** Unlocked and not yet completed. */
    public boolean isActive(GoalDefinitions defs, Identifier goalId) {
        return !this.completed.contains(goalId) && this.isUnlocked(defs, goalId);
    }
}
