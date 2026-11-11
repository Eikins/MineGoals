package com.eikins.minegoals.data;

import com.eikins.minegoals.data.criterion.GoalCriterion;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Optional;

/** A personal goal, loaded from {@code data/<ns>/minegoals/goal/<name>.json}. */
public record Goal(
        Identifier category,
        int tier,
        int points,
        Component title,
        Optional<Component> description,
        Item icon,
        List<Identifier> requires,
        GoalCriterion criterion,
        Rewards rewards
) {

    public static final Codec<Goal> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("category").forGetter(Goal::category),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("tier", 1).forGetter(Goal::tier),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("points", 10).forGetter(Goal::points),
            ComponentSerialization.CODEC.fieldOf("title").forGetter(Goal::title),
            ComponentSerialization.CODEC.optionalFieldOf("description").forGetter(Goal::description),
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("icon").forGetter(Goal::icon),
            Identifier.CODEC.listOf().optionalFieldOf("requires", List.of()).forGetter(Goal::requires),
            GoalCriterion.CODEC.fieldOf("criterion").forGetter(Goal::criterion),
            Rewards.CODEC.optionalFieldOf("rewards", Rewards.NONE).forGetter(Goal::rewards)
    ).apply(i, Goal::new));

    public int count() {
        return this.criterion.count();
    }
}
