package com.eikins.minegoals.client;

import com.eikins.minegoals.data.Category;
import com.eikins.minegoals.data.CommandReward;
import com.eikins.minegoals.data.Goal;
import com.eikins.minegoals.data.GoalDefinitions;
import com.eikins.minegoals.data.Rewards;
import com.eikins.minegoals.data.TierVisibility;
import com.eikins.minegoals.network.MineGoalsNetwork;
import com.eikins.minegoals.progress.PlayerGoalProgress;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Goals screen, styled like the vanilla advancements screen and built from vanilla GUI sprites:
 * <ul>
 *   <li>advancement tabs above the window, one per unlocked category;</li>
 *   <li>the advancement window, filled with the category's background tile;</li>
 *   <li>the tier track as advancement frames (the last tier as a challenge frame);</li>
 *   <li>MineGoals rows ({@link GoalRowSprites}): the selected tier pinned on top (taller row), then its goals in a
 *       scrolled list, claimed goals last and darkened. Each row has its icon, a progress bar, three reward slots and
 *       a claim / completed / track button.</li>
 * </ul>
 * A tier's reward is earned by completing the tier; claiming it unlocks the next tier. Tiers the player has not
 * reached may be hidden, depending on the category's {@link TierVisibility}.
 */
public class GoalsScreen extends Screen {

    private static final int TAB_WIDTH = 28;
    private static final int TAB_HEIGHT = 32;
    private static final int TAB_OVERLAP = 4;
    private static final int FRAME = 26;
    private static final int ROW_GAP = 2;
    private static final int SCROLL_STEP = 17;
    private static final int REWARD_SLOTS = 3;
    private static final int SLOT_SIZE = 18;
    private static final int SCROLLBAR_WIDTH = 6;

    private static final int TITLE_COLOR = 0xFF404040;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFC8C8C8;
    private static final int TEXT_LOCKED = 0xFF8C8C8C;
    private static final int CLAIM_HINT = 0xFF80FF20;

    private final List<Identifier> categoryIds = new ArrayList<>();
    /** Claim buttons drawn this frame, used to resolve clicks. */
    private final List<ClaimButton> claimButtons = new ArrayList<>();
    private int selectedCategory;
    private int selectedTier = 1;
    private double scroll;

    // Window layout, computed in init()
    private int windowX;
    private int windowY;
    private int windowWidth;
    private int windowHeight;

    public GoalsScreen() {
        super(Component.translatable("minegoals.screen.title"));
    }

    @Override
    protected void init() {
        this.windowWidth = Math.min(GoalRowSprites.SOURCE_WIDTH + 2 * VanillaSprites.WINDOW_BORDER + 8 + SCROLLBAR_WIDTH + 2,
                this.width - 16);
        this.windowX = (this.width - this.windowWidth) / 2;
        this.windowY = 8 + TAB_HEIGHT - TAB_OVERLAP;
        this.windowHeight = this.height - 8 - this.windowY;

        this.categoryIds.clear();
        this.categoryIds.addAll(ClientGoalState.visibleCategories());
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
            this.selectedTier = Math.max(1, ClientGoalState.progress().tier(ClientGoalState.definitions(), id));
        }
    }

    /** Keeps the tabs in sync with unlocked categories (one may unlock while the screen is open). */
    private void refreshCategories() {
        List<Identifier> visible = ClientGoalState.visibleCategories();
        if (visible.equals(this.categoryIds)) {
            return;
        }
        Identifier current = this.currentCategoryId();
        this.categoryIds.clear();
        this.categoryIds.addAll(visible);
        int index = current == null ? -1 : this.categoryIds.indexOf(current);
        if (index >= 0) {
            this.selectedCategory = index;
        } else {
            this.selectCategory(0);
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

    private int innerX() {
        return this.windowX + VanillaSprites.WINDOW_BORDER;
    }

    private int innerY() {
        return this.windowY + VanillaSprites.WINDOW_TITLE;
    }

    private int innerWidth() {
        return this.windowWidth - 2 * VanillaSprites.WINDOW_BORDER;
    }

    private int innerBottom() {
        return this.windowY + this.windowHeight - VanillaSprites.WINDOW_BORDER;
    }

    private int trackY() {
        return this.innerY() + 6;
    }

    /** The selected tier's row stays pinned above the goals list. */
    private int tierRowY() {
        return this.trackY() + FRAME + 12;
    }

    private int listTop() {
        return this.tierRowY() + GoalRowSprites.Style.PRIMARY.height + 4;
    }

    private int listBottom() {
        return this.innerBottom() - 3;
    }

    private int listX() {
        return this.innerX() + 4;
    }

    private int listWidth() {
        return Math.max(GoalRowSprites.MIN_WIDTH, this.innerWidth() - 8 - SCROLLBAR_WIDTH - 2);
    }

    /** Tabs sit 4 px apart; with many categories they get closer so they still fit above the window. */
    private int tabStep() {
        int count = Math.max(1, this.categoryIds.size());
        return Math.min(TAB_WIDTH + 4, (this.windowWidth - TAB_WIDTH) / Math.max(1, count - 1));
    }

    private int tabX(int index) {
        return this.windowX + index * this.tabStep();
    }

    private int tabY() {
        return this.windowY - TAB_HEIGHT + TAB_OVERLAP;
    }

    private int frameStep(int tiers) {
        return Math.min(FRAME + 18, (this.innerWidth() - 16) / tiers);
    }

    private int frameX(int tiers, int index) {
        int step = this.frameStep(tiers);
        int total = (tiers - 1) * step + FRAME;
        return this.innerX() + (this.innerWidth() - total) / 2 + index * step;
    }

    /** Tiers drawn on the track for the current category, according to its visibility. */
    private int shownTiers(GoalDefinitions defs, PlayerGoalProgress progress, Identifier categoryId) {
        Category category = defs.categories().get(categoryId);
        int reached = progress.tier(defs, categoryId);
        return Math.max(1, category.tierVisibility().shownTiers(reached, category.tiers().size()));
    }

    private boolean selectedTierRevealed(GoalDefinitions defs, PlayerGoalProgress progress, Identifier categoryId) {
        Category category = defs.categories().get(categoryId);
        return category.tierVisibility().revealsContent(this.selectedTier, progress.tier(defs, categoryId));
    }

    /**
     * Goals of the selected tier, in list order: ready to claim first, then in progress, then locked, and claimed
     * goals last (drawn darker).
     */
    private List<Row> rows() {
        Identifier categoryId = this.currentCategoryId();
        if (categoryId == null) {
            return List.of();
        }
        GoalDefinitions defs = ClientGoalState.definitions();
        PlayerGoalProgress progress = ClientGoalState.progress();
        if (!this.selectedTierRevealed(defs, progress, categoryId)) {
            return List.of();
        }
        List<Row> rows = new ArrayList<>();
        for (Identifier goal : defs.goalsIn(categoryId)) {
            if (defs.goals().get(goal).tier() == this.selectedTier) {
                rows.add(new Row(goal));
            }
        }
        rows.sort(java.util.Comparator.comparingInt(row -> sortRank(defs, progress, row.goal())));
        return rows;
    }

    private static int sortRank(GoalDefinitions defs, PlayerGoalProgress progress, Identifier goal) {
        if (progress.isGoalClaimed(goal)) {
            return 3;
        }
        if (progress.isCompleted(goal)) {
            return 0;
        }
        return progress.isUnlocked(defs, goal) ? 1 : 2;
    }

    private int contentHeight() {
        return this.rows().size() * (GoalRowSprites.Style.NORMAL.height + ROW_GAP);
    }

    private int maxScroll() {
        return Math.max(0, this.contentHeight() - (this.listBottom() - this.listTop()));
    }

    // ---- rendering -------------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        this.claimButtons.clear();
        this.refreshCategories();
        GoalDefinitions defs = ClientGoalState.definitions();
        PlayerGoalProgress progress = ClientGoalState.progress();
        Identifier categoryId = this.currentCategoryId();
        Category category = categoryId == null ? null : defs.categories().get(categoryId);

        // Window: background tile inside, then the frame, then the tabs on top of the frame.
        Identifier background = category == null
                ? VanillaSprites.DEFAULT_BACKGROUND
                : category.backgroundFor(this.selectedTier).orElse(VanillaSprites.DEFAULT_BACKGROUND);
        VanillaSprites.tile(g, background, this.innerX(), this.innerY(), this.innerWidth(),
                this.innerBottom() - this.innerY());
        VanillaSprites.window(g, this.windowX, this.windowY, this.windowWidth, this.windowHeight);
        this.renderTabs(g, defs, progress, mouseX, mouseY);
        this.renderTitle(g, category);

        if (category == null) {
            g.centeredText(this.font, Component.translatable("minegoals.screen.empty"),
                    this.width / 2, this.height / 2, TEXT);
            return;
        }
        this.selectedTier = Math.min(this.selectedTier, this.shownTiers(defs, progress, categoryId));
        this.renderTrack(g, defs, progress, categoryId, category, mouseX, mouseY);
        this.renderRows(g, defs, progress, categoryId, category, mouseX, mouseY);
    }

    private void renderTitle(GuiGraphicsExtractor g, Category category) {
        Component title = category == null
                ? this.title
                : Component.empty().append(this.title).append(" - ").append(category.title());
        GoalRender.clippedText(g, this.font, title, this.windowX + 8, this.windowY + 6, this.windowWidth - 16, TITLE_COLOR);
    }

    private void renderTabs(GuiGraphicsExtractor g, GoalDefinitions defs, PlayerGoalProgress progress, int mouseX, int mouseY) {
        for (int i = 0; i < this.categoryIds.size(); i++) {
            Identifier categoryId = this.categoryIds.get(i);
            Category category = defs.categories().get(categoryId);
            boolean selected = i == this.selectedCategory;
            Identifier sprite = i == 0
                    ? (selected ? VanillaSprites.TAB_LEFT_SELECTED : VanillaSprites.TAB_LEFT)
                    : (selected ? VanillaSprites.TAB_MIDDLE_SELECTED : VanillaSprites.TAB_MIDDLE);
            int x = this.tabX(i);
            int y = this.tabY();
            VanillaSprites.sprite(g, sprite, x, y, TAB_WIDTH, TAB_HEIGHT);
            g.item(new ItemStack(category.icon()), x + 6, y + 9);
            if (this.hasClaimable(defs, progress, categoryId)) {
                VanillaSprites.sprite(g, VanillaSprites.NOTIFICATION, x + TAB_WIDTH - 9, y + 3, 10, 10);
            }
            if (inside(mouseX, mouseY, x, y, TAB_WIDTH, TAB_HEIGHT - TAB_OVERLAP)) {
                g.setTooltipForNextFrame(this.font, List.of(category.title()), Optional.empty(), mouseX, mouseY);
            }
        }
    }

    private boolean hasClaimable(GoalDefinitions defs, PlayerGoalProgress progress, Identifier categoryId) {
        for (Identifier goal : defs.goalsIn(categoryId)) {
            if (progress.canClaimGoal(defs, goal)) {
                return true;
            }
        }
        int tiers = defs.categories().get(categoryId).tiers().size();
        for (int tier = 1; tier <= tiers; tier++) {
            if (progress.canClaimTier(defs, categoryId, tier)) {
                return true;
            }
        }
        return false;
    }

    private void renderTrack(GuiGraphicsExtractor g, GoalDefinitions defs, PlayerGoalProgress progress,
                             Identifier categoryId, Category category, int mouseX, int mouseY) {
        int tiers = this.shownTiers(defs, progress, categoryId);
        int unlocked = progress.tier(defs, categoryId);
        TierVisibility visibility = category.tierVisibility();
        int y = this.trackY();
        int lineY = y + FRAME / 2;

        // Connections first, like the advancement tree: a black line with a white (or grey) line inside.
        for (int i = 1; i < tiers; i++) {
            int from = this.frameX(tiers, i - 1) + FRAME;
            int to = this.frameX(tiers, i);
            g.fill(from, lineY - 1, to, lineY + 2, 0xFF000000);
            g.fill(from, lineY, to, lineY + 1, i + 1 <= unlocked ? 0xFFFFFFFF : 0xFF707070);
        }

        for (int i = 0; i < tiers; i++) {
            int tier = i + 1;
            int x = this.frameX(tiers, i);
            TierState state = tierState(defs, progress, categoryId, tier, unlocked);
            boolean revealed = visibility.revealsContent(tier, unlocked);
            boolean last = tier == category.tiers().size();
            boolean obtained = state == TierState.DONE || state == TierState.CLAIMABLE;
            Identifier frame = last
                    ? (obtained ? VanillaSprites.CHALLENGE_FRAME_OBTAINED : VanillaSprites.CHALLENGE_FRAME_UNOBTAINED)
                    : (obtained ? VanillaSprites.TASK_FRAME_OBTAINED : VanillaSprites.TASK_FRAME_UNOBTAINED);
            VanillaSprites.sprite(g, frame, x, y, FRAME, FRAME, state == TierState.LOCKED ? 0.55F : 1F);
            if (tier == this.selectedTier) {
                g.outline(x - 1, y - 1, FRAME + 2, FRAME + 2, 0xFFFFFFFF);
            }
            String label = revealed ? String.valueOf(tier) : "?";
            g.text(this.font, label, x + (FRAME - this.font.width(label)) / 2 + 1, y + 9,
                    state == TierState.LOCKED ? TEXT_LOCKED : TEXT, true);
            if (state == TierState.CLAIMABLE) {
                VanillaSprites.sprite(g, VanillaSprites.NOTIFICATION, x + FRAME - 7, y - 3, 10, 10);
            }

            if (inside(mouseX, mouseY, x, y, FRAME, FRAME)) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable("minegoals.screen.tier", tier).withStyle(ChatFormatting.GOLD));
                lines.add((revealed ? state.label(tier) : Component.translatable("minegoals.screen.tier_hidden"))
                        .copy().withStyle(ChatFormatting.GRAY));
                g.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
            }
        }

        if (progress.canClaimTier(defs, categoryId, unlocked)) {
            Component hint = Component.translatable("minegoals.screen.category_claim", unlocked);
            g.text(this.font, hint, this.innerX() + (this.innerWidth() - this.font.width(hint)) / 2, y + FRAME + 2, CLAIM_HINT, true);
        }
    }

    private void renderRows(GuiGraphicsExtractor g, GoalDefinitions defs, PlayerGoalProgress progress,
                            Identifier categoryId, Category category, int mouseX, int mouseY) {
        int listTop = this.listTop();
        int listBottom = this.listBottom();
        int x = this.listX();
        int width = this.listWidth();

        if (!this.selectedTierRevealed(defs, progress, categoryId)) {
            g.centeredText(this.font, Component.translatable("minegoals.screen.tier_hidden"),
                    this.innerX() + this.innerWidth() / 2, this.tierRowY() + 10, TEXT);
            return;
        }

        // Pinned tier row
        List<Component> tooltip = null;
        int tierY = this.tierRowY();
        List<Component> tierTooltip = this.renderTierRow(g, defs, progress, categoryId, category, x, tierY, width,
                mouseX, mouseY);
        if (inside(mouseX, mouseY, x, tierY, width, GoalRowSprites.Style.PRIMARY.height)) {
            tooltip = tierTooltip;
        }

        // Scrolled goals
        List<Row> rows = this.rows();
        if (rows.isEmpty()) {
            g.centeredText(this.font, Component.translatable("minegoals.screen.no_goals"),
                    this.innerX() + this.innerWidth() / 2, listTop + 10, TEXT);
        } else {
            boolean mouseInList = mouseY >= listTop && mouseY < listBottom;
            int rowHeight = GoalRowSprites.Style.NORMAL.height;
            List<Integer> claimedRowsY = new ArrayList<>();
            g.enableScissor(x, listTop, x + width, listBottom);
            int y = listTop - (int) this.scroll;
            for (Row row : rows) {
                boolean hovered = mouseInList && inside(mouseX, mouseY, x, y, width, rowHeight);
                List<Component> rowTooltip = this.renderGoalRow(g, defs, progress, row.goal(), x, y, width,
                        mouseX, mouseY, mouseInList);
                if (hovered) {
                    tooltip = rowTooltip;
                }
                if (progress.isGoalClaimed(row.goal())) {
                    claimedRowsY.add(y);
                }
                y += rowHeight + ROW_GAP;
            }
            if (!claimedRowsY.isEmpty()) {
                // A later stratum is drawn over the icons and text too, so the whole row darkens.
                g.nextStratum();
                for (int rowY : claimedRowsY) {
                    GoalRowSprites.mask(g, x, rowY, width);
                }
            }
            g.disableScissor();
            this.renderScrollbar(g, listTop, listBottom);
        }

        if (tooltip != null && !tooltip.isEmpty()) {
            g.setTooltipForNextFrame(this.font, tooltip, Optional.empty(), mouseX, mouseY);
        }
    }

    private void renderScrollbar(GuiGraphicsExtractor g, int top, int bottom) {
        int maxScroll = this.maxScroll();
        if (maxScroll <= 0) {
            return;
        }
        int x = this.listX() + this.listWidth() + 2;
        int height = bottom - top;
        VanillaSprites.sprite(g, VanillaSprites.SCROLLER_BACKGROUND, x, top, SCROLLBAR_WIDTH, height);
        int handle = Math.max(16, height * height / this.contentHeight());
        int handleY = top + (int) ((height - handle) * (this.scroll / maxScroll));
        VanillaSprites.sprite(g, VanillaSprites.SCROLLER, x, handleY, SCROLLBAR_WIDTH, handle);
    }

    /** First row (taller "primary" style): the selected tier, with its reward, its state and its progress. */
    private List<Component> renderTierRow(GuiGraphicsExtractor g, GoalDefinitions defs, PlayerGoalProgress progress,
                                          Identifier categoryId, Category category, int x, int y, int width,
                                          int mouseX, int mouseY) {
        GoalRowSprites.Style style = GoalRowSprites.Style.PRIMARY;
        int tier = this.selectedTier;
        Rewards rewards = category.tiers().get(tier - 1).rewards();
        int unlocked = progress.tier(defs, categoryId);
        TierState state = tierState(defs, progress, categoryId, tier, unlocked);
        boolean claimed = progress.isTierClaimed(categoryId, tier);
        TierProgress tierProgress = tierProgress(defs, progress, categoryId, category, tier, unlocked);

        GoalRowSprites.background(g, style, x, y, width);
        g.item(new ItemStack(Items.CHEST), x + 8, y + style.iconY);
        Component title = rewards.isEmpty()
                ? Component.translatable("minegoals.screen.tier", tier)
                : Component.translatable("minegoals.screen.tier_reward", tier);
        int textRight = GoalRowSprites.rightPartX(x, width) - 4;
        String counter = tierProgress.text();
        int counterWidth = this.font.width(counter);
        GoalRender.clippedText(g, this.font, title, x + 36, y + style.titleY, textRight - (x + 36), TEXT);
        GoalRender.clippedText(g, this.font, state.label(tier), x + 36, y + style.subtitleY,
                textRight - (x + 36) - 4 - counterWidth, TEXT_DIM);
        g.text(this.font, counter, textRight - counterWidth, y + style.subtitleY, TEXT_DIM, true);
        if (tierProgress.fraction() >= 0) {
            GoalRowSprites.progress(g, x + 32, y + style.progressY, GoalRowSprites.progressWidth(width), tierProgress.fraction());
        }

        Button button;
        if (state == TierState.CLAIMABLE && !rewards.isEmpty()) {
            button = Button.CLAIM;
        } else if (claimed || state == TierState.DONE) {
            button = Button.COMPLETED;
        } else {
            button = Button.TRACK;
        }
        this.renderButton(g, button, GoalRowSprites.buttonX(x, width), y + style.buttonY, true,
                MineGoalsNetwork.ClaimReward.tier(categoryId, tier), mouseX, mouseY);
        List<Component> slotTooltip = this.renderRewardSlots(g, rewards, claimed, x, width, y + style.slotsY,
                mouseX, mouseY, true);
        if (slotTooltip != null) {
            return slotTooltip;
        }

        List<Component> lines = new ArrayList<>();
        lines.add(title.copy().withStyle(ChatFormatting.GOLD));
        lines.add(Component.literal(tierProgress.text()).withStyle(ChatFormatting.GRAY));
        lines.addAll(describeRewards(rewards));
        lines.add(Component.translatable("minegoals.screen.tier_reward_hint").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    private List<Component> renderGoalRow(GuiGraphicsExtractor g, GoalDefinitions defs, PlayerGoalProgress progress,
                                          Identifier id, int x, int y, int width, int mouseX, int mouseY, boolean mouseInList) {
        GoalRowSprites.Style style = GoalRowSprites.Style.NORMAL;
        Goal goal = defs.goals().get(id);
        boolean completed = progress.isCompleted(id);
        boolean claimed = progress.isGoalClaimed(id);
        boolean unlocked = progress.isUnlocked(defs, id);
        int count = progress.count(id);

        GoalRowSprites.background(g, style, x, y, width);
        g.item(new ItemStack(goal.icon()), x + 8, y + style.iconY);

        int textRight = GoalRowSprites.rightPartX(x, width) - 4;
        String pointsText = "+" + goal.points() + " pts";
        int pointsWidth = this.font.width(pointsText);
        GoalRender.clippedText(g, this.font, goal.title(), x + 36, y + style.titleY, textRight - (x + 36) - 4 - pointsWidth,
                unlocked || completed ? TEXT : TEXT_LOCKED);
        g.text(this.font, pointsText, textRight - pointsWidth, y + style.titleY, 0xFFFFE066, true);

        Component subtitle;
        if (completed) {
            subtitle = Component.translatable("minegoals.screen.completed");
        } else if (!unlocked) {
            subtitle = this.lockReason(defs, progress, goal);
        } else {
            subtitle = goal.description().orElse(Component.empty());
        }
        String counter = count + "/" + goal.count();
        int counterWidth = this.font.width(counter);
        GoalRender.clippedText(g, this.font, subtitle, x + 36, y + style.subtitleY,
                textRight - (x + 36) - 4 - counterWidth, TEXT_DIM);
        g.text(this.font, counter, textRight - counterWidth, y + style.subtitleY, TEXT_DIM, true);
        GoalRowSprites.progress(g, x + 32, y + style.progressY, GoalRowSprites.progressWidth(width),
                completed ? 1F : GoalRender.fraction(count, goal.count()));

        // Every completed goal is claimed: claiming grants its rewards and its points.
        Button button = claimed ? Button.COMPLETED : completed ? Button.CLAIM : Button.TRACK;
        int buttonY = y + style.buttonY;
        boolean clickable = mouseInList && buttonY >= this.listTop() && buttonY + GoalRowSprites.BUTTON_HEIGHT <= this.listBottom();
        this.renderButton(g, button, GoalRowSprites.buttonX(x, width), buttonY, clickable,
                MineGoalsNetwork.ClaimReward.goal(id), mouseX, mouseY);
        List<Component> slotTooltip = this.renderRewardSlots(g, goal.rewards(), claimed, x, width, y + style.slotsY,
                mouseX, mouseY, mouseInList);
        if (slotTooltip != null) {
            return slotTooltip;
        }

        List<Component> lines = new ArrayList<>();
        lines.add(goal.title().copy().withStyle(ChatFormatting.YELLOW));
        goal.description().ifPresent(d -> lines.add(d.copy().withStyle(ChatFormatting.GRAY)));
        lines.add(Component.literal(count + " / " + goal.count()).withStyle(ChatFormatting.GRAY));
        lines.addAll(describeRewards(goal.rewards()));
        return lines;
    }

    /** The row button: claim (clickable), completed, or "Track" (placeholder for goal tracking, not clickable yet). */
    private enum Button {
        CLAIM(GoalRowSprites.BUTTON_CLAIM),
        COMPLETED(GoalRowSprites.BUTTON_COMPLETED),
        TRACK(GoalRowSprites.BUTTON_TRACK);

        final Identifier sprite;

        Button(Identifier sprite) {
            this.sprite = sprite;
        }
    }

    /** Draws a row button; a claim button is registered for clicks when {@code clickable} (visible, under the mouse area). */
    private void renderButton(GuiGraphicsExtractor g, Button button, int x, int y, boolean clickable,
                              MineGoalsNetwork.ClaimReward payload, int mouseX, int mouseY) {
        GoalRowSprites.button(g, button.sprite, x, y);
        Component label = switch (button) {
            case CLAIM -> Component.translatable("minegoals.screen.claim");
            case COMPLETED -> Component.translatable("minegoals.screen.claimed");
            case TRACK -> Component.translatable("minegoals.screen.track");
        };
        int labelWidth = this.font.width(label);
        int textX = x + (GoalRowSprites.BUTTON_WIDTH - labelWidth) / 2 + 1;
        g.text(this.font, label, textX, y + 2, button == Button.COMPLETED ? TEXT_DIM : TEXT, true);
        if (button == Button.CLAIM) {
            if (clickable) {
                if (inside(mouseX, mouseY, x, y, GoalRowSprites.BUTTON_WIDTH, GoalRowSprites.BUTTON_HEIGHT)) {
                    g.fill(x + 1, y + 1, x + GoalRowSprites.BUTTON_WIDTH - 1, y + GoalRowSprites.BUTTON_HEIGHT - 1, 0x30FFFFFF);
                }
                this.claimButtons.add(new ClaimButton(x, y, payload));
            }
        }
    }

    /**
     * Fills the {@value #REWARD_SLOTS} reward slots of a row (they are part of the row texture): experience first as an XP
     * bottle, then items with their count, then commands with their own icon. Rewards that do not fit are summarised
     * by a "+N" label on the left and listed in the row tooltip. Claimed rewards get a check mark instead of their
     * count.
     *
     * @return the tooltip of the hovered slot (or "+N" label), or null
     */
    private List<Component> renderRewardSlots(GuiGraphicsExtractor g, Rewards rewards, boolean claimed, int rowX,
                                              int rowWidth, int y, int mouseX, int mouseY, boolean mouseInList) {
        List<RewardIcon> icons = rewardIcons(rewards);
        List<Component> tooltip = null;
        int x = GoalRowSprites.slotX(rowX, rowWidth, 0);
        for (int i = 0; i < REWARD_SLOTS; i++) {
            int slotX = GoalRowSprites.slotX(rowX, rowWidth, i);
            boolean filled = i < icons.size();
            boolean done = claimed && filled;
            if (!filled) {
                continue;
            }
            RewardIcon icon = icons.get(i);
            if (icon.texture() != null) {
                g.blit(icon.texture(), slotX + 1, y + 1, slotX + 17, y + 17, 0F, 1F, 0F, 1F);
            } else {
                g.item(icon.stack(), slotX + 1, y + 1);
            }
            if (done) {
                VanillaSprites.sprite(g, VanillaSprites.CHECKMARK, slotX + SLOT_SIZE - 10, y + SLOT_SIZE - 9, 9, 8);
            } else if (icon.stack() != null && icon.countText() != null) {
                g.itemDecorations(this.font, icon.stack(), slotX + 1, y + 1, icon.countText());
            }
            if (mouseInList && inside(mouseX, mouseY, slotX, y, SLOT_SIZE, SLOT_SIZE)) {
                List<Component> lines = new ArrayList<>(icon.lines());
                if (done) {
                    lines.add(Component.translatable("minegoals.screen.claimed").withStyle(ChatFormatting.GREEN));
                }
                tooltip = lines;
            }
        }
        if (icons.size() > REWARD_SLOTS) {
            String more = "+" + (icons.size() - REWARD_SLOTS);
            int moreX = x - 2 - this.font.width(more);
            g.text(this.font, more, moreX, y + 5, 0xFFFFE066, true);
            if (mouseInList && inside(mouseX, mouseY, moreX, y, this.font.width(more), SLOT_SIZE)) {
                tooltip = icons.subList(REWARD_SLOTS, icons.size()).stream().map(icon -> icon.lines().getFirst()).toList();
            }
        }
        return tooltip;
    }

    private static List<RewardIcon> rewardIcons(Rewards rewards) {
        List<RewardIcon> icons = new ArrayList<>();
        // Experience always comes first.
        if (rewards.experience() > 0) {
            icons.add(RewardIcon.item(new ItemStack(Items.EXPERIENCE_BOTTLE), String.valueOf(rewards.experience()),
                    Component.translatable("minegoals.screen.reward_xp", rewards.experience())));
        }
        for (ItemStackTemplate item : rewards.items()) {
            // A count of 1 keeps the slot clean; the real count is drawn as text so it is not tied to stack sizes.
            ItemStack stack = item.withCount(1).create();
            icons.add(RewardIcon.item(stack, item.count() > 1 ? String.valueOf(item.count()) : null,
                    Component.literal(item.count() + "× ").append(stack.getHoverName())));
        }
        for (CommandReward command : rewards.commands()) {
            List<Component> lines = new ArrayList<>();
            lines.add(command.name().orElseGet(() -> Component.translatable("minegoals.screen.reward_special")));
            command.description().ifPresent(d -> lines.add(d.copy().withStyle(ChatFormatting.GRAY)));
            ItemStack stack = null;
            Identifier texture = null;
            if (command.icon().orElse(null) instanceof CommandReward.TextureIcon textureIcon) {
                texture = textureIcon.texture();
            } else if (command.icon().orElse(null) instanceof CommandReward.ItemIcon itemIcon) {
                stack = itemIcon.item().withCount(1).create();
            } else {
                stack = new ItemStack(Items.NETHER_STAR);
            }
            icons.add(new RewardIcon(stack, texture, null, List.copyOf(lines)));
        }
        return icons;
    }

    /**
     * One reward shown in a slot: an item or a texture, the count text over it, and its tooltip lines (the first
     * line names the reward).
     */
    private record RewardIcon(ItemStack stack, Identifier texture, String countText, List<Component> lines) {
        static RewardIcon item(ItemStack stack, String countText, Component label) {
            return new RewardIcon(stack, null, countText, List.of(label));
        }
    }

    private static TierState tierState(GoalDefinitions defs, PlayerGoalProgress progress, Identifier categoryId,
                                       int tier, int unlocked) {
        if (tier > unlocked) {
            return TierState.LOCKED;
        }
        if (progress.canClaimTier(defs, categoryId, tier)) {
            return TierState.CLAIMABLE;
        }
        return progress.isTierComplete(defs, categoryId, tier) ? TierState.DONE : TierState.IN_PROGRESS;
    }

    /** Where a tier stands for the player. */
    private enum TierState {
        DONE("minegoals.screen.tier_state.done"),
        CLAIMABLE("minegoals.screen.tier_state.claimable"),
        IN_PROGRESS("minegoals.screen.tier_state.in_progress"),
        LOCKED("minegoals.screen.tier_state.locked");

        private final String key;

        TierState(String key) {
            this.key = key;
        }

        Component label(int tier) {
            return Component.translatable(this.key, tier);
        }
    }

    /**
     * Progress towards completing {@code tier}: points earned from the tier's own goals against the points it needs.
     * When the category hides tier requirements, only the points are shown (no bar).
     */
    private static TierProgress tierProgress(GoalDefinitions defs, PlayerGoalProgress progress, Identifier categoryId,
                                             Category category, int tier, int unlocked) {
        int earned = progress.tierPoints(defs, categoryId, tier);
        int needed = category.pointsNeeded(tier);
        if (tier > unlocked || (tier == unlocked && !category.tierVisibility().revealsNextThreshold())) {
            return new TierProgress(earned + " pts", -1F);
        }
        return new TierProgress(Math.min(earned, needed) + " / " + needed + " pts", GoalRender.fraction(earned, needed));
    }

    /** Text and bar fill (negative for no bar) of a tier's progress. */
    private record TierProgress(String text, float fraction) {
    }

    private Component lockReason(GoalDefinitions defs, PlayerGoalProgress progress, Goal goal) {
        int unlocked = progress.tier(defs, goal.category());
        if (unlocked < goal.tier()) {
            if (goal.tier() == unlocked + 1 && progress.canClaimTier(defs, goal.category(), unlocked)) {
                return Component.translatable("minegoals.screen.locked_claim", unlocked);
            }
            return goal.trackBeforeUnlock()
                    ? Component.translatable("minegoals.screen.locked_tier_tracked", goal.tier())
                    : Component.translatable("minegoals.screen.locked_tier", goal.tier());
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
        if (rewards.experience() > 0) {
            lines.add(Component.literal(" • ").append(Component.translatable("minegoals.screen.reward_xp", rewards.experience())));
        }
        for (ItemStackTemplate item : rewards.items()) {
            lines.add(Component.literal(" • " + item.count() + "× ").append(item.create().getHoverName()));
        }
        for (CommandReward command : rewards.commands()) {
            lines.add(Component.literal(" • ").append(
                    command.name().orElseGet(() -> Component.translatable("minegoals.screen.reward_special"))));
        }
        return lines;
    }

    // ---- input -----------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        for (ClaimButton button : this.claimButtons) {
            if (inside(mx, my, button.x(), button.y(), GoalRowSprites.BUTTON_WIDTH, GoalRowSprites.BUTTON_HEIGHT)) {
                ClientGoalState.claim(button.payload());
                this.playClaimSound(button.payload());
                return true;
            }
        }
        for (int i = 0; i < this.categoryIds.size(); i++) {
            if (inside(mx, my, this.tabX(i), this.tabY(), TAB_WIDTH, TAB_HEIGHT - TAB_OVERLAP)) {
                this.selectCategory(i);
                return true;
            }
        }
        Identifier categoryId = this.currentCategoryId();
        if (categoryId != null) {
            int tiers = this.shownTiers(ClientGoalState.definitions(), ClientGoalState.progress(), categoryId);
            for (int i = 0; i < tiers; i++) {
                if (inside(mx, my, this.frameX(tiers, i), this.trackY(), FRAME, FRAME)) {
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
        this.scroll = Math.max(0, Math.min(this.maxScroll(), this.scroll - scrollY * SCROLL_STEP));
        return true;
    }

    /** UI sound on the client: a light pickup for a goal, the level-up sound for a tier reward. */
    private void playClaimSound(MineGoalsNetwork.ClaimReward payload) {
        boolean tier = payload.tier() > 0;
        this.minecraft.getSoundManager().play(tier
                ? SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0F, 0.9F)
                : SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.1F, 0.8F));
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** A goal row of the scrolled list. */
    private record Row(Identifier goal) {
    }

    private record ClaimButton(int x, int y, MineGoalsNetwork.ClaimReward payload) {
    }
}
