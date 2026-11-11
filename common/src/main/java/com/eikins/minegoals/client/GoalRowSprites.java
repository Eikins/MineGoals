package com.eikins.minegoals.client;

import com.eikins.minegoals.Constants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/**
 * MineGoals' own goal-row sprites ({@code assets/minegoals/textures/gui/sprites/goal/}) and how to draw them.
 * <p>
 * A row background is 361 px wide: an icon slot (x 0-31), a text area above a progress groove (x 32-242), three
 * reward slots (x 246, 264, 282) and a button area (x 303, 54x12). Rows can be drawn at any width: the groove is
 * made of 10 px segments, so the middle repeats the segment at x 42-51 and the right part (slots and button) keeps
 * its pixels.
 */
public final class GoalRowSprites {

    public static final Identifier BACKGROUND = Constants.id("goal/background");
    public static final Identifier PRIMARY_BACKGROUND = Constants.id("goal/primary_background");
    public static final Identifier BUTTON_CLAIM = Constants.id("goal/button_claim");
    public static final Identifier BUTTON_COMPLETED = Constants.id("goal/button_completed");
    public static final Identifier BUTTON_TRACK = Constants.id("goal/button_track");
    public static final Identifier PROGRESS_EMPTY = Constants.id("goal/progress_empty");
    public static final Identifier PROGRESS_FULL = Constants.id("goal/progress_full");
    /** Black silhouette of a normal row, drawn translucent over claimed goals. */
    public static final Identifier MASK = Constants.id("goal/mask");
    public static final float MASK_ALPHA = 0.6F;

    public static final int SOURCE_WIDTH = 361;
    /** Width of the fixed right part (reward slots and button), from source x 242 to the end. */
    public static final int RIGHT_WIDTH = SOURCE_WIDTH - 242;
    public static final int MIN_WIDTH = 42 + RIGHT_WIDTH;

    public static final int BUTTON_WIDTH = 54;
    public static final int BUTTON_HEIGHT = 12;
    public static final int PROGRESS_HEIGHT = 5;
    private static final int PROGRESS_SOURCE_WIDTH = 211;

    private static final int LEFT = 42;
    private static final int SEGMENT_START = 42;
    private static final int SEGMENT = 10;

    private GoalRowSprites() {
    }

    /** Layout of one row style (normal goal row, or the taller "primary" tier row). */
    public enum Style {
        NORMAL(BACKGROUND, 32, 0, 5, 15, 24, 6, 9, 8),
        PRIMARY(PRIMARY_BACKGROUND, 38, 3, 8, 18, 27, 9, 12, 11);

        public final Identifier sprite;
        public final int height;
        /** Top of the dark body (the icon slot spans the full height). */
        public final int bodyTop;
        public final int titleY;
        public final int subtitleY;
        public final int progressY;
        public final int slotsY;
        public final int buttonY;
        public final int iconY;

        Style(Identifier sprite, int height, int bodyTop, int titleY, int subtitleY, int progressY, int slotsY,
              int buttonY, int iconY) {
            this.sprite = sprite;
            this.height = height;
            this.bodyTop = bodyTop;
            this.titleY = titleY;
            this.subtitleY = subtitleY;
            this.progressY = progressY;
            this.slotsY = slotsY;
            this.buttonY = buttonY;
            this.iconY = iconY;
        }
    }

    /** Left edge of the fixed right part (slots and button) for a row of this width. */
    public static int rightPartX(int x, int width) {
        return x + width - RIGHT_WIDTH;
    }

    public static int slotX(int x, int width, int index) {
        return rightPartX(x, width) + 4 + index * 18;
    }

    public static int buttonX(int x, int width) {
        return rightPartX(x, width) + 61;
    }

    /** Width of the progress groove (211 for a 361 px row). */
    public static int progressWidth(int width) {
        return width - 32 - RIGHT_WIDTH + 1;
    }

    /** Draws a row background at any width (at least {@link #MIN_WIDTH}). */
    public static void background(GuiGraphicsExtractor g, Style style, int x, int y, int width) {
        tiled(g, style.sprite, style.height, x, y, width, -1);
    }

    /** Darkens a normal row with the mask texture, at the same width as its background. */
    public static void mask(GuiGraphicsExtractor g, int x, int y, int width) {
        tiled(g, MASK, Style.NORMAL.height, x, y, width, ARGB.white(MASK_ALPHA));
    }

    private static void tiled(GuiGraphicsExtractor g, Identifier sprite, int h, int x, int y, int width, int color) {
        int middle = width - LEFT - RIGHT_WIDTH;
        part(g, sprite, h, 0, x, y, LEFT, SOURCE_WIDTH, color);
        for (int offset = 0; offset < middle; offset += SEGMENT) {
            part(g, sprite, h, SEGMENT_START, x + LEFT + offset, y, Math.min(SEGMENT, middle - offset), SOURCE_WIDTH, color);
        }
        part(g, sprite, h, SOURCE_WIDTH - RIGHT_WIDTH, x + width - RIGHT_WIDTH, y, RIGHT_WIDTH, SOURCE_WIDTH, color);
    }

    /** Draws the filled part of the progress groove; the empty groove is already part of the background. */
    public static void progress(GuiGraphicsExtractor g, int x, int y, int width, float fraction) {
        int filled = Math.round(width * Math.max(0F, Math.min(1F, fraction)));
        if (filled <= 0) {
            return;
        }
        // Same segment logic as the background: first segment, repeated middle segment, then the end cap.
        int body = width - 1;
        int drawn = 0;
        part(g, PROGRESS_FULL, PROGRESS_HEIGHT, 0, x, y, Math.min(SEGMENT, filled), PROGRESS_SOURCE_WIDTH);
        drawn += Math.min(SEGMENT, filled);
        while (drawn < Math.min(filled, body)) {
            int w = Math.min(SEGMENT, Math.min(filled, body) - drawn);
            part(g, PROGRESS_FULL, PROGRESS_HEIGHT, SEGMENT, x + drawn, y, w, PROGRESS_SOURCE_WIDTH);
            drawn += w;
        }
        if (filled >= width) {
            part(g, PROGRESS_FULL, PROGRESS_HEIGHT, PROGRESS_SOURCE_WIDTH - 1, x + body, y, 1, PROGRESS_SOURCE_WIDTH);
        }
    }

    public static void button(GuiGraphicsExtractor g, Identifier sprite, int x, int y) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, BUTTON_WIDTH, BUTTON_HEIGHT);
    }

    /** Draws columns [u, u + width) of a sprite at their original size. */
    private static void part(GuiGraphicsExtractor g, Identifier sprite, int height, int u, int x, int y, int width,
                             int spriteWidth) {
        part(g, sprite, height, u, x, y, width, spriteWidth, -1);
    }

    private static void part(GuiGraphicsExtractor g, Identifier sprite, int height, int u, int x, int y, int width,
                             int spriteWidth, int color) {
        if (width > 0) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, spriteWidth, height, u, 0, x, y, width, height, color);
        }
    }
}
