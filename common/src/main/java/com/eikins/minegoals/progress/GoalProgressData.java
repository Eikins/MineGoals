package com.eikins.minegoals.progress;

import com.eikins.minegoals.Constants;
import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** World-level storage of every player's progress ({@code data/minegoals_progress.dat}). */
public class GoalProgressData extends SavedData {

    private static final Codec<GoalProgressData> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, PlayerGoalProgress.CODEC)
            .xmap(GoalProgressData::new, data -> data.players);

    // Our data has no vanilla data fixers; this type is a no-op when the stored version is current.
    public static final SavedDataType<GoalProgressData> TYPE = new SavedDataType<>(
            Constants.id("progress"), GoalProgressData::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<UUID, PlayerGoalProgress> players;

    public GoalProgressData() {
        this(Map.of());
    }

    private GoalProgressData(Map<UUID, PlayerGoalProgress> players) {
        this.players = new HashMap<>(players);
    }

    public static GoalProgressData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public PlayerGoalProgress get(UUID player) {
        return this.players.computeIfAbsent(player, id -> new PlayerGoalProgress());
    }
}
