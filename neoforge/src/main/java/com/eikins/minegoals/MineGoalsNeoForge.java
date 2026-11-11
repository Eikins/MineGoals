package com.eikins.minegoals;

import com.eikins.minegoals.command.MineGoalsCommand;
import com.eikins.minegoals.data.GoalDataLoader;
import com.eikins.minegoals.network.MineGoalsNetwork;
import com.eikins.minegoals.progress.GoalTracker;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(Constants.MOD_ID)
public class MineGoalsNeoForge {

    public MineGoalsNeoForge(IEventBus modBus) {
        CommonClass.init();

        modBus.addListener(MineGoalsNeoForge::registerPayloads);
        NeoForge.EVENT_BUS.addListener((AddServerReloadListenersEvent event) ->
                event.addListener(GoalDataLoader.ID, new GoalDataLoader(event.getRegistryAccess())));
        // Fires for a joining player and for everyone after /reload.
        NeoForge.EVENT_BUS.addListener((OnDatapackSyncEvent event) ->
                event.getRelevantPlayers().forEach(GoalTracker::syncPlayer));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                MineGoalsCommand.register(event.getDispatcher()));
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // Client handlers are registered in MineGoalsNeoForgeClient, so servers never load client classes.
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(MineGoalsNetwork.SyncDefinitions.TYPE, MineGoalsNetwork.SyncDefinitions.STREAM_CODEC);
        registrar.playToClient(MineGoalsNetwork.SyncProgress.TYPE, MineGoalsNetwork.SyncProgress.STREAM_CODEC);
        registrar.playToClient(MineGoalsNetwork.Notify.TYPE, MineGoalsNetwork.Notify.STREAM_CODEC);
        registrar.playToServer(MineGoalsNetwork.ClaimReward.TYPE, MineGoalsNetwork.ClaimReward.STREAM_CODEC,
                (payload, ctx) -> GoalTracker.claim((ServerPlayer) ctx.player(), payload.id(), payload.tier()));
    }
}
