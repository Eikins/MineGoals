package com.eikins.minegoals.network;

import com.eikins.minegoals.Constants;
import com.eikins.minegoals.data.GoalDefinitions;
import com.eikins.minegoals.progress.PlayerGoalProgress;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * Payloads. Each loader registers them. Server-to-client handlers live in {@code client.ClientPayloadHandlers};
 * client-to-server payloads ({@link ClaimReward}, {@link TrackGoal}, {@link CollectPendingItems}) are handled by
 * {@code GoalTracker}.
 */
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

    /**
     * Client to server: claim the rewards of a completed goal ({@code tier == 0}) or of a reached tier
     * ({@code id} is then the category). The server validates everything.
     */
    public record ClaimReward(Identifier id, int tier) implements CustomPacketPayload {
        public static final Type<ClaimReward> TYPE = new Type<>(Constants.id("claim_reward"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ClaimReward> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, ClaimReward::id,
                ByteBufCodecs.VAR_INT, ClaimReward::tier,
                ClaimReward::new);

        public static ClaimReward goal(Identifier goal) {
            return new ClaimReward(goal, 0);
        }

        public static ClaimReward tier(Identifier category, int tier) {
            return new ClaimReward(category, tier);
        }

        @Override
        public Type<ClaimReward> type() {
            return TYPE;
        }
    }

    /** Client to server: start ({@code tracked == true}) or stop following a goal. Handled by {@code GoalTracker.setTracked}. */
    public record TrackGoal(Identifier goal, boolean tracked) implements CustomPacketPayload {
        public static final Type<TrackGoal> TYPE = new Type<>(Constants.id("track_goal"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TrackGoal> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, TrackGoal::goal,
                ByteBufCodecs.BOOL, TrackGoal::tracked,
                TrackGoal::new);

        @Override
        public Type<TrackGoal> type() {
            return TYPE;
        }
    }

    /** Client to server: move pending reward items into the inventory. Handled by {@code GoalTracker.collectPendingItems}. */
    public record CollectPendingItems() implements CustomPacketPayload {
        public static final CollectPendingItems INSTANCE = new CollectPendingItems();
        public static final Type<CollectPendingItems> TYPE = new Type<>(Constants.id("collect_pending_items"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CollectPendingItems> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<CollectPendingItems> type() {
            return TYPE;
        }
    }

    /** Shows a toast with an item icon: a category was unlocked, a goal was completed or a tier was reached. */
    public record Notify(Component title, Component message, Holder<Item> icon) implements CustomPacketPayload {
        public static final Type<Notify> TYPE = new Type<>(Constants.id("notify"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Notify> STREAM_CODEC = StreamCodec.composite(
                ComponentSerialization.STREAM_CODEC, Notify::title,
                ComponentSerialization.STREAM_CODEC, Notify::message,
                Item.STREAM_CODEC, Notify::icon,
                Notify::new);

        @Override
        public Type<Notify> type() {
            return TYPE;
        }
    }
}
