package com.eikins.minegoals.client;

import com.eikins.minegoals.network.MineGoalsNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/** Client handlers for the server payloads. Must run on the client main thread. */
public final class ClientPayloadHandlers {

    private ClientPayloadHandlers() {
    }

    public static void onDefinitions(MineGoalsNetwork.SyncDefinitions payload) {
        ClientGoalState.setDefinitions(payload.definitions());
    }

    public static void onProgress(MineGoalsNetwork.SyncProgress payload) {
        ClientGoalState.setProgress(payload.progress());
    }

    public static void onNotify(MineGoalsNetwork.Notify payload) {
        Minecraft.getInstance().gui.toastManager().addToast(
                new GoalToast(payload.title(), payload.message(), new ItemStack(payload.icon())));
    }
}
