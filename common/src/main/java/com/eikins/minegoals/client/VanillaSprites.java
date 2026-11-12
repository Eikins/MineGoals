package com.eikins.minegoals.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Vanilla GUI sprites and textures used by MineGoals, and helpers to draw them at any size. */
public final class VanillaSprites {

    // Advancement screen
    public static final Identifier WINDOW = Identifier.withDefaultNamespace("textures/gui/advancements/window.png");
    public static final Identifier TAB_LEFT = sprite("advancements/tab_above_left");
    public static final Identifier TAB_LEFT_SELECTED = sprite("advancements/tab_above_left_selected");
    public static final Identifier TAB_MIDDLE = sprite("advancements/tab_above_middle");
    public static final Identifier TAB_MIDDLE_SELECTED = sprite("advancements/tab_above_middle_selected");
    public static final Identifier TASK_FRAME_OBTAINED = sprite("advancements/task_frame_obtained");
    public static final Identifier TASK_FRAME_UNOBTAINED = sprite("advancements/task_frame_unobtained");
    public static final Identifier GOAL_FRAME_OBTAINED = sprite("advancements/goal_frame_obtained");
    public static final Identifier GOAL_FRAME_UNOBTAINED = sprite("advancements/goal_frame_unobtained");
    public static final Identifier CHALLENGE_FRAME_OBTAINED = sprite("advancements/challenge_frame_obtained");
    public static final Identifier CHALLENGE_FRAME_UNOBTAINED = sprite("advancements/challenge_frame_unobtained");
    public static final Identifier BOX_OBTAINED = sprite("advancements/box_obtained");
    public static final Identifier BOX_UNOBTAINED = sprite("advancements/box_unobtained");

    // Widgets
    public static final Identifier BUTTON = sprite("widget/button");
    public static final Identifier BUTTON_HIGHLIGHTED = sprite("widget/button_highlighted");
    public static final Identifier PAGE_BACKWARD = sprite("widget/page_backward");
    public static final Identifier PAGE_BACKWARD_HIGHLIGHTED = sprite("widget/page_backward_highlighted");
    public static final Identifier PAGE_FORWARD = sprite("widget/page_forward");
    public static final Identifier PAGE_FORWARD_HIGHLIGHTED = sprite("widget/page_forward_highlighted");
    public static final Identifier SCROLLER = sprite("widget/scroller");
    public static final Identifier SCROLLER_BACKGROUND = sprite("widget/scroller_background");
    public static final Identifier SLOT = sprite("container/slot");
    public static final Identifier CHECKMARK = sprite("icon/checkmark");
    public static final Identifier NOTIFICATION = sprite("icon/unseen_notification");

    // HUD
    public static final Identifier XP_BAR_BACKGROUND = sprite("hud/experience_bar_background");
    public static final Identifier XP_BAR_PROGRESS = sprite("hud/experience_bar_progress");
    public static final Identifier EFFECT_BACKGROUND = sprite("hud/effect_background");

    public static final Identifier DEFAULT_BACKGROUND =
            Identifier.withDefaultNamespace("textures/gui/advancements/backgrounds/stone.png");

    /** The advancement window: 252x140 in a 256x256 texture, with a 9 px frame and an 18 px title strip. */
    public static final int WINDOW_BORDER = 9;
    public static final int WINDOW_TITLE = 18;
    private static final int WINDOW_W = 252;
    private static final int WINDOW_H = 140;

    public static final int XP_BAR_WIDTH = 182;

    private VanillaSprites() {
    }

    private static Identifier sprite(String path) {
        return Identifier.withDefaultNamespace(path);
    }

    public static void sprite(GuiGraphicsExtractor g, Identifier sprite, int x, int y, int width, int height) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height);
    }

    public static void sprite(GuiGraphicsExtractor g, Identifier sprite, int x, int y, int width, int height, float alpha) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height, alpha);
    }

    /** Draws the advancement window stretched to any size: corners as-is, edges stretched (they are uniform). */
    public static void window(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        int l = WINDOW_BORDER;
        int r = WINDOW_BORDER;
        int t = WINDOW_TITLE;
        int b = WINDOW_BORDER;
        int midW = width - l - r;
        int midH = height - t - b;
        int srcMidW = WINDOW_W - l - r;
        int srcMidH = WINDOW_H - t - b;
        // corners
        region(g, x, y, 0, 0, l, t, l, t);
        region(g, x + width - r, y, WINDOW_W - r, 0, r, t, r, t);
        region(g, x, y + height - b, 0, WINDOW_H - b, l, b, l, b);
        region(g, x + width - r, y + height - b, WINDOW_W - r, WINDOW_H - b, r, b, r, b);
        // edges
        region(g, x + l, y, l, 0, midW, t, srcMidW, t);
        region(g, x + l, y + height - b, l, WINDOW_H - b, midW, b, srcMidW, b);
        region(g, x, y + t, 0, t, l, midH, l, srcMidH);
        region(g, x + width - r, y + t, WINDOW_W - r, t, r, midH, r, srcMidH);
    }

    private static void region(GuiGraphicsExtractor g, int x, int y, int u, int v, int width, int height,
                               int regionWidth, int regionHeight) {
        if (width > 0 && height > 0) {
            g.blit(RenderPipelines.GUI_TEXTURED, WINDOW, x, y, u, v, width, height, regionWidth, regionHeight, 256, 256);
        }
    }

    /** Tiles a 16x16 advancement background texture over an area, cropping the last row and column. */
    public static void tile(GuiGraphicsExtractor g, Identifier texture, int x, int y, int width, int height) {
        for (int ty = 0; ty < height; ty += 16) {
            for (int tx = 0; tx < width; tx += 16) {
                int w = Math.min(16, width - tx);
                int h = Math.min(16, height - ty);
                g.blit(RenderPipelines.GUI_TEXTURED, texture, x + tx, y + ty, 0, 0, w, h, 16, 16);
            }
        }
    }

    /** The vanilla experience bar, up to 182 px wide, with a fill fraction. */
    public static void xpBar(GuiGraphicsExtractor g, int x, int y, int width, float fraction) {
        int w = Math.min(width, XP_BAR_WIDTH);
        // Keep the right end cap: draw the left part, then the last 2 px of the sprite.
        g.blitSprite(RenderPipelines.GUI_TEXTURED, XP_BAR_BACKGROUND, XP_BAR_WIDTH, 5, 0, 0, x, y, w - 2, 5);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, XP_BAR_BACKGROUND, XP_BAR_WIDTH, 5, XP_BAR_WIDTH - 2, 0, x + w - 2, y, 2, 5);
        int filled = Math.round(w * Math.max(0F, Math.min(1F, fraction)));
        if (filled > 0) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, XP_BAR_PROGRESS, XP_BAR_WIDTH, 5, 0, 0, x, y, filled, 5);
        }
    }

    /** Text with a 1 px black outline, like the experience level number above the hotbar. */
    public static void outlinedText(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, String text, int x, int y, int color) {
        g.text(font, text, x + 1, y, 0xFF000000, false);
        g.text(font, text, x - 1, y, 0xFF000000, false);
        g.text(font, text, x, y + 1, 0xFF000000, false);
        g.text(font, text, x, y - 1, 0xFF000000, false);
        g.text(font, text, x, y, color, false);
    }
}
