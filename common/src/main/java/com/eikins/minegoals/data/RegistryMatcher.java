package com.eikins.minegoals.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

/**
 * Matches a registry entry either by id ({@code "minecraft:stone"}) or by tag ({@code "#minecraft:logs"}).
 * Kept as plain ids so goal files can be parsed without registry access.
 */
public record RegistryMatcher<T>(Identifier id, boolean isTag, ResourceKey<? extends Registry<T>> registry) {

    public static <T> Codec<RegistryMatcher<T>> codec(ResourceKey<? extends Registry<T>> registry) {
        return Codec.STRING.comapFlatMap(
                raw -> {
                    boolean tag = raw.startsWith("#");
                    return Identifier.read(tag ? raw.substring(1) : raw)
                            .map(id -> new RegistryMatcher<>(id, tag, registry));
                },
                RegistryMatcher::toString
        );
    }

    public boolean matches(Holder<T> holder) {
        if (this.isTag) {
            return holder.is(TagKey.create(this.registry, this.id));
        }
        return holder.is(this.id);
    }

    @Override
    public String toString() {
        return this.isTag ? "#" + this.id : this.id.toString();
    }

    static DataResult<Integer> positive(int value) {
        return value > 0 ? DataResult.success(value) : DataResult.error(() -> "Value must be > 0, got " + value);
    }
}
