package com.eikins.minegoals.data.criterion;

import com.eikins.minegoals.Constants;
import com.eikins.minegoals.data.RegistryMatcher;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;

import java.util.OptionalInt;

/** Stand in a matching biome. */
public record VisitBiomeCriterion(RegistryMatcher<Biome> biome) implements GoalCriterion {

    public static final MapCodec<VisitBiomeCriterion> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryMatcher.codec(Registries.BIOME).fieldOf("biome").forGetter(VisitBiomeCriterion::biome)
    ).apply(i, VisitBiomeCriterion::new));

    @Override
    public Identifier type() {
        return Constants.id("visit_biome");
    }

    @Override
    public int count() {
        return 1;
    }

    @Override
    public OptionalInt poll(ServerPlayer player) {
        boolean inside = this.biome.matches(player.level().getBiome(player.blockPosition()));
        return inside ? OptionalInt.of(1) : OptionalInt.empty();
    }
}
