package com.eikins.minegoals.client;

import com.eikins.minegoals.Constants;
import com.eikins.minegoals.data.Category;
import com.eikins.minegoals.data.Goal;
import com.eikins.minegoals.data.GoalDefinitions;
import com.eikins.minegoals.data.Rewards;
import com.eikins.minegoals.progress.PlayerGoalProgress;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Goals screen: global level on top, one tab per category, a battle-pass style tier track, and the goals of
 * the selected tier below.
 */
public class GoalsScreen extends Screen {

    private static final int TAB_HEIGHT = 20;
    private static final int TRACK_HEIGHT = 30;
    private static final int ROW_HEIGHT = 32;

    private final List<Identifier> categoryIds = new ArrayList<>();
    private int selectedCategory;
    private int selectedTier = 1;
    private double scroll;

    // Layout, computed in init()
    private int left;
    private int top;
    private int panelWidth;
    private int panelBottom;

    public GoalsScreen() {
        super(Component.translatable("minegoals.screen.title"));
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(380, this.width - 20);
        this.left = (this.width - this.panelWidth) / 2;
        this.top = 10;
        this.panelBottom = this.height - 10;

        GoalDefinitions defs = ClientGoalState.definitions();
        this.categoryIds.clear();
        this.categoryIds.addAll(defs.categories().keySet());
        Identifier focus = ClientGoalState.focusedCategory();
        if (focus != null && this.categoryIds.contains(focus)) {
            this.selectCategory(this.categoryIds.indexOf(focus));
        } else {
            this.selectCategory(0);
        }
    }

    private void selectCategory(int index) {
        this.selectedCategory = index;
        this.scroll = 0;
        Identifier id = this.currentCategoryId();
        if (id != null) {
            GoalDefinitions defs = ClientGoalState.definitions();
            this.selectedTier = Math.max(1, ClientGoalState.progress().tier(defs, id));
        }
    }

    private Identifier currentCategoryId() {
        return this.selectedCategory < this.categoryIds.size() ? this.categoryIds.get(this.selectedCategory) : null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- layout helpers --------------------------------------------------------------------------

    private int tabsY() {
        return this.top + 28;
    }

    private int trackY() {
        return this.tabsY() + TAB_HEIGHT + 6;
    }

    private int listTop() {
        return this.trackY() + TRACK_HEIGHT + 14;
    }

    private int listBottom() {
        return this.panelBottom - 6;
    }

    private int tabWidth() {
        return this.categoryIds.isEmpty() ? this.panelWidth : (this.panelWidth - 8) / this.categoryIds.size();
    }

    private int tierBoxWidth(int tiers) {
        return Math.min(64, (this.panelWidth - 16 - (tiers - 1) * 6) / tiers);
    }

    private int tierBoxX(int tiers, int index) {
        int box = this.tierBoxWidth(tiers);
        int total = tiers * box + (tiers - 1) * 6;
        return this.left + (this.panelWidth - total) / 2 + index * (box + 6);
    }

    private List<Identifier> visibleGoals() {
        Identifier categoryId = this.currentCategoryId();
        if (categoryId == null) {
            return List.of();
        }
        GoalDefinitions defs = ClientGoalState.definitions();
        return defs.goalsIn(categoryId).stream()
                .filter(id -> defs.goals().get(id).tier() == this.selectedTier)
                .toList();
    }

    private int maxScroll() {
        int content = this.visibleGoals().size() * ROW_HEIGHT;
        return Math.max(0, content - (this.listBottom() - this.listTop()));
    }

    // ---- rendering -------------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        GoalDefinitions defs = ClientGoalState.definitions();
        PlayerGoalProgress progress = ClientGoalState.progress();

        g.fill(this.left, this.top, this.left + this.panelWidth, this.panelBottom, GoalRender.PANEL);
        this.renderHeader(g, defs, progress);

        Identifier categoryId = this.currentCategoryId();
        if (categoryId == null) {
            g.centeredText(this.font, Component.translatable("minegoals.screen.empty"),
                    this.width / 2, this.height / 2, GoalRender.GRAY);
            return;
        }
        Category category = defs.categories().get(categoryId);
        this.renderTabs(g, defs, mouseX, mouseY);
        this.renderTrack(g, defs, progress, categoryId, category, mouseX, mouseY);
        this.renderGoals(g, defs, progress, mouseX, mouseY);
    }

    private void renderHeader(GuiGraphicsExtractor g, GoalDefinitions defs, PlayerGoalProgress progress) {
        int total = progress.totalPoints(defs);
        g.text(this.font, this.title, this.left + 8, this.top + 6, GoalRender.GOLD, false);
        Component level = Component.translatable("minegoals.screen.level", progress.level(defs), total);
        g.text(this.font, level, this.left + this.panelWidth - 8 - this.font.width(level), this.top + 6, GoalRender.WHITE, false);
        GoalRender.bar(g, this.left + 8, this.top + 18, this.panelWidth - 16, 3,
                GoalRender.fraction(total % Constants.POINTS_PER_LEVEL, Constants.POINTS_PER_LEVEL), GoalRender.GOLD);
    }

    private void renderTabs(GuiGraphicsExtractor g, GoalDefinitions defs, int mouseX, int mouseY) {
        int tabWidth = this.tabWidth();
        int y = this.tabsY();
        for (int i = 0; i < this.categoryIds.size(); i++) {
            Category category = defs.categories().get(this.categoryIds.get(i));
            int x = this.left + 4 + i * tabWidth;
            boolean selected = i == this.selectedCategory;
            boolean hovered = inside(mouseX, mouseY, x, y, tabWidth - 2, TAB_HEIGHT);
            g.fill(x, y, x + tabWidth - 2, y + TAB_HEIGHT, selected ? GoalRender.PANEL_LIGHT : hovered ? 0x80303040 : 0x60000000);
            if (selected) {
                g.fill(x, y + TAB_HEIGHT - 2, x + tabWidth - 2, y + TAB_HEIGHT, GoalRender.GOLD);
            }
            g.item(new ItemStack(category.icon()), x + 2, y + 2);
            GoalRender.clippedText(g, this.font, category.title(), x + 20, y + 6, tabWidth - 24,
                    selected ? GoalRender.WHITE : GoalRender.GRAY);
        }
    }

    private void renderTrack(GuiGraphicsExtractor g, GoalDefinitions defs, PlayerGoalProgress progress,
                             Identifier categoryId, Category category, int mouseX, int mouseY) {
        int tiers = category.tiers().size();
        int reached = progress.tier(defs, categoryId);
        int points = progress.categoryPoints(defs, categoryId);
        int box = this.tierBoxWidth(tiers);
        int y = this.trackY();

        for (int i = 0; i < tiers; i++) {
            int tier = i + 1;
            int x = this.tierBoxX(tiers, i);
            boolean isReached = tier <= reached;
            boolean isNext = tier == reached + 1;
            if (i > 0) {
                int prevEnd = this.tierBoxX(tiers, i - 1) + box;
                g.fill(prevEnd, y + TRACK_HEIGHT / 2 - 1, x, y + TRACK_HEIGHT / 2 + 1, isReached ? GoalRender.GREEN : GoalRender.DARK_GRAY);
            }
            int background = isReached ? 0xFF1F5A1F : isNext ? 0xFF5A4A1A : 0xFF2A2A2A;
            g.fill(x, y, x + box, y + TRACK_HEIGHT, background);
            if (tier == this.selectedTier) {
                g.outline(x - 1, y - 1, box + 2, TRACK_HEIGHT + 2, GoalRender.WHITE);
            }
            Component name = Component.translatable("minegoals.screen.tier_short", tier);
            g.centeredText(this.font, name, x + box / 2, y + 5, isReached ? GoalRender.WHITE : GoalRender.GRAY);
            String threshold = category.tiers().get(i).points() + " pts";
            g.centeredText(this.font, threshold, x + box / 2, y + 17, isReached ? GoalRender.GREEN : GoalRender.GRAY);

            if (inside(mouseX, mouseY, x, y, box, TRACK_HEIGHT)) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable("minegoals.screen.tier", tier).withStyle(ChatFormatting.GOLD));
                lines.add(Component.translatable("minegoals.screen.tier_threshold", category.tiers().get(i).points())
                        .withStyle(ChatFormatting.GRAY));
                lines.addAll(describeRewards(category.tiers().get(i).rewards()));
                g.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
            }
        }

        int next = category.pointsForTier(reached + 1);
        Component summary = next < 0
                ? Component.translatable("minegoals.screen.category_max", points)
                : Component.translatable("minegoals.screen.category_progress", points, next);
        g.centeredText(this.font, summary, this.left + this.panelWidth / 2, y + TRACK_HEIGHT + 3, GoalRender.GRAY);
    }

    private void renderGoals(GuiGraphicsExtractor g, GoalDefinitions defs, PlayerGoalProgress progress, int mouseX, int mouseY) {
        int listTop = this.listTop();
        int listBottom = this.listBottom();
        int x = this.left + 6;
        int width = this.panelWidth - 12;
        List<Identifier> goals = this.visibleGoals();

        if (goals.isEmpty()) {
            g.centeredText(this.font, Component.translatable("minegoals.screen.no_goals"),
                    this.left + this.panelWidth / 2, listTop + 10, GoalRender.GRAY);
            return;
        }

        g.enableScissor(x, listTop, x + width, listBottom);
        int y = listTop - (int) this.scroll;
        Identifier hovered = null;
        for (Identifier id : goals) {
            Goal goal = defs.goals().get(id);
            boolean completed = progress.isCompleted(id);
            boolean unlocked = progress.isUnlocked(defs, id);
            int count = progress.count(id);

            g.fill(x, y, x + width, y + ROW_HEIGHT - 2, GoalRender.PANEL_LIGHT);
            g.item(new ItemStack(goal.icon()), x + 4, y + 7);

            int titleColor = completed ? GoalRender.GREEN : unlocked ? GoalRender.WHITE : GoalRender.DARK_GRAY;
            String pointsText = "+" + goal.points() + " pts";
            int pointsWidth = this.font.width(pointsText);
            GoalRender.clippedText(g, this.font, goal.title(), x + 24, y + 3, width - 32 - pointsWidth, titleColor);
            g.text(this.font, pointsText, x + width - 4 - pointsWidth, y + 3, GoalRender.GOLD, false);

            Component subtitle;
            if (completed) {
                subtitle = Component.translatable("minegoals.screen.completed");
            } else if (!unlocked) {
                subtitle = this.lockReason(defs, progress, goal);
            } else {
                subtitle = goal.description().orElse(Component.empty());
            }
            GoalRender.clippedText(g, this.font, subtitle, x + 24, y + 13, width - 28, GoalRender.GRAY);

            String counter = count + "/" + goal.count();
            int counterWidth = this.font.width(counter);
            GoalRender.bar(g, x + 24, y + 24, width - 32 - counterWidth, 3,
                    GoalRender.fraction(count, goal.count()), completed ? GoalRender.GREEN : unlocked ? GoalRender.GOLD : GoalRender.DARK_GRAY);
            g.text(this.font, counter, x + width - 4 - counterWidth, y + 22, GoalRender.GRAY, false);

            if (mouseY >= listTop && mouseY < listBottom && inside(mouseX, mouseY, x, y, width, ROW_HEIGHT - 2)) {
                hovered = id;
            }
            y += ROW_HEIGHT;
        }
        g.disableScissor();

        if (hovered != null) {
            Goal goal = defs.goals().get(hovered);
            List<Component> lines = new ArrayList<>();
            lines.add(goal.title().copy().withStyle(ChatFormatting.YELLOW));
            goal.description().ifPresent(d -> lines.add(d.copy().withStyle(ChatFormatting.GRAY)));
            lines.addAll(describeRewards(goal.rewards()));
            g.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
        }
    }

    private Component lockReason(GoalDefinitions defs, PlayerGoalProgress progress, Goal goal) {
        if (progress.tier(defs, goal.category()) < goal.tier()) {
            return Component.translatable("minegoals.screen.locked_tier", goal.tier());
        }
        List<Component> missing = new ArrayList<>();
        for (Identifier required : goal.requires()) {
            if (!progress.isCompleted(required) && defs.goals().containsKey(required)) {
                missing.add(defs.goals().get(required).title());
            }
        }
        Component list = missing.isEmpty() ? Component.empty() : missing.getFirst();
        return Component.translatable("minegoals.screen.locked_requires", list);
    }

    private static List<Component> describeRewards(Rewards rewards) {
        List<Component> lines = new ArrayList<>();
        if (rewards.isEmpty()) {
            return lines;
        }
        lines.add(Component.translatable("minegoals.screen.rewards").withStyle(ChatFormatting.GOLD));
        for (Rewards.ItemReward item : rewards.items()) {
            lines.add(Component.literal(" • " + item.count() + "× ")
                    .append(new ItemStack(item.item()).getHoverName()));
        }
        if (rewards.experience() > 0) {
            lines.add(Component.literal(" • ").append(Component.translatable("minegoals.screen.reward_xp", rewards.experience())));
        }
        if (!rewards.commands().isEmpty()) {
            lines.add(Component.literal(" • ").append(Component.translatable("minegoals.screen.reward_special")));
        }
        return lines;
    }

    // ---- input -----------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        int tabWidth = this.tabWidth();
        for (int i = 0; i < this.categoryIds.size(); i++) {
            if (inside(mx, my, this.left + 4 + i * tabWidth, this.tabsY(), tabWidth - 2, TAB_HEIGHT)) {
                this.selectCategory(i);
                return true;
            }
        }
        Identifier categoryId = this.currentCategoryId();
        if (categoryId != null) {
            int tiers = ClientGoalState.definitions().categories().get(categoryId).tiers().size();
            int box = this.tierBoxWidth(tiers);
            for (int i = 0; i < tiers; i++) {
                if (inside(mx, my, this.tierBoxX(tiers, i), this.trackY(), box, TRACK_HEIGHT)) {
                    this.selectedTier = i + 1;
                    this.scroll = 0;
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        this.scroll = Math.max(0, Math.min(this.maxScroll(), this.scroll - scrollY * ROW_HEIGHT / 2.0));
        return true;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
