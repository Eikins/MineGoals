package com.eikins.minegoals.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;

import java.util.List;

/** A goal category: a track of tiers unlocked by earning points, like a battle pass. */
public record Category(Component title, Item icon, int order, TierVisibility tierVisibility, List<Tier> tiers) {

    public static final Codec<Category> CODEC = RecordCodecBuilder.<Category>create(i -> i.group(
            ComponentSerialization.CODEC.fieldOf("title").forGetter(Category::title),
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("icon").forGetter(Category::icon),
            Codec.INT.optionalFieldOf("order", 0).forGetter(Category::order),
            TierVisibility.CODEC.optionalFieldOf("tier_visibility", TierVisibility.VISIBLE).forGetter(Category::tierVisibility),
            Tier.CODEC.listOf().fieldOf("tiers").forGetter(Category::tiers)
    ).apply(i, Category::new)).validate(Category::validate);

    private static DataResult<Category> validate(Category category) {
        List<Tier> tiers = category.tiers();
        if (tiers.isEmpty()) {
            return DataResult.error(() -> "A category needs at least one tier");
        }
        if (tiers.getFirst().points() != 0) {
            return DataResult.error(() -> "The first tier must require 0 points");
        }
        for (int t = 1; t < tiers.size(); t++) {
            if (tiers.get(t).points() <= tiers.get(t - 1).points()) {
                int index = t + 1;
                return DataResult.error(() -> "Tier " + index + " must require more points than the previous tier");
            }
        }
        return DataResult.success(category);
    }

    /** Tier reached (1-based) with the given points. */
    public int tierFor(int points) {
        int tier = 0;
        for (Tier t : this.tiers) {
            if (points >= t.points()) {
                tier++;
            }
        }
        return Math.max(1, tier);
    }

    /** Points required for the given tier (1-based), or -1 past the last tier. */
    public int pointsForTier(int tier) {
        return tier >= 1 && tier <= this.tiers.size() ? this.tiers.get(tier - 1).points() : -1;
    }

    public record Tier(int points, Rewards rewards) {
        public static final Codec<Tier> CODEC = RecordCodecBuilder.create(i -> i.group(
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("points").forGetter(Tier::points),
                Rewards.CODEC.optionalFieldOf("rewards", Rewards.NONE).forGetter(Tier::rewards)
        ).apply(i, Tier::new));
    }
}
