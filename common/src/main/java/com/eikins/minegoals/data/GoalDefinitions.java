package com.eikins.minegoals.data;

import com.eikins.minegoals.Constants;
import com.eikins.minegoals.data.criterion.CategoryTierCriterion;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable, validated snapshot of every category and goal. The server builds one on each datapack reload
 * and sends it to clients, which keep their own copy.
 */
public final class GoalDefinitions {

    public static final GoalDefinitions EMPTY = new GoalDefinitions(Map.of(), Map.of());

    public static final Codec<GoalDefinitions> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Identifier.CODEC, Category.CODEC).fieldOf("categories").forGetter(GoalDefinitions::categories),
            Codec.unboundedMap(Identifier.CODEC, Goal.CODEC).fieldOf("goals").forGetter(GoalDefinitions::goals)
    ).apply(i, GoalDefinitions::new));

    private static volatile GoalDefinitions server = EMPTY;

    private final Map<Identifier, Category> categories;
    private final Map<Identifier, Goal> goals;
    private final Map<Identifier, List<Identifier>> goalsByCategory;

    private GoalDefinitions(Map<Identifier, Category> categories, Map<Identifier, Goal> goals) {
        Map<Identifier, Category> sortedCategories = new LinkedHashMap<>();
        categories.entrySet().stream()
                .sorted(Comparator.<Map.Entry<Identifier, Category>>comparingInt(e -> e.getValue().order())
                        .thenComparing(e -> e.getKey().toString()))
                .forEach(e -> sortedCategories.put(e.getKey(), e.getValue()));
        this.categories = sortedCategories;

        Map<Identifier, Goal> sortedGoals = new LinkedHashMap<>();
        goals.entrySet().stream()
                .sorted(Comparator.<Map.Entry<Identifier, Goal>>comparingInt(e -> e.getValue().tier())
                        .thenComparing(e -> e.getKey().toString()))
                .forEach(e -> sortedGoals.put(e.getKey(), e.getValue()));
        this.goals = sortedGoals;

        Map<Identifier, List<Identifier>> byCategory = new LinkedHashMap<>();
        sortedCategories.keySet().forEach(id -> byCategory.put(id, new ArrayList<>()));
        sortedGoals.forEach((id, goal) -> byCategory.computeIfAbsent(goal.category(), k -> new ArrayList<>()).add(id));
        this.goalsByCategory = byCategory;
    }

    /** Builds a snapshot, dropping (and logging) goals that reference unknown categories, tiers or goals. */
    public static GoalDefinitions validated(Map<Identifier, Category> categories, Map<Identifier, Goal> goals) {
        Map<Identifier, Goal> valid = new LinkedHashMap<>();
        goals.forEach((id, goal) -> {
            Category category = categories.get(goal.category());
            if (category == null) {
                Constants.LOG.warn("Goal {} ignored: unknown category {}", id, goal.category());
            } else if (goal.tier() > category.tiers().size()) {
                Constants.LOG.warn("Goal {} ignored: tier {} but category {} only has {} tiers",
                        id, goal.tier(), goal.category(), category.tiers().size());
            } else if (goal.criterion() instanceof CategoryTierCriterion target
                    && (!categories.containsKey(target.category())
                        || target.tier() > categories.get(target.category()).tiers().size())) {
                Constants.LOG.warn("Goal {} ignored: criterion targets tier {} of {}, which does not exist",
                        id, target.tier(), target.category());
            } else {
                valid.put(id, goal);
            }
        });
        // Drop goals whose requirements are missing, repeating until stable since removals cascade.
        boolean changed = true;
        while (changed) {
            changed = valid.entrySet().removeIf(e -> e.getValue().requires().stream().anyMatch(req -> {
                if (!valid.containsKey(req)) {
                    Constants.LOG.warn("Goal {} ignored: required goal {} does not exist", e.getKey(), req);
                    return true;
                }
                return false;
            }));
        }
        return new GoalDefinitions(categories, valid);
    }

    public static GoalDefinitions server() {
        return server;
    }

    public static void setServer(GoalDefinitions definitions) {
        server = definitions;
    }

    public Map<Identifier, Category> categories() {
        return this.categories;
    }

    public Map<Identifier, Goal> goals() {
        return this.goals;
    }

    public List<Identifier> goalsIn(Identifier category) {
        return this.goalsByCategory.getOrDefault(category, List.of());
    }
}
