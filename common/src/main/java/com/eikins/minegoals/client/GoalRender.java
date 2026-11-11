package com.eikins.minegoals.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Small drawing helpers shared by the HUD, the screen and the toast. Colors are ARGB. */
public final class GoalRender {

    public static final int WHITE = 0xFFFFFFFF;
    public static final int GRAY = 0xFFA0A0A0;
    public static final int DARK_GRAY = 0xFF606060;
    public static final int GOLD = 0xFFFFC94A;
    public static final int GREEN = 0xFF5BD75B;
    public static final int BAR_BG = 0xFF2A2A2A;
    public static final int PANEL = 0xC0101018;
    public static final int PANEL_LIGHT = 0xE0202030;

    private GoalRender() {
    }

    public static void bar(GuiGraphicsExtractor g, int x, int y, int width, int height, float fraction, int color) {
        g.fill(x, y, x + width, y + height, BAR_BG);
        int filled = Math.round(width * Math.max(0F, Math.min(1F, fraction)));
        if (filled > 0) {
            g.fill(x, y, x + filled, y + height, color);
        }
    }

    /** Draws text cut to {@code maxWidth} with an ellipsis. */
    public static void clippedText(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int maxWidth, int color) {
        String raw = text.getString();
        if (font.width(raw) <= maxWidth) {
            g.text(font, text, x, y, color, false);
        } else {
            String cut = font.plainSubstrByWidth(raw, maxWidth - font.width("…")) + "…";
            g.text(font, cut, x, y, color, false);
        }
    }

    public static float fraction(int value, int max) {
        return max <= 0 ? 1F : (float) value / max;
    }
}
