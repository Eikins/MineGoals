package com.eikins.minegoals.data.criterion;

import com.eikins.minegoals.Constants;
import com.eikins.minegoals.data.RegistryMatcher;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatType;
import net.minecraft.stats.Stats;
import net.minecraft.util.ExtraCodecs;

/**
 * Criteria backed by vanilla statistics: blocks mined, entities killed, items picked up and items crafted.
 * Hooking the stat system covers all four without any loader-specific event.
 */
public record StatCriterion<T>(Kind<T> kind, RegistryMatcher<T> target, int count) implements GoalCriterion {

    public static final Kind<net.minecraft.world.level.block.Block> BLOCK =
            new Kind<>("break_block", "block", Stats.BLOCK_MINED, Registries.BLOCK, BuiltInRegistries.BLOCK);
    public static final Kind<net.minecraft.world.entity.EntityType<?>> ENTITY =
            new Kind<>("kill_entity", "entity", Stats.ENTITY_KILLED, Registries.ENTITY_TYPE, BuiltInRegistries.ENTITY_TYPE);
    public static final Kind<net.minecraft.world.item.Item> PICKUP =
            new Kind<>("collect_item", "item", Stats.ITEM_PICKED_UP, Registries.ITEM, BuiltInRegistries.ITEM);
    public static final Kind<net.minecraft.world.item.Item> CRAFT =
            new Kind<>("craft_item", "item", Stats.ITEM_CRAFTED, Registries.ITEM, BuiltInRegistries.ITEM);

    public static final MapCodec<StatCriterion<?>> BREAK_BLOCK = codec(BLOCK);
    public static final MapCodec<StatCriterion<?>> KILL_ENTITY = codec(ENTITY);
    public static final MapCodec<StatCriterion<?>> COLLECT_ITEM = codec(PICKUP);
    public static final MapCodec<StatCriterion<?>> CRAFT_ITEM = codec(CRAFT);

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> MapCodec<StatCriterion<?>> codec(Kind<T> kind) {
        MapCodec<StatCriterion<T>> codec = RecordCodecBuilder.mapCodec(i -> i.group(
                RegistryMatcher.codec(kind.registryKey()).fieldOf(kind.field()).forGetter(StatCriterion::target),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(StatCriterion::count)
        ).apply(i, (target, count) -> new StatCriterion<>(kind, target, count)));
        return (MapCodec) codec;
    }

    @Override
    public Identifier type() {
        return Constants.id(this.kind.name());
    }

    @Override
    @SuppressWarnings("unchecked")
    public int onStat(Stat<?> stat, int delta) {
        if (stat.getType() != this.kind.statType()) {
            return 0;
        }
        Holder<T> holder = this.kind.registry().wrapAsHolder((T) stat.getValue());
        return this.target.matches(holder) ? delta : 0;
    }

    public record Kind<T>(String name, String field, StatType<T> statType,
                          ResourceKey<? extends Registry<T>> registryKey, Registry<T> registry) {
    }
}
