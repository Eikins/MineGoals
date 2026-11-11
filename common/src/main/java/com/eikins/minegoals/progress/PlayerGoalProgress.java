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
 * so they stay consistent when a datapack changes point values. Only the highest tier already rewarded
 * per category is stored, so tier rewards are never granted twice.
 */
public final class PlayerGoalProgress {

    public static final Codec<PlayerGoalProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Identifier.CODEC, Codec.INT).optionalFieldOf("counts", Map.of()).forGetter(p -> p.counts),
            Identifier.CODEC.listOf().optionalFieldOf("completed", List.of()).forGetter(p -> List.copyOf(p.completed)),
            Codec.unboundedMap(Identifier.CODEC, Codec.INT).optionalFieldOf("rewarded_tiers", Map.of()).forGetter(p -> p.rewardedTiers)
    ).apply(i, PlayerGoalProgress::new));

    private final Map<Identifier, Integer> counts;
    private final Set<Identifier> completed;
    private final Map<Identifier, Integer> rewardedTiers;

    public PlayerGoalProgress() {
        this(Map.of(), List.of(), Map.of());
    }

    private PlayerGoalProgress(Map<Identifier, Integer> counts, List<Identifier> completed, Map<Identifier, Integer> rewardedTiers) {
        this.counts = new HashMap<>(counts);
        this.completed = new HashSet<>(completed);
        this.rewardedTiers = new HashMap<>(rewardedTiers);
    }

    public PlayerGoalProgress copy() {
        return new PlayerGoalProgress(this.counts, List.copyOf(this.completed), this.rewardedTiers);
    }

    // ---- raw state -------------------------------------------------------------------------------

    public int count(Identifier goal) {
        return this.counts.getOrDefault(goal, 0);
    }

    public boolean isCompleted(Identifier goal) {
        return this.completed.contains(goal);
    }

    public int rewardedTier(Identifier category) {
        return this.rewardedTiers.getOrDefault(category, 0);
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

    public void setRewardedTier(Identifier category, int tier) {
        this.rewardedTiers.put(category, tier);
    }

    public void resetGoal(Identifier goal) {
        this.counts.remove(goal);
        this.completed.remove(goal);
    }

    public void resetCategory(GoalDefinitions defs, Identifier category) {
        defs.goalsIn(category).forEach(this::resetGoal);
        this.rewardedTiers.remove(category);
    }

    public void resetAll() {
        this.counts.clear();
        this.completed.clear();
        this.rewardedTiers.clear();
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

    /** Unlocked and not yet completed. */
    public boolean isActive(GoalDefinitions defs, Identifier goalId) {
        return !this.completed.contains(goalId) && this.isUnlocked(defs, goalId);
    }
}
