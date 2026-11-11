package com.eikins.minegoals.client;

import com.eikins.minegoals.Constants;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Key bindings: G opens the goals screen, H toggles the HUD, J shows the next category in the HUD.
 * Created by each loader with its key category.
 */
public final class ClientKeys {

    public static final net.minecraft.resources.Identifier CATEGORY_ID = Constants.id("main");

    private static KeyMapping openScreen;
    private static KeyMapping toggleHud;
    private static KeyMapping nextCategory;

    private ClientKeys() {
    }

    public static KeyMapping[] create(KeyMapping.Category category) {
        openScreen = new KeyMapping("key.minegoals.open", InputConstants.KEY_G, category);
        toggleHud = new KeyMapping("key.minegoals.toggle_hud", InputConstants.KEY_H, category);
        nextCategory = new KeyMapping("key.minegoals.next_category", InputConstants.KEY_J, category);
        return new KeyMapping[]{openScreen, toggleHud, nextCategory};
    }

    /** Called at the end of every client tick. */
    public static void onClientTick(Minecraft mc) {
        if (openScreen == null || mc.player == null) {
            return;
        }
        while (openScreen.consumeClick()) {
            mc.gui.setScreen(new GoalsScreen());
        }
        while (toggleHud.consumeClick()) {
            ClientGoalState.toggleHud();
        }
        while (nextCategory.consumeClick()) {
            ClientGoalState.cycleCategory();
        }
    }
}
