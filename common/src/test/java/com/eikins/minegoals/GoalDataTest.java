package com.eikins.minegoals;

import com.eikins.minegoals.data.Category;
import com.eikins.minegoals.data.Goal;
import com.eikins.minegoals.data.GoalDefinitions;
import com.eikins.minegoals.data.RegistryMatcher;
import com.eikins.minegoals.data.Rewards;
import com.eikins.minegoals.data.TierVisibility;
import com.eikins.minegoals.data.criterion.AdvancementCriterion;
import com.eikins.minegoals.data.criterion.StatCriterion;
import com.eikins.minegoals.data.criterion.VisitBiomeCriterion;
import com.eikins.minegoals.data.criterion.VisitDimensionCriterion;
import com.eikins.minegoals.progress.PlayerGoalProgress;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoalDataTest {

    private static final int CATEGORY_COUNT = 9;
    private static final int GOAL_COUNT = 137;

    private static GoalDefinitions examples;
    /** JSON ops with vanilla registries, as the real loader uses, so item components can resolve enchantments. */
    private static DynamicOps<JsonElement> ops;

    @BeforeAll
    static void bootstrap() throws IOException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        HolderLookup.Provider registries = VanillaRegistries.createWorldLookup();
        ops = registries.createSerializationContext(JsonOps.INSTANCE);
        Path data = Path.of(System.getProperty("minegoals.resources"), "data", "minegoals", "minegoals");
        examples = GoalDefinitions.validated(
                parseFolder(data.resolve("category"), Category.CODEC),
                parseFolder(data.resolve("goal"), Goal.CODEC));
    }

    /** Parses every JSON file below {@code folder}; ids keep sub-folders, like the real loader. */
    private static <T> Map<Identifier, T> parseFolder(Path folder, Codec<T> codec) throws IOException {
        Map<Identifier, T> result = new HashMap<>();
        try (Stream<Path> files = Files.walk(folder)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                String name = folder.relativize(file).toString().replace('\\', '/').replace(".json", "");
                DataResult<T> parsed = codec.parse(ops, JsonParser.parseString(Files.readString(file)));
                result.put(Constants.id(name), parsed.getOrThrow(error -> new AssertionError(file + ": " + error)));
            }
        }
        return result;
    }

    private static JsonElement json(String raw) {
        return JsonParser.parseString(raw);
    }

    private static Identifier id(String path) {
        return Constants.id(path);
    }

    private static Identifier road(String goal) {
        return id("road_to_end/" + goal);
    }

    private static void complete(PlayerGoalProgress progress, Identifier goal) {
        progress.markCompleted(goal);
    }

    private static void completeTier(PlayerGoalProgress progress, Identifier category, int tier) {
        for (Identifier goal : examples.goalsIn(category)) {
            if (examples.goals().get(goal).tier() == tier) {
                progress.markCompleted(goal);
            }
        }
    }

    // ---- example content -------------------------------------------------------------------------

    @Test
    void exampleContentParsesAndValidates() {
        assertEquals(CATEGORY_COUNT, examples.categories().size());
        assertEquals(GOAL_COUNT, examples.goals().size(), "every example goal should survive validation");
        assertEquals(id("road_to_end"), examples.categories().keySet().iterator().next(), "categories sorted by order");
        assertTrue(examples.goals().containsKey(road("first_table")), "goal ids keep their category folder");
    }

    @Test
    void roadToTheEndIsALongGuidedTrack() {
        Category road = examples.categories().get(id("road_to_end"));
        assertTrue(road.tiers().size() >= 10);
        assertEquals(TierVisibility.NEXT, road.tierVisibility());
        // Every tier needs all goals of the tiers before it.
        int points = 0;
        for (int tier = 1; tier <= road.tiers().size(); tier++) {
            assertEquals(points, road.pointsForTier(tier), "tier " + tier);
            for (Identifier goal : examples.goalsIn(id("road_to_end"))) {
                if (examples.goals().get(goal).tier() == tier) {
                    points += examples.goals().get(goal).points();
                }
            }
        }
        assertTrue(examples.goals().containsKey(road("free_the_end")));
        assertEquals(road.tiers().size(), examples.goals().get(road("free_the_end")).tier(), "the dragon is the last tier");
    }

    /** Catches typos in ids: everything a goal points at must exist in this Minecraft version. */
    @Test
    void everyReferenceExistsInVanilla() {
        List<String> missing = new ArrayList<>();
        examples.goals().forEach((goalId, goal) -> {
            switch (goal.criterion()) {
                case StatCriterion<?> stat -> {
                    RegistryMatcher<?> target = stat.target();
                    if (target.isTag()) {
                        String folder = Registries.tagsDirPath(target.registry());
                        check(missing, goalId, "data/" + target.id().getNamespace() + "/" + folder + "/" + target.id().getPath() + ".json");
                    } else if (!stat.kind().registry().containsKey(target.id())) {
                        missing.add(goalId + " -> " + target);
                    }
                }
                case VisitBiomeCriterion biome -> {
                    Identifier b = biome.biome().id();
                    check(missing, goalId, biome.biome().isTag()
                            ? "data/" + b.getNamespace() + "/tags/worldgen/biome/" + b.getPath() + ".json"
                            : "data/" + b.getNamespace() + "/worldgen/biome/" + b.getPath() + ".json");
                }
                case AdvancementCriterion advancement -> {
                    Identifier a = advancement.advancement();
                    check(missing, goalId, "data/" + a.getNamespace() + "/advancement/" + a.getPath() + ".json");
                }
                case VisitDimensionCriterion dimension -> {
                    String path = dimension.dimension().getPath();
                    if (!List.of("overworld", "the_nether", "the_end").contains(path)) {
                        missing.add(goalId + " -> " + dimension.dimension());
                    }
                }
                default -> {
                }
            }
        });
        assertTrue(missing.isEmpty(), "unknown references: " + missing);
    }

    private static void check(List<String> missing, Identifier goal, String resource) {
        if (GoalDataTest.class.getClassLoader().getResource(resource) == null) {
            missing.add(goal + " -> " + resource);
        }
    }

    @Test
    void everyGoalAndCategoryIsTranslated() throws IOException {
        Path lang = Path.of(System.getProperty("minegoals.resources"), "assets", "minegoals", "lang");
        for (String file : List.of("en_us.json", "fr_fr.json")) {
            var keys = JsonParser.parseString(Files.readString(lang.resolve(file))).getAsJsonObject().keySet();
            for (Identifier goal : examples.goals().keySet()) {
                String key = "goal.minegoals." + goal.getPath().replace('/', '.');
                assertTrue(keys.contains(key) && keys.contains(key + ".desc"), file + " misses " + key);
            }
            for (Identifier category : examples.categories().keySet()) {
                assertTrue(keys.contains("category.minegoals." + category.getPath()), file + " misses " + category);
            }
        }
    }

    // ---- parsing ---------------------------------------------------------------------------------

    @Test
    void unknownCriterionTypeIsRejected() {
        var result = Goal.CODEC.parse(JsonOps.INSTANCE, json("""
                {"category": "minegoals:oceans", "title": "x", "icon": "minecraft:stone",
                 "criterion": {"type": "minegoals:fly_to_the_moon"}}"""));
        assertTrue(result.isError());
    }

    @Test
    void goalDefaultsApply() {
        Goal goal = Goal.CODEC.parse(JsonOps.INSTANCE, json("""
                {"category": "minegoals:oceans", "title": "x", "icon": "minecraft:stone",
                 "criterion": {"type": "minegoals:break_block", "block": "#minecraft:logs"}}""")).getOrThrow();
        assertEquals(1, goal.tier());
        assertEquals(10, goal.points());
        assertEquals(1, goal.count());
        assertTrue(goal.requires().isEmpty());
        assertTrue(goal.rewards().isEmpty());
    }

    @Test
    void newCriteriaParse() {
        Goal fish = Goal.CODEC.parse(JsonOps.INSTANCE, json("""
                {"category": "minegoals:oceans", "title": "x", "icon": "minecraft:cod",
                 "criterion": {"type": "minegoals:custom_stat", "stat": "minecraft:fish_caught", "count": 5}}""")).getOrThrow();
        assertEquals(5, fish.count());
        Goal adv = Goal.CODEC.parse(JsonOps.INSTANCE, json("""
                {"category": "minegoals:oceans", "title": "x", "icon": "minecraft:cod",
                 "criterion": {"type": "minegoals:advancement", "advancement": "minecraft:story/mine_diamond"}}""")).getOrThrow();
        assertEquals(1, adv.count());
    }

    @Test
    void rewardItemsAcceptGiveStyleComponents() {
        Rewards rewards = Rewards.CODEC.parse(ops, json("""
                {"items": [
                  "minecraft:diamond",
                  {"id": "minecraft:diamond_sword", "count": 1, "components": {
                    "minecraft:enchantments": {"minecraft:sharpness": 5},
                    "minecraft:custom_name": "Excalibur"}}
                ]}""")).getOrThrow();
        assertEquals(2, rewards.items().size());
        assertEquals(1, rewards.items().getFirst().count(), "a plain id is a single item");

        // Real ItemStacks need a loaded world (item components are bound then), so check the parsed data instead.
        var sword = rewards.items().get(1).components().split().added();
        assertTrue(sword.has(DataComponents.ENCHANTMENTS), "enchantments parsed");
        assertEquals("Excalibur", sword.get(DataComponents.CUSTOM_NAME).getString());

        assertTrue(Rewards.CODEC.parse(ops, json("""
                {"items": [{"id": "minecraft:diamond_sword", "components": {"minecraft:enchantments": {"minecraft:nope": 1}}}]}"""))
                .isError(), "unknown enchantments are rejected");
    }

    @Test
    void exampleRewardsUseComponents() {
        Category road = examples.categories().get(id("road_to_end"));
        var bow = road.tiers().get(8).rewards().items().getFirst();
        assertEquals(Items.BOW, bow.item().value());
        assertTrue(bow.components().split().added().has(DataComponents.ENCHANTMENTS), "tier 9 gives an enchanted bow");
        var wings = road.tiers().get(11).rewards().items().getFirst();
        assertEquals(Items.ELYTRA, wings.item().value());
        assertTrue(wings.components().split().added().has(DataComponents.CUSTOM_NAME), "tier 12 gives named elytra");
    }

    @Test
    void categoryTiersMustStartAtZeroAndIncrease() {
        assertTrue(Category.CODEC.parse(JsonOps.INSTANCE, json("""
                {"title": "x", "icon": "minecraft:stone", "tiers": [{"points": 5}]}""")).isError());
        assertTrue(Category.CODEC.parse(JsonOps.INSTANCE, json("""
                {"title": "x", "icon": "minecraft:stone", "tiers": [{"points": 0}, {"points": 20}, {"points": 20}]}""")).isError());
    }

    private static Goal goalJson(String category, String extra) {
        return Goal.CODEC.parse(JsonOps.INSTANCE, json("""
                {"category": "%s", "title": "x", "icon": "minecraft:stone", %s
                 "criterion": {"type": "minegoals:visit_dimension", "dimension": "minecraft:the_end"}}"""
                .formatted(category, extra))).getOrThrow();
    }

    @Test
    void goalsWithBrokenReferencesAreDropped() {
        Map<Identifier, Goal> goals = new HashMap<>(examples.goals());
        goals.put(id("orphan"), goalJson("minegoals:nope", ""));
        goals.put(id("needs_orphan"), goalJson("minegoals:oceans", "\"requires\": [\"minegoals:orphan\"],"));

        GoalDefinitions defs = GoalDefinitions.validated(examples.categories(), goals);
        assertFalse(defs.goals().containsKey(id("orphan")));
        assertFalse(defs.goals().containsKey(id("needs_orphan")), "removal must cascade to dependants");
        assertEquals(GOAL_COUNT, defs.goals().size());
    }

    @Test
    void definitionsRoundTripThroughTheirCodec() {
        JsonElement encoded = GoalDefinitions.CODEC.encodeStart(ops, examples).getOrThrow();
        GoalDefinitions decoded = GoalDefinitions.CODEC.parse(ops, encoded).getOrThrow();
        assertEquals(examples.goals().keySet(), decoded.goals().keySet());
        assertEquals(examples.categories().keySet(), decoded.categories().keySet());
    }

    // ---- progression -----------------------------------------------------------------------------

    @Test
    void pointsTiersAndLevelAreDerivedFromCompletedGoals() {
        PlayerGoalProgress progress = new PlayerGoalProgress();
        Identifier roadId = id("road_to_end");
        assertEquals(0, progress.categoryPoints(examples, roadId));
        assertEquals(1, progress.tier(examples, roadId));
        assertEquals(1, progress.level(examples));

        complete(progress, road("lumberjack"));
        complete(progress, road("first_table"));
        assertEquals(20, progress.categoryPoints(examples, roadId));
        assertFalse(progress.isTierComplete(examples, roadId, 1), "all 3 tier-1 goals needed");

        complete(progress, road("wooden_pickaxe"));
        assertEquals(30, progress.categoryPoints(examples, roadId));
        assertTrue(progress.isTierComplete(examples, roadId, 1));
        assertEquals(1, progress.tier(examples, roadId), "tier 2 waits for the tier 1 reward to be claimed");

        progress.markTierClaimed(roadId, 1);
        assertEquals(2, progress.tier(examples, roadId));

        completeTier(progress, roadId, 2);
        progress.markTierClaimed(roadId, 2);
        assertEquals(3, progress.tier(examples, roadId));
        assertEquals(90, progress.totalPoints(examples));

        completeTier(progress, id("oceans"), 1);
        assertEquals(120, progress.totalPoints(examples));
        assertEquals(2, progress.level(examples), "one level every 100 points");
    }

    @Test
    void goalsUnlockByTier() {
        PlayerGoalProgress progress = new PlayerGoalProgress();
        assertTrue(progress.isActive(examples, road("lumberjack")));
        assertFalse(progress.isUnlocked(examples, road("furnace")));

        completeTier(progress, id("road_to_end"), 1);
        assertFalse(progress.isUnlocked(examples, road("furnace")), "tier 1 complete but its reward is not claimed");
        progress.markTierClaimed(id("road_to_end"), 1);
        assertTrue(progress.isUnlocked(examples, road("furnace")));
        assertFalse(progress.isActive(examples, road("lumberjack")), "completed goals are no longer active");
    }

    @Test
    void goalsUnlockByRequirementsToo() {
        Map<Identifier, Goal> goals = new HashMap<>(examples.goals());
        goals.put(id("needs_angler"), goalJson("minegoals:oceans", "\"requires\": [\"minegoals:oceans/angler\"],"));
        GoalDefinitions defs = GoalDefinitions.validated(examples.categories(), goals);

        PlayerGoalProgress progress = new PlayerGoalProgress();
        assertFalse(progress.isUnlocked(defs, id("needs_angler")), "tier 1 but requires angler");
        progress.markCompleted(id("oceans/angler"));
        assertTrue(progress.isUnlocked(defs, id("needs_angler")));
    }

    @Test
    void themedGoalsTrackBeforeTheirTierUnlocks() {
        PlayerGoalProgress progress = new PlayerGoalProgress();
        Identifier themed = id("oceans/tactical_fishing");
        assertEquals(2, examples.goals().get(themed).tier());
        assertFalse(progress.isUnlocked(examples, themed));
        assertTrue(progress.isTracking(examples, themed), "themed goals count while their tier is locked");
        assertFalse(progress.isActive(examples, themed), "but cannot complete before the tier unlocks");

        assertFalse(progress.isTracking(examples, road("furnace")), "Road to the End stays step by step");
        assertTrue(progress.isTracking(examples, road("lumberjack")), "unlocked goals always track");

        progress.markCompleted(themed);
        assertFalse(progress.isTracking(examples, themed), "completed goals stop tracking");
    }

    @Test
    void requirementsBlockEarlyTracking() {
        Map<Identifier, Goal> goals = new HashMap<>(examples.goals());
        goals.put(id("early_but_needs_angler"), goalJson("minegoals:oceans",
                "\"tier\": 3, \"track_before_unlock\": true, \"requires\": [\"minegoals:oceans/angler\"],"));
        GoalDefinitions defs = GoalDefinitions.validated(examples.categories(), goals);

        PlayerGoalProgress progress = new PlayerGoalProgress();
        assertFalse(progress.isTracking(defs, id("early_but_needs_angler")), "requires is never bypassed");
        progress.markCompleted(id("oceans/angler"));
        assertTrue(progress.isTracking(defs, id("early_but_needs_angler")), "tier lock is bypassed");
        assertFalse(progress.isActive(defs, id("early_but_needs_angler")));
    }

    @Test
    void trackBeforeUnlockDefaultsToFalse() {
        assertFalse(goalJson("minegoals:oceans", "").trackBeforeUnlock());
    }

    @Test
    void countsAreClampedAndChangeDetected() {
        PlayerGoalProgress progress = new PlayerGoalProgress();
        assertTrue(progress.setCount(road("lumberjack"), 50, 16));
        assertEquals(16, progress.count(road("lumberjack")));
        assertFalse(progress.setCount(road("lumberjack"), 20, 16), "already at max, no change");
        assertFalse(progress.setCount(road("torches"), 0, 16), "0 on a fresh goal is not a change");
    }

    @Test
    void progressRoundTripsThroughItsCodec() {
        PlayerGoalProgress progress = new PlayerGoalProgress();
        progress.setCount(road("lumberjack"), 7, 16);
        complete(progress, road("first_table"));
        progress.setAnnouncedTier(id("road_to_end"), 2);
        progress.markGoalClaimed(road("first_table"));
        progress.markTierClaimed(id("road_to_end"), 2);

        JsonElement encoded = PlayerGoalProgress.CODEC.encodeStart(JsonOps.INSTANCE, progress).getOrThrow();
        PlayerGoalProgress decoded = PlayerGoalProgress.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(7, decoded.count(road("lumberjack")));
        assertTrue(decoded.isCompleted(road("first_table")));
        assertEquals(2, decoded.announcedTier(id("road_to_end")));
        assertTrue(decoded.isGoalClaimed(road("first_table")));
        assertTrue(decoded.isTierClaimed(id("road_to_end"), 2));
        assertFalse(decoded.isTierClaimed(id("road_to_end"), 3));
    }

    @Test
    void resetCategoryClearsGoalsAndClaims() {
        PlayerGoalProgress progress = new PlayerGoalProgress();
        complete(progress, road("lumberjack"));
        complete(progress, id("oceans/angler"));
        progress.setAnnouncedTier(id("road_to_end"), 2);
        progress.markTierClaimed(id("road_to_end"), 2);
        progress.markGoalClaimed(road("lumberjack"));

        progress.resetCategory(examples, id("road_to_end"));
        assertFalse(progress.isCompleted(road("lumberjack")));
        assertTrue(progress.isCompleted(id("oceans/angler")), "other categories untouched");
        assertEquals(0, progress.announcedTier(id("road_to_end")));
        assertFalse(progress.isTierClaimed(id("road_to_end"), 2));
        assertFalse(progress.isGoalClaimed(road("lumberjack")));
    }

    // ---- claims ----------------------------------------------------------------------------------

    @Test
    void goalRewardsAreClaimableOnceCompleted() {
        PlayerGoalProgress progress = new PlayerGoalProgress();
        assertFalse(progress.canClaimGoal(examples, road("lumberjack")), "not completed yet");

        complete(progress, road("lumberjack"));
        assertTrue(progress.canClaimGoal(examples, road("lumberjack")));
        assertEquals(1, progress.claimableCount(examples));

        progress.markGoalClaimed(road("lumberjack"));
        assertFalse(progress.canClaimGoal(examples, road("lumberjack")), "claimed only once");
        assertEquals(0, progress.claimableCount(examples));
    }

    @Test
    void tierRewardIsEarnedByCompletingTheTierAndClaimingUnlocksTheNext() {
        PlayerGoalProgress progress = new PlayerGoalProgress();
        Identifier roadId = id("road_to_end");
        assertFalse(progress.canClaimTier(examples, roadId, 1), "tier 1 not complete yet");

        completeTier(progress, roadId, 1);
        for (Identifier goal : examples.goalsIn(roadId)) {
            if (progress.isCompleted(goal)) {
                progress.markGoalClaimed(goal);
            }
        }
        assertTrue(progress.canClaimTier(examples, roadId, 1));
        assertFalse(progress.canClaimTier(examples, roadId, 2), "tier 2 locked and not complete");
        assertFalse(progress.canClaimTier(examples, roadId, 99), "out of range");
        assertEquals(1, progress.claimableCount(examples));

        progress.markTierClaimed(roadId, 1);
        assertFalse(progress.canClaimTier(examples, roadId, 1), "claimed only once");
        assertEquals(2, progress.tier(examples, roadId));
    }

    @Test
    void earlyTrackedPointsStillUnlockOneClaimAtATime() {
        PlayerGoalProgress progress = new PlayerGoalProgress();
        Identifier oceans = id("oceans");
        // Complete every Oceans goal (possible with track_before_unlock): all tiers are complete...
        completeTier(progress, oceans, 1);
        completeTier(progress, oceans, 2);
        completeTier(progress, oceans, 3);
        completeTier(progress, oceans, 4);
        assertEquals(1, progress.tier(examples, oceans), "...but tiers still open one claim at a time");
        assertTrue(progress.canClaimTier(examples, oceans, 1));
        assertFalse(progress.canClaimTier(examples, oceans, 2), "tier 2 is still locked");

        progress.markTierClaimed(oceans, 1);
        progress.markTierClaimed(oceans, 2);
        progress.markTierClaimed(oceans, 3);
        assertEquals(4, progress.tier(examples, oceans));
        assertTrue(progress.isTierComplete(examples, oceans, 4), "last tier: every goal done");
        assertTrue(progress.canClaimTier(examples, oceans, 4));
    }

    @Test
    void tierVisibilityControlsWhatIsShown() {
        assertEquals(3, TierVisibility.VISIBLE.shownTiers(1, 3));
        assertEquals(2, TierVisibility.NEXT.shownTiers(1, 3));
        assertEquals(3, TierVisibility.NEXT.shownTiers(3, 3), "never more than the real tiers");
        assertEquals(1, TierVisibility.HIDDEN.shownTiers(1, 3));

        assertTrue(TierVisibility.VISIBLE.revealsContent(3, 1));
        assertFalse(TierVisibility.NEXT.revealsContent(2, 1), "next tier shows as ??? only");
        assertTrue(TierVisibility.NEXT.revealsContent(1, 1));
        assertFalse(TierVisibility.HIDDEN.revealsNextThreshold());
        assertTrue(TierVisibility.NEXT.revealsNextThreshold());
    }

    @Test
    void tierVisibilityIsParsedAndDefaultsToVisible() {
        Category fallback = Category.CODEC.parse(JsonOps.INSTANCE, json("""
                {"title": "x", "icon": "minecraft:stone", "tiers": [{"points": 0}]}""")).getOrThrow();
        assertEquals(TierVisibility.VISIBLE, fallback.tierVisibility());
        assertTrue(Category.CODEC.parse(JsonOps.INSTANCE, json("""
                {"title": "x", "icon": "minecraft:stone", "tier_visibility": "sometimes", "tiers": [{"points": 0}]}""")).isError());
    }
}
