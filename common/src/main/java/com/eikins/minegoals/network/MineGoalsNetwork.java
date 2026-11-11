package com.eikins.minegoals.network;

import com.eikins.minegoals.Constants;
import com.eikins.minegoals.data.GoalDefinitions;
import com.eikins.minegoals.progress.PlayerGoalProgress;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server-to-client payloads. Each loader registers them; handlers live in {@code client.ClientPayloadHandlers}. */
public final class MineGoalsNetwork {

    private MineGoalsNetwork() {
    }

    /** All categories and goals. Sent on join and after {@code /reload}. */
    public record SyncDefinitions(GoalDefinitions definitions) implements CustomPacketPayload {
        public static final Type<SyncDefinitions> TYPE = new Type<>(Constants.id("sync_definitions"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncDefinitions> STREAM_CODEC =
                ByteBufCodecs.fromCodecWithRegistries(GoalDefinitions.CODEC).map(SyncDefinitions::new, SyncDefinitions::definitions);

        @Override
        public Type<SyncDefinitions> type() {
            return TYPE;
        }
    }

    /** The receiving player's full progress. Sent whenever it changes. */
    public record SyncProgress(PlayerGoalProgress progress) implements CustomPacketPayload {
        public static final Type<SyncProgress> TYPE = new Type<>(Constants.id("sync_progress"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncProgress> STREAM_CODEC =
                ByteBufCodecs.fromCodecWithRegistries(PlayerGoalProgress.CODEC).map(SyncProgress::new, SyncProgress::progress);

        @Override
        public Type<SyncProgress> type() {
            return TYPE;
        }
    }

    /** Shows a toast: a goal was completed or a tier was reached. */
    public record Notify(Component title, Component message) implements CustomPacketPayload {
        public static final Type<Notify> TYPE = new Type<>(Constants.id("notify"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Notify> STREAM_CODEC = StreamCodec.composite(
                ComponentSerialization.STREAM_CODEC, Notify::title,
                ComponentSerialization.STREAM_CODEC, Notify::message,
                Notify::new);

        @Override
        public Type<Notify> type() {
            return TYPE;
        }
    }
}
