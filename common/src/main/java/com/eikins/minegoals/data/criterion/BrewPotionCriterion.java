package com.eikins.minegoals.data.criterion;

import com.eikins.minegoals.Constants;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.List;

/**
 * Take brewed potions out of a brewing stand (one per bottle), e.g. any of {@code minecraft:swiftness},
 * {@code minecraft:long_swiftness} and {@code minecraft:strong_swiftness}. An empty {@code potions} list accepts any
 * potion.
 */
public record BrewPotionCriterion(List<Identifier> potions, int count) implements GoalCriterion {

    public static final MapCodec<BrewPotionCriterion> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.listOf().optionalFieldOf("potions", List.of()).forGetter(BrewPotionCriterion::potions),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(BrewPotionCriterion::count)
    ).apply(i, BrewPotionCriterion::new));

    @Override
    public Identifier type() {
        return Constants.id("brew_potion");
    }

    @Override
    public int onBrew(PotionContents contents) {
        return contents.potion()
                .filter(potion -> this.potions.isEmpty() || this.potions.stream().anyMatch(potion::is))
                .isPresent() ? 1 : 0;
    }
}
