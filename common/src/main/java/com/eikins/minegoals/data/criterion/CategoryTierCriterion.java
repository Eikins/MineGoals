package com.eikins.minegoals.data.criterion;

import com.eikins.minegoals.Constants;
import com.eikins.minegoals.data.GoalDefinitions;
import com.eikins.minegoals.progress.PlayerGoalProgress;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;

import java.util.OptionalInt;

/**
 * Complete a tier of a category, usually another one, e.g. tier 2 of {@code minegoals:mining}. A tier is complete
 * once its claimed goals reach the points it needs; claiming the tier's own reward is not required.
 */
public record CategoryTierCriterion(Identifier category, int tier) implements GoalCriterion {

    public static final MapCodec<CategoryTierCriterion> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("category").forGetter(CategoryTierCriterion::category),
            ExtraCodecs.POSITIVE_INT.fieldOf("tier").forGetter(CategoryTierCriterion::tier)
    ).apply(i, CategoryTierCriterion::new));

    @Override
    public Identifier type() {
        return Constants.id("category_tier");
    }

    @Override
    public int count() {
        return 1;
    }

    @Override
    public OptionalInt fromProgress(PlayerGoalProgress progress, GoalDefinitions defs) {
        return progress.isTierComplete(defs, this.category, this.tier) ? OptionalInt.of(1) : OptionalInt.empty();
    }
}
