package com.eikins.minegoals.data.criterion;

import com.eikins.minegoals.Constants;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;

import java.util.Map;
import java.util.OptionalInt;

/** What a player has to do to complete a goal. */
public sealed interface GoalCriterion
        permits StatCriterion, HoldItemCriterion, VisitBiomeCriterion, VisitDimensionCriterion, AdvancementCriterion {

    Map<Identifier, MapCodec<? extends GoalCriterion>> TYPES = Map.of(
            Constants.id("break_block"), StatCriterion.BREAK_BLOCK,
            Constants.id("kill_entity"), StatCriterion.KILL_ENTITY,
            Constants.id("collect_item"), StatCriterion.COLLECT_ITEM,
            Constants.id("craft_item"), StatCriterion.CRAFT_ITEM,
            Constants.id("custom_stat"), StatCriterion.CUSTOM_STAT,
            Constants.id("advancement"), AdvancementCriterion.CODEC,
            Constants.id("hold_item"), HoldItemCriterion.CODEC,
            Constants.id("visit_biome"), VisitBiomeCriterion.CODEC,
            Constants.id("visit_dimension"), VisitDimensionCriterion.CODEC
    );

    Codec<GoalCriterion> CODEC = Identifier.CODEC
            .comapFlatMap(
                    id -> TYPES.containsKey(id)
                            ? com.mojang.serialization.DataResult.success(id)
                            : com.mojang.serialization.DataResult.error(() -> "Unknown criterion type: " + id),
                    id -> id)
            .dispatch("type", GoalCriterion::type, TYPES::get);

    Identifier type();

    /** Amount needed to complete the goal. */
    int count();

    /** Progress gained from a vanilla stat increment, or 0 if this criterion does not care. */
    default int onStat(Stat<?> stat, int delta) {
        return 0;
    }

    /** Absolute progress measured by polling the player every second, or empty for stat-based criteria. */
    default OptionalInt poll(ServerPlayer player) {
        return OptionalInt.empty();
    }
}
