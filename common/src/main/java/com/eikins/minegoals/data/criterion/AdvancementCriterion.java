package com.eikins.minegoals.data.criterion;

import com.eikins.minegoals.Constants;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.OptionalInt;

/**
 * Earn a vanilla (or datapack) advancement, e.g. {@code minecraft:story/follow_ender_eye}. Advancements earned
 * before the goal was unlocked count too, since they are a permanent achievement.
 */
public record AdvancementCriterion(Identifier advancement) implements GoalCriterion {

    public static final MapCodec<AdvancementCriterion> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("advancement").forGetter(AdvancementCriterion::advancement)
    ).apply(i, AdvancementCriterion::new));

    @Override
    public Identifier type() {
        return Constants.id("advancement");
    }

    @Override
    public int count() {
        return 1;
    }

    @Override
    public OptionalInt poll(ServerPlayer player) {
        AdvancementHolder holder = player.level().getServer().getAdvancements().get(this.advancement);
        if (holder == null) {
            return OptionalInt.empty();
        }
        return player.getAdvancements().getOrStartProgress(holder).isDone() ? OptionalInt.of(1) : OptionalInt.empty();
    }
}
