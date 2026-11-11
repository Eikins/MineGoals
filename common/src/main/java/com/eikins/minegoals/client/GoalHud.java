package com.eikins.minegoals.client;

import com.eikins.minegoals.Constants;
import com.eikins.minegoals.FeatureFlags;
import com.eikins.minegoals.data.Category;
import com.eikins.minegoals.data.Goal;
import com.eikins.minegoals.data.GoalDefinitions;
import com.eikins.minegoals.progress.PlayerGoalProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Top-right overlay: rewards waiting to be claimed, the focused category's tier (cycled with J),
 * and up to three active goals.
 */
public final class GoalHud {

    public static final Identifier ID = Constants.id("goals_hud");

    private static final int WIDTH = 150;
    private static final int MAX_GOALS = 3;
    private static final int GOAL_ROW = 20;

    private GoalHud() {
    }

    /** HUD entry point registered by both loaders: the journal icon, plus the goals panel when enabled. */
    public static void render(GuiGraphicsExtractor g) {
        JournalHud.render(g);
        if (FeatureFlags.GOAL_PANEL) {
            renderPanel(g);
        }
    }

    private static void renderPanel(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        GoalDefinitions defs = ClientGoalState.definitions();
        if (!ClientGoalState.isHudVisible() || mc.player == null || defs.categories().isEmpty() || mc.gui.hud.isHidden()) {
            return;
        }
        PlayerGoalProgress progress = ClientGoalState.progress();
        Identifier categoryId = ClientGoalState.focusedCategory();
        if (categoryId == null) {
            return;
        }
        Category category = defs.categories().get(categoryId);
        Font font = mc.font;

        List<Identifier> active = new ArrayList<>(defs.goalsIn(categoryId).stream()
                .filter(id -> progress.isActive(defs, id))
                .toList());
        active.sort(Comparator.comparingDouble((Identifier id) ->
                -GoalRender.fraction(progress.count(id), defs.goals().get(id).count())));
        List<Identifier> shown = active.subList(0, Math.min(MAX_GOALS, active.size()));

        int x = g.guiWidth() - WIDTH - 4;
        int y = 4;
        int height = 46 + Math.max(1, shown.size()) * GOAL_ROW;
        g.fill(x, y, x + WIDTH, y + height, GoalRender.PANEL);

        // Rewards waiting
        int claimable = progress.claimableCount(defs);
        if (claimable > 0) {
            Component claim = Component.translatable("minegoals.hud.claimable", claimable);
            g.text(font, claim, x + WIDTH - 4 - font.width(claim), y + 4, GoalRender.GREEN, false);
        }

        // Focused category and its tier progress
        int tier = progress.tier(defs, categoryId);
        int earned = progress.tierPoints(defs, categoryId, tier);
        int needed = category.pointsNeeded(tier);
        boolean last = tier == category.tiers().size() && progress.isTierComplete(defs, categoryId, tier);
        List<Identifier> visible = ClientGoalState.visibleCategories();
        int categoryCount = visible.size();
        String position = categoryCount > 1 ? (visible.indexOf(categoryId) + 1) + "/" + categoryCount : "";
        GoalRender.clippedText(g, font, category.title(), x + 4, y + 20, WIDTH - 12 - font.width(position), GoalRender.WHITE);
        g.text(font, position, x + WIDTH - 4 - font.width(position), y + 20, GoalRender.DARK_GRAY, false);
        boolean showNext = category.tierVisibility().revealsNextThreshold();
        Component tierText = last
                ? Component.translatable("minegoals.hud.tier_max", tier, earned)
                : showNext
                        ? Component.translatable("minegoals.hud.tier", tier, Math.min(earned, needed), needed)
                        : Component.translatable("minegoals.hud.tier_hidden", tier, earned);
        g.text(font, tierText, x + 4, y + 30, GoalRender.GRAY, false);
        if (last || showNext) {
            GoalRender.bar(g, x + 4, y + 40, WIDTH - 8, 2, last ? 1F : GoalRender.fraction(earned, needed), GoalRender.GREEN);
        }

        // Active goals
        int rowY = y + 46;
        if (shown.isEmpty()) {
            g.text(font, Component.translatable("minegoals.hud.none"), x + 4, rowY + 6, GoalRender.GRAY, false);
        }
        for (Identifier id : shown) {
            Goal goal = defs.goals().get(id);
            int count = progress.count(id);
            g.item(new ItemStack(goal.icon()), x + 2, rowY + 1);
            GoalRender.clippedText(g, font, goal.title(), x + 20, rowY + 2, WIDTH - 24, GoalRender.WHITE);
            String counter = count + "/" + goal.count();
            GoalRender.bar(g, x + 20, rowY + 13, WIDTH - 28 - font.width(counter), 3,
                    GoalRender.fraction(count, goal.count()), GoalRender.GREEN);
            g.text(font, counter, x + WIDTH - 4 - font.width(counter), rowY + 11, GoalRender.GRAY, false);
            rowY += GOAL_ROW;
        }
    }
}
