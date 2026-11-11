package com.eikins.minegoals.data;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.List;
import java.util.Optional;

/**
 * A reward that runs one or more commands, shown in the goals screen with an optional custom icon, name and
 * description. Written either as a plain command string or as:
 * <pre>
 * {"run": "effect give @s minecraft:speed 60" | [...], "icon": "minecraft:sugar" | {"id": ..., "components": ...}
 *  | {"texture": "minegoals:textures/gui/reward/speed.png"}, "name": "Speed boost", "description": "60 seconds"}
 * </pre>
 */
public record CommandReward(List<String> run, Optional<Icon> icon, Optional<Component> name, Optional<Component> description) {

    private static final Codec<CommandReward> FULL_CODEC = RecordCodecBuilder.create(i -> i.group(
            ExtraCodecs.compactListCodec(Codec.STRING).fieldOf("run").forGetter(CommandReward::run),
            Icon.CODEC.optionalFieldOf("icon").forGetter(CommandReward::icon),
            ComponentSerialization.CODEC.optionalFieldOf("name").forGetter(CommandReward::name),
            ComponentSerialization.CODEC.optionalFieldOf("description").forGetter(CommandReward::description)
    ).apply(i, CommandReward::new));

    /** A plain string is a single command with the default icon and name. */
    public static final Codec<CommandReward> CODEC = Codec.either(Codec.STRING, FULL_CODEC).xmap(
            either -> either.map(CommandReward::of, reward -> reward),
            reward -> reward.isPlain() ? Either.left(reward.run().getFirst()) : Either.right(reward));

    public static CommandReward of(String command) {
        return new CommandReward(List.of(command), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private boolean isPlain() {
        return this.run.size() == 1 && this.icon.isEmpty() && this.name.isEmpty() && this.description.isEmpty();
    }

    /** What a command reward looks like in the goals screen: an item (with components) or a texture. */
    public sealed interface Icon permits ItemIcon, TextureIcon {
        Codec<Icon> CODEC = Codec.either(TextureIcon.CODEC.codec(), ItemStackTemplate.CODEC).xmap(
                either -> either.map(texture -> texture, ItemIcon::new),
                icon -> icon instanceof TextureIcon texture ? Either.left(texture) : Either.right(((ItemIcon) icon).item()));
    }

    public record ItemIcon(ItemStackTemplate item) implements Icon {
    }

    /** Full texture path, e.g. {@code minegoals:textures/gui/reward/speed.png}; drawn at 16x16. */
    public record TextureIcon(Identifier texture) implements Icon {
        static final MapCodec<TextureIcon> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Identifier.CODEC.fieldOf("texture").forGetter(TextureIcon::texture)
        ).apply(i, TextureIcon::new));
    }
}
