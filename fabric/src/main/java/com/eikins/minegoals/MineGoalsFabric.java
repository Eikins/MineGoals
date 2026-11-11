package com.eikins.minegoals;

import com.eikins.minegoals.command.MineGoalsCommand;
import com.eikins.minegoals.data.GoalDataLoader;
import com.eikins.minegoals.network.MineGoalsNetwork;
import com.eikins.minegoals.progress.GoalTracker;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.v1.DataResourceLoader;

public class MineGoalsFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        CommonClass.init();

        PayloadTypeRegistry.clientboundPlay().register(MineGoalsNetwork.SyncDefinitions.TYPE, MineGoalsNetwork.SyncDefinitions.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MineGoalsNetwork.SyncProgress.TYPE, MineGoalsNetwork.SyncProgress.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MineGoalsNetwork.Notify.TYPE, MineGoalsNetwork.Notify.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MineGoalsNetwork.ClaimReward.TYPE, MineGoalsNetwork.ClaimReward.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(MineGoalsNetwork.ClaimReward.TYPE,
                (payload, ctx) -> GoalTracker.claim(ctx.player(), payload.id(), payload.tier()));
        PayloadTypeRegistry.serverboundPlay().register(MineGoalsNetwork.TrackGoal.TYPE, MineGoalsNetwork.TrackGoal.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(MineGoalsNetwork.TrackGoal.TYPE,
                (payload, ctx) -> GoalTracker.setTracked(ctx.player(), payload.goal(), payload.tracked()));

        DataResourceLoader.get().registerReloadListener(GoalDataLoader.ID, GoalDataLoader::new);
        // Fires for a joining player and for everyone after /reload.
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> GoalTracker.syncPlayer(player));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> MineGoalsCommand.register(dispatcher));
    }
}
