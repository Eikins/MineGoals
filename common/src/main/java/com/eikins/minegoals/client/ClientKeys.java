package com.eikins.minegoals.client;

import com.eikins.minegoals.Constants;
import com.eikins.minegoals.FeatureFlags;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Key bindings: G opens the goals screen. With {@link FeatureFlags#GOAL_PANEL}, H toggles the goals panel and
 * J shows the next category in it.
 * Created by each loader with its key category.
 */
public final class ClientKeys {

    public static final net.minecraft.resources.Identifier CATEGORY_ID = Constants.id("main");

    private static KeyMapping openScreen;
    private static KeyMapping toggleHud;
    private static KeyMapping nextCategory;

    private ClientKeys() {
    }

    /** Creates the key bindings to register. H and J only exist when the goals panel is enabled. */
    public static KeyMapping[] create(KeyMapping.Category category) {
        openScreen = new KeyMapping("key.minegoals.open", InputConstants.KEY_G, category);
        if (!FeatureFlags.GOAL_PANEL) {
            return new KeyMapping[]{openScreen};
        }
        toggleHud = new KeyMapping("key.minegoals.toggle_hud", InputConstants.KEY_H, category);
        nextCategory = new KeyMapping("key.minegoals.next_category", InputConstants.KEY_J, category);
        return new KeyMapping[]{openScreen, toggleHud, nextCategory};
    }

    /** Name of the key that opens the goals screen, as bound in the controls menu. */
    public static Component openKeyName() {
        return openScreen == null ? Component.literal("G") : openScreen.getTranslatedKeyMessage();
    }

    /** Called at the end of every client tick. */
    public static void onClientTick(Minecraft mc) {
        if (openScreen == null || mc.player == null) {
            return;
        }
        while (openScreen.consumeClick()) {
            mc.gui.setScreen(new GoalsScreen());
        }
        if (toggleHud == null) {
            return;
        }
        while (toggleHud.consumeClick()) {
            ClientGoalState.toggleHud();
        }
        while (nextCategory.consumeClick()) {
            ClientGoalState.cycleCategory();
        }
    }
}
