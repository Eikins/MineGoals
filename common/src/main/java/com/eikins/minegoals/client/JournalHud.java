package com.eikins.minegoals.client;

import com.eikins.minegoals.data.GoalDefinitions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Bottom-right HUD: a journal icon with the key that opens the goals screen, and a green dot on the icon's top-right
 * corner when a reward is ready to be claimed.
 */
public final class JournalHud {

    private static final ItemStack JOURNAL = new ItemStack(Items.WRITABLE_BOOK);
    private static final int MARGIN = 4;
    private static final int BOX = 20;
    private static final int DOT_SIZE = 6;
    private static final int DOT_COLOR = 0xFF3FD24F;

    private JournalHud() {
    }

    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        GoalDefinitions defs = ClientGoalState.definitions();
        if (mc.player == null || defs.goals().isEmpty() || mc.gui.hud.isHidden()) {
            return;
        }
        Font font = mc.font;
        int x = g.guiWidth() - MARGIN - BOX;
        int y = g.guiHeight() - MARGIN - BOX;

        // Journal icon
        pill(g, x, y, BOX, BOX, 0x80000000);
        g.item(JOURNAL, x + 2, y + 2);

        // Key cap on the left: [G]
        Component key = ClientKeys.openKeyName();
        int keyWidth = font.width(key) + 6;
        int keyX = x - 3 - keyWidth;
        int keyY = y + (BOX - 11) / 2;
        pill(g, keyX, keyY, keyWidth, 11, 0x80000000);
        g.outline(keyX, keyY, keyWidth, 11, 0x60FFFFFF);
        g.text(font, key, keyX + 3, keyY + 2, GoalRender.WHITE, false);

        // Green dot over the icon's top-right corner when at least one reward is waiting
        if (ClientGoalState.progress().claimableCount(defs) > 0) {
            int dotX = x + BOX - DOT_SIZE + 2;
            int dotY = y - 2;
            pill(g, dotX - 1, dotY - 1, DOT_SIZE + 2, DOT_SIZE + 2, 0xFF000000);
            pill(g, dotX, dotY, DOT_SIZE, DOT_SIZE, DOT_COLOR);
        }
    }

    /** A rectangle with its four corner pixels cut, which reads as rounded at GUI scale. */
    private static void pill(GuiGraphicsExtractor g, int x, int y, int width, int height, int color) {
        g.fill(x + 1, y, x + width - 1, y + height, color);
        g.fill(x, y + 1, x + 1, y + height - 1, color);
        g.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }
}
