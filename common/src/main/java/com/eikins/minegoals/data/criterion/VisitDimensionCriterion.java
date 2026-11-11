package com.eikins.minegoals.data.criterion;

import com.eikins.minegoals.Constants;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.OptionalInt;

/** Enter a dimension, e.g. {@code minecraft:the_nether}. */
public record VisitDimensionCriterion(Identifier dimension) implements GoalCriterion {

    public static final MapCodec<VisitDimensionCriterion> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("dimension").forGetter(VisitDimensionCriterion::dimension)
    ).apply(i, VisitDimensionCriterion::new));

    @Override
    public Identifier type() {
        return Constants.id("visit_dimension");
    }

    @Override
    public int count() {
        return 1;
    }

    @Override
    public OptionalInt poll(ServerPlayer player) {
        boolean inside = player.level().dimension().identifier().equals(this.dimension);
        return inside ? OptionalInt.of(1) : OptionalInt.empty();
    }
}
