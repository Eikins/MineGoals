package com.eikins.minegoals.client;

import com.eikins.minegoals.data.GoalDefinitions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Bottom-right HUD, built from vanilla sprites: a journal in a status-effect frame, the key that opens the goals
 * screen on a small vanilla button, and the vanilla "!" notification badge when a reward is ready to be claimed.
 */
public final class JournalHud {

    private static final ItemStack JOURNAL = new ItemStack(Items.WRITABLE_BOOK);
    private static final int MARGIN = 4;
    private static final int FRAME = 24;
    private static final int KEY_HEIGHT = 13;

    private JournalHud() {
    }

    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        GoalDefinitions defs = ClientGoalState.definitions();
        if (mc.player == null || defs.goals().isEmpty() || mc.gui.hud.isHidden()) {
            return;
        }
        Font font = mc.font;
        int x = g.guiWidth() - MARGIN - FRAME;
        int y = g.guiHeight() - MARGIN - FRAME;

        // Journal in the status-effect frame
        VanillaSprites.sprite(g, VanillaSprites.EFFECT_BACKGROUND, x, y, FRAME, FRAME);
        g.item(JOURNAL, x + 4, y + 4);

        // Key on a small vanilla button, on the left: [G]
        Component key = ClientKeys.openKeyName();
        int keyWidth = font.width(key) + 8;
        int keyX = x - 3 - keyWidth;
        int keyY = y + (FRAME - KEY_HEIGHT) / 2;
        VanillaSprites.sprite(g, VanillaSprites.BUTTON, keyX, keyY, keyWidth, KEY_HEIGHT);
        g.text(font, key, keyX + 4, keyY + 3, 0xFFFFFFFF, true);

        // Vanilla notification badge over the frame's top-right corner when a reward is waiting
        if (ClientGoalState.progress().claimableCount(defs) > 0) {
            VanillaSprites.sprite(g, VanillaSprites.NOTIFICATION, x + FRAME - 7, y - 3, 10, 10);
        }
    }
}
