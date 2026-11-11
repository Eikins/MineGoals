package com.eikins.minegoals.data;

import com.eikins.minegoals.Constants;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Rewards granted when a goal is completed or a tier is reached. */
public record Rewards(List<ItemReward> items, int experience, List<String> commands) {

    public static final Rewards NONE = new Rewards(List.of(), 0, List.of());

    public static final Codec<Rewards> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemReward.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Rewards::items),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("experience", 0).forGetter(Rewards::experience),
            Codec.STRING.listOf().optionalFieldOf("commands", List.of()).forGetter(Rewards::commands)
    ).apply(i, Rewards::new));

    public boolean isEmpty() {
        return this.items.isEmpty() && this.experience == 0 && this.commands.isEmpty();
    }

    public void grant(ServerPlayer player) {
        for (ItemReward reward : this.items) {
            ItemStack stack = new ItemStack(reward.item(), reward.count());
            if (!player.getInventory().add(stack)) {
                player.spawnAtLocation(player.level(), stack);
            }
        }
        if (this.experience > 0) {
            player.giveExperiencePoints(this.experience);
        }
        if (!this.commands.isEmpty()) {
            MinecraftServer server = player.level().getServer();
            var source = player.createCommandSourceStack()
                    .withPermission(LevelBasedPermissionSet.GAMEMASTER)
                    .withSuppressedOutput();
            for (String command : this.commands) {
                try {
                    server.getCommands().performPrefixedCommand(source, command);
                } catch (Exception e) {
                    Constants.LOG.error("Reward command failed: {}", command, e);
                }
            }
        }
    }

    public record ItemReward(Item item, int count) {
        public static final Codec<ItemReward> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("id").forGetter(ItemReward::item),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(ItemReward::count)
        ).apply(i, ItemReward::new));
    }
}
