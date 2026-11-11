package com.eikins.minegoals;

import com.eikins.minegoals.client.ClientGoalState;
import com.eikins.minegoals.client.ClientKeys;
import com.eikins.minegoals.client.ClientPayloadHandlers;
import com.eikins.minegoals.client.GoalHud;
import com.eikins.minegoals.network.MineGoalsNetwork;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;

public class MineGoalsFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(MineGoalsNetwork.SyncDefinitions.TYPE, (payload, ctx) -> ClientPayloadHandlers.onDefinitions(payload));
        ClientPlayNetworking.registerGlobalReceiver(MineGoalsNetwork.SyncProgress.TYPE, (payload, ctx) -> ClientPayloadHandlers.onProgress(payload));
        ClientPlayNetworking.registerGlobalReceiver(MineGoalsNetwork.Notify.TYPE, (payload, ctx) -> ClientPayloadHandlers.onNotify(payload));
        ClientGoalState.setServerSender(ClientPlayNetworking::send);

        HudElementRegistry.addLast(GoalHud.ID, (graphics, deltaTracker) -> GoalHud.render(graphics));

        KeyMapping.Category category = KeyMapping.Category.register(ClientKeys.CATEGORY_ID);
        for (KeyMapping key : ClientKeys.create(category)) {
            KeyMappingHelper.registerKeyMapping(key);
        }
        ClientTickEvents.END_CLIENT_TICK.register(ClientKeys::onClientTick);
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> ClientGoalState.reset());
    }
}
