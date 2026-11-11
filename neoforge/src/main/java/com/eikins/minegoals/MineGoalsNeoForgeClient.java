package com.eikins.minegoals;

import com.eikins.minegoals.client.ClientGoalState;
import com.eikins.minegoals.client.ClientKeys;
import com.eikins.minegoals.client.ClientPayloadHandlers;
import com.eikins.minegoals.client.GoalHud;
import com.eikins.minegoals.network.MineGoalsNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Constants.MOD_ID, dist = Dist.CLIENT)
public class MineGoalsNeoForgeClient {

    public MineGoalsNeoForgeClient(IEventBus modBus) {
        modBus.addListener((RegisterClientPayloadHandlersEvent event) -> {
            event.register(MineGoalsNetwork.SyncDefinitions.TYPE, (payload, ctx) -> ClientPayloadHandlers.onDefinitions(payload));
            event.register(MineGoalsNetwork.SyncProgress.TYPE, (payload, ctx) -> ClientPayloadHandlers.onProgress(payload));
            event.register(MineGoalsNetwork.Notify.TYPE, (payload, ctx) -> ClientPayloadHandlers.onNotify(payload));
        });
        ClientGoalState.setClaimSender(payload -> ClientPacketDistributor.sendToServer(payload));
        modBus.addListener((RegisterGuiLayersEvent event) ->
                event.registerAboveAll(GoalHud.ID, (graphics, deltaTracker) -> GoalHud.render(graphics)));
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            KeyMapping.Category category = new KeyMapping.Category(ClientKeys.CATEGORY_ID);
            event.registerCategory(category);
            for (KeyMapping key : ClientKeys.create(category)) {
                event.register(key);
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> ClientKeys.onClientTick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientGoalState.reset());
    }
}
