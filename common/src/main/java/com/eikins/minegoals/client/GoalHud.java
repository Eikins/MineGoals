package com.eikins.minegoals.client;

import com.eikins.minegoals.Constants;
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
 * Top-right overlay: global level and rewards waiting to be claimed, the focused category's tier (cycled with J),
 * and up to three active goals.
 */
public final class GoalHud {

    public static final Identifier ID = Constants.id("goals_hud");

    private static final int WIDTH = 150;
    private static final int MAX_GOALS = 3;
    private static final int GOAL_ROW = 20;

    private GoalHud() {
    }

    public static void render(GuiGraphicsExtractor g) {
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

        // Global level
        int total = progress.totalPoints(defs);
        g.text(font, Component.translatable("minegoals.hud.level", progress.level(defs)), x + 4, y + 4, GoalRender.GOLD, false);
        int claimable = progress.claimableCount(defs);
        if (claimable > 0) {
            Component claim = Component.translatable("minegoals.hud.claimable", claimable);
            g.text(font, claim, x + WIDTH - 4 - font.width(claim), y + 4, GoalRender.GREEN, false);
        }
        GoalRender.bar(g, x + 4, y + 14, WIDTH - 8, 2,
                GoalRender.fraction(total % Constants.POINTS_PER_LEVEL, Constants.POINTS_PER_LEVEL), GoalRender.GOLD);

        // Focused category and its tier progress
        int points = progress.categoryPoints(defs, categoryId);
        int tier = progress.tier(defs, categoryId);
        int next = category.pointsForTier(tier + 1);
        int current = category.pointsForTier(tier);
        int categoryCount = defs.categories().size();
        String position = categoryCount > 1
                ? (List.copyOf(defs.categories().keySet()).indexOf(categoryId) + 1) + "/" + categoryCount
                : "";
        GoalRender.clippedText(g, font, category.title(), x + 4, y + 20, WIDTH - 12 - font.width(position), GoalRender.WHITE);
        g.text(font, position, x + WIDTH - 4 - font.width(position), y + 20, GoalRender.DARK_GRAY, false);
        boolean showNext = next >= 0 && category.tierVisibility().revealsNextThreshold();
        Component tierText = next < 0
                ? Component.translatable("minegoals.hud.tier_max", tier, points)
                : showNext
                        ? Component.translatable("minegoals.hud.tier", tier, points, next)
                        : Component.translatable("minegoals.hud.tier_hidden", tier, points);
        g.text(font, tierText, x + 4, y + 30, GoalRender.GRAY, false);
        if (next < 0 || showNext) {
            GoalRender.bar(g, x + 4, y + 40, WIDTH - 8, 2,
                    next < 0 ? 1F : GoalRender.fraction(points - current, next - current), GoalRender.GREEN);
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
