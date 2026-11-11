package com.eikins.minegoals.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Optional;

/**
 * A goal category: a track of tiers unlocked by earning points, like a battle pass. {@code background} is the texture
 * tiled behind the goals screen, e.g. {@code minecraft:textures/gui/advancements/backgrounds/nether.png}.
 */
public record Category(Component title, Item icon, int order, TierVisibility tierVisibility, CategoryUnlock unlock,
                       Optional<Identifier> background, List<Tier> tiers) {

    public static final Codec<Category> CODEC = RecordCodecBuilder.<Category>create(i -> i.group(
            ComponentSerialization.CODEC.fieldOf("title").forGetter(Category::title),
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("icon").forGetter(Category::icon),
            Codec.INT.optionalFieldOf("order", 0).forGetter(Category::order),
            TierVisibility.CODEC.optionalFieldOf("tier_visibility", TierVisibility.VISIBLE).forGetter(Category::tierVisibility),
            CategoryUnlock.CODEC.optionalFieldOf("unlock", CategoryUnlock.ALWAYS).forGetter(Category::unlock),
            Identifier.CODEC.optionalFieldOf("background").forGetter(Category::background),
            Tier.CODEC.listOf().fieldOf("tiers").forGetter(Category::tiers)
    ).apply(i, Category::new)).validate(Category::validate);

    private static DataResult<Category> validate(Category category) {
        List<Tier> tiers = category.tiers();
        if (tiers.isEmpty()) {
            return DataResult.error(() -> "A category needs at least one tier");
        }
        return DataResult.success(category);
    }

    /** Points a tier (1-based) needs, earned from its own goals, or -1 outside the track. */
    public int pointsNeeded(int tier) {
        return tier >= 1 && tier <= this.tiers.size() ? this.tiers.get(tier - 1).points() : -1;
    }

    /**
     * A tier of the track. {@code points} is how many points the player must earn from this tier's own goals to
     * complete it; its {@code rewards} are earned by completing it. {@code background} (optional) replaces the
     * category's background in the goals screen while this tier is selected.
     */
    public record Tier(int points, Rewards rewards, Optional<Identifier> background) {
        public static final Codec<Tier> CODEC = RecordCodecBuilder.create(i -> i.group(
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("points").forGetter(Tier::points),
                Rewards.CODEC.optionalFieldOf("rewards", Rewards.NONE).forGetter(Tier::rewards),
                Identifier.CODEC.optionalFieldOf("background").forGetter(Tier::background)
        ).apply(i, Tier::new));
    }

    /** Background for a tier (1-based): the tier's own, else the category's. */
    public Optional<Identifier> backgroundFor(int tier) {
        if (tier >= 1 && tier <= this.tiers.size() && this.tiers.get(tier - 1).background().isPresent()) {
            return this.tiers.get(tier - 1).background();
        }
        return this.background;
    }
}
