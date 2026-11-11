package com.eikins.minegoals;

import com.eikins.minegoals.data.Category;
import com.eikins.minegoals.data.Goal;
import com.eikins.minegoals.data.GoalDefinitions;
import com.eikins.minegoals.data.TierVisibility;
import com.eikins.minegoals.progress.PlayerGoalProgress;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoalDataTest {

    private static GoalDefinitions examples;

    @BeforeAll
    static void bootstrap() throws IOException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Path data = Path.of(System.getProperty("minegoals.resources"), "data", "minegoals", "minegoals");
        examples = GoalDefinitions.validated(
                parseFolder(data.resolve("category"), Category.CODEC),
                parseFolder(data.resolve("goal"), Goal.CODEC));
    }

    private static <T> Map<Identifier, T> parseFolder(Path folder, Codec<T> codec) throws IOException {
        Map<Identifier, T> result = new HashMap<>();
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString().replace(".json", "");
                DataResult<T> parsed = codec.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(file)));
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

    // ---- parsing ---------------------------------------------------------------------------------

    @Test
    void exampleContentParsesAndValidates() {
        assertEquals(4, examples.categories().size());
        assertEquals(32, examples.goals().size(), "every example goal should survive validation");
        assertEquals(id("mining"), examples.categories().keySet().iterator().next(), "categories sorted by order");
    }

    @Test
    void unknownCriterionTypeIsRejected() {
        var result = Goal.CODEC.parse(JsonOps.INSTANCE, json("""
                {"category": "minegoals:mining", "title": "x", "icon": "minecraft:stone",
                 "criterion": {"type": "minegoals:fly_to_the_moon"}}"""));
        assertTrue(result.isError());
    }

    @Test
    void goalDefaultsApply() {
        Goal goal = Goal.CODEC.parse(JsonOps.INSTANCE, json("""
                {"category": "minegoals:mining", "title": "x", "icon": "minecraft:stone",
                 "criterion": {"type": "minegoals:break_block", "block": "#minecraft:logs"}}""")).getOrThrow();
        assertEquals(1, goal.tier());
        assertEquals(10, goal.points());
        assertEquals(1, goal.count());
        assertTrue(goal.requires().isEmpty());
        assertTrue(goal.rewards().isEmpty());
    }

    @Test
    void categoryTiersMustStartAtZeroAndIncrease() {
        assertTrue(Category.CODEC.parse(JsonOps.INSTANCE, json("""
                {"title": "x", "icon": "minecraft:stone", "tiers": [{"points": 5}]}""")).isError());
        assertTrue(Category.CODEC.parse(JsonOps.INSTANCE, json("""
                {"title": "x", "icon": "minecraft:stone", "tiers": [{"points": 0}, {"points": 20}, {"points": 20}]}""")).isError());
    }

    @Test
    void goalsWithBrokenReferencesAreDropped() {
        Goal orphan = Goal.CODEC.parse(JsonOps.INSTANCE, json("""
                {"category": "minegoals:nope", "title": "x", "icon": "minecraft:stone",
                 "criterion": {"type": "minegoals:visit_dimension", "dimension": "minecraft:the_end"}}""")).getOrThrow();
        Goal needsOrphan = Goal.CODEC.parse(JsonOps.INSTANCE, json("""
                {"category": "minegoals:mining", "title": "x", "icon": "minecraft:stone", "requires": ["minegoals:orphan"],
                 "criterion": {"type": "minegoals:visit_dimension", "dimension": "minecraft:the_end"}}""")).getOrThrow();
        Map<Identifier, Goal> goals = new HashMap<>(examples.goals());
        goals.put(id("orphan"), orphan);
        goals.put(id("needs_orphan"), needsOrphan);

        GoalDefinitions defs = GoalDefinitions.validated(examples.categories(), goals);
        assertFalse(defs.goals().containsKey(id("orphan")));
        assertFalse(defs.goals().containsKey(id("needs_orphan")), "removal must cascade to dependants");
        assertEquals(32, defs.goals().size());
    }

    @Test
    void definitionsRoundTripThroughTheirCodec() {
        JsonElement encoded = GoalDefinitions.CODEC.encodeStart(JsonOps.INSTANCE, examples).getOrThrow();
        GoalDefinitions decoded = GoalDefinitions.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(examples.goals().keySet(), decoded.goals().keySet());
        assertEquals(examples.categories().keySet(), decoded.categories().keySet());
    }

    // ---- progression -----------------------------------------------------------------------------

    private static void complete(PlayerGoalProgress progress, String goal) {
        progress.markCompleted(id(goal));
    }

    @Test
    void pointsTiersAndLevelAreDerivedFromCompletedGoals() {
        PlayerGoalProgress progress = new PlayerGoalProgress();
        Identifier mining = id("mining");
        assertEquals(0, progress.categoryPoints(examples, mining));
        assertEquals(1, progress.tier(examples, mining));
        assertEquals(1, progress.level(examples));

        complete(progress, "lumberjack");
        complete(progress, "stone_breaker");
        assertEquals(20, progress.categoryPoints(examples, mining));
        assertEquals(1, progress.tier(examples, mining), "30 points needed for tier 2");

        complete(progress, "coal_miner");
        assertEquals(30, progress.categoryPoints(examples, mining));
        assertEquals(2, progress.tier(examples, mining));

        complete(progress, "iron_miner");
        complete(progress, "deep_digger");
        complete(progress, "gold_rush");
        assertEquals(75, progress.categoryPoints(examples, mining));
        assertEquals(3, progress.tier(examples, mining));

        complete(progress, "zombie_slayer");
        complete(progress, "bone_collector");
        complete(progress, "spider_squasher");
        assertEquals(105, progress.totalPoints(examples));
        assertEquals(2, progress.level(examples), "one level every 100 points");
    }

    @Test
    void goalsUnlockByTier() {
        PlayerGoalProgress progress = new PlayerGoalProgress();
        assertTrue(progress.isActive(examples, id("lumberjack")));
        assertFalse(progress.isUnlocked(examples, id("iron_miner")));

        complete(progress, "lumberjack");
        complete(progress, "stone_breaker");
        complete(progress, "coal_miner");
        assertTrue(progress.isUnlocked(examples, id("iron_miner")));
        assertFalse(progress.isActive(examples, id("lumberjack")), "completed goals are no longer active");
    }

    @Test
    void goalsUnlockByRequirementsToo() {
        PlayerGoalProgress progress = new PlayerGoalProgress();
        // Reach combat tier 3 without visiting the Nether.
        for (String goal : new String[]{"zombie_slayer", "bone_collector", "spider_squasher",
                "creeper_defuser", "ender_hunter", "witch_hunter"}) {
            complete(progress, goal);
        }
        assertEquals(3, progress.tier(examples, id("combat")));
        assertFalse(progress.isUnlocked(examples, id("blaze_runner")), "requires nether_traveler");

        complete(progress, "nether_traveler");
        assertTrue(progress.isUnlocked(examples, id("blaze_runner")));
    }

    @Test
    void countsAreClampedAndChangeDetected() {
        PlayerGoalProgress progress = new PlayerGoalProgress();
        assertTrue(progress.setCount(id("lumberjack"), 50, 16));
        assertEquals(16, progress.count(id("lumberjack")));
        assertFalse(progress.setCount(id("lumberjack"), 20, 16), "already at max, no change");
        assertFalse(progress.setCount(id("stone_breaker"), 0, 64), "0 on a fresh goal is not a change");
    }

    @Test
    void progressRoundTripsThroughItsCodec() {
        PlayerGoalProgress progress = new PlayerGoalProgress();
        progress.setCount(id("lumberjack"), 7, 16);
        complete(progress, "coal_miner");
        progress.setAnnouncedTier(id("mining"), 2);
        progress.markGoalClaimed(id("coal_miner"));
        progress.markTierClaimed(id("mining"), 1);

        JsonElement encoded = PlayerGoalProgress.CODEC.encodeStart(JsonOps.INSTANCE, progress).getOrThrow();
        PlayerGoalProgress decoded = PlayerGoalProgress.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(7, decoded.count(id("lumberjack")));
        assertTrue(decoded.isCompleted(id("coal_miner")));
        assertEquals(2, decoded.announcedTier(id("mining")));
        assertTrue(decoded.isGoalClaimed(id("coal_miner")));
        assertTrue(decoded.isTierClaimed(id("mining"), 1));
        assertFalse(decoded.isTierClaimed(id("mining"), 2));
    }

    @Test
    void resetCategoryClearsGoalsAndRewardedTier() {
        PlayerGoalProgress progress = new PlayerGoalProgress();
        complete(progress, "lumberjack");
        complete(progress, "zombie_slayer");
        progress.setAnnouncedTier(id("mining"), 2);
        progress.markTierClaimed(id("mining"), 2);
        progress.markGoalClaimed(id("lumberjack"));

        progress.resetCategory(examples, id("mining"));
        assertFalse(progress.isCompleted(id("lumberjack")));
        assertTrue(progress.isCompleted(id("zombie_slayer")), "other categories untouched");
        assertEquals(0, progress.announcedTier(id("mining")));
        assertFalse(progress.isTierClaimed(id("mining"), 2));
        assertFalse(progress.isGoalClaimed(id("lumberjack")));
    }

    // ---- claims ----------------------------------------------------------------------------------

    @Test
    void goalRewardsAreClaimableOnceCompleted() {
        PlayerGoalProgress progress = new PlayerGoalProgress();
        assertFalse(progress.canClaimGoal(examples, id("lumberjack")), "not completed yet");

        complete(progress, "lumberjack");
        assertTrue(progress.canClaimGoal(examples, id("lumberjack")));
        assertEquals(1, progress.claimableCount(examples));

        progress.markGoalClaimed(id("lumberjack"));
        assertFalse(progress.canClaimGoal(examples, id("lumberjack")), "claimed only once");
        assertEquals(0, progress.claimableCount(examples));
    }

    @Test
    void tierRewardsAreClaimableOnceReached() {
        PlayerGoalProgress progress = new PlayerGoalProgress();
        Identifier mining = id("mining");
        assertFalse(progress.canClaimTier(examples, mining, 1), "tier 1 has no reward");
        assertFalse(progress.canClaimTier(examples, mining, 2), "tier 2 not reached");

        for (String goal : new String[]{"lumberjack", "stone_breaker", "coal_miner"}) {
            complete(progress, goal);
            progress.markGoalClaimed(id(goal));
        }
        assertTrue(progress.canClaimTier(examples, mining, 2));
        assertFalse(progress.canClaimTier(examples, mining, 3));
        assertFalse(progress.canClaimTier(examples, mining, 99), "out of range");
        assertEquals(1, progress.claimableCount(examples));

        progress.markTierClaimed(mining, 2);
        assertFalse(progress.canClaimTier(examples, mining, 2));
    }

    // ---- tier visibility -------------------------------------------------------------------------

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
        assertEquals(TierVisibility.NEXT, examples.categories().get(id("exploration")).tierVisibility());
        assertTrue(Category.CODEC.parse(JsonOps.INSTANCE, json("""
                {"title": "x", "icon": "minecraft:stone", "tier_visibility": "sometimes", "tiers": [{"points": 0}]}""")).isError());
    }
}
