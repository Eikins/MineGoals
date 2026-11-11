package com.eikins.minegoals.data;

import com.eikins.minegoals.Constants;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.List;

/**
 * Rewards of a goal or a tier, granted when the player claims them from the goals screen.
 * <p>
 * Items use the vanilla item format: either an id ({@code "minecraft:diamond"}) or
 * {@code {"id": ..., "count": 1-99, "components": {...}}}, where components are the same data components as in
 * {@code /give} (custom name, enchantments, lore...). Parsing components such as enchantments needs registry access,
 * which {@link GoalDataLoader} provides.
 * <p>
 * Commands are {@link CommandReward}s: a command string, or an object with the commands to run and how the reward
 * looks in the goals screen (icon, name, description).
 */
public record Rewards(List<ItemStackTemplate> items, int experience, List<CommandReward> commands) {

    public static final Rewards NONE = new Rewards(List.of(), 0, List.of());

    public static final Codec<Rewards> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStackTemplate.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Rewards::items),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("experience", 0).forGetter(Rewards::experience),
            CommandReward.CODEC.listOf().optionalFieldOf("commands", List.of()).forGetter(Rewards::commands)
    ).apply(i, Rewards::new));

    public boolean isEmpty() {
        return this.items.isEmpty() && this.experience == 0 && this.commands.isEmpty();
    }

    public void grant(ServerPlayer player) {
        for (ItemStackTemplate reward : this.items) {
            // create() returns a fresh stack each time, so the template is never modified.
            ItemStack stack = reward.create();
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
            for (CommandReward reward : this.commands) {
                for (String command : reward.run()) {
                    try {
                        server.getCommands().performPrefixedCommand(source, command);
                    } catch (Exception e) {
                        Constants.LOG.error("Reward command failed: {}", command, e);
                    }
                }
            }
        }
    }
}
