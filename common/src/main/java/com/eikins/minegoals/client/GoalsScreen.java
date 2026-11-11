package com.eikins.minegoals.client;

import com.eikins.minegoals.Constants;
import com.eikins.minegoals.data.Category;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Goals screen: global level on top, one tab per category, a battle-pass style tier track, and below it the
 * selected tier's reward row followed by its goals. Each row shows its rewards in three fixed slots on the right. Rewards are claimed by clicking their
 * "Claim" button; a tier's reward is earned by completing the tier, and claiming it unlocks the next tier.
 * Tiers the player has not reached may be hidden, depending on the category's {@link TierVisibility}.
 */
public class GoalsScreen extends Screen {

    private static final int TAB_HEIGHT = 20;
    private static final int TRACK_HEIGHT = 30;
    private static final int ROW_HEIGHT = 32;
    private static final int CLAIM_WIDTH = 52;
    private static final int CLAIM_HEIGHT = 12;
    private static final int MIN_TAB_WIDTH_WITH_TITLE = 60;
    private static final int REWARD_SLOTS = 3;
    private static final int SLOT_SIZE = 18;
    private static final int SLOTS_WIDTH = REWARD_SLOTS * SLOT_SIZE;

    private final List<Identifier> categoryIds = new ArrayList<>();
    /** Claim buttons drawn this frame, used to resolve clicks. */
    private final List<ClaimButton> claimButtons = new ArrayList<>();
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
            this.selectedTier = Math.max(1, ClientGoalState.progress().tier(ClientGoalState.definitions(), id));
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

    /** Rows of the list: the tier reward first (if any), then the goals of the selected tier. */
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
        Category category = defs.categories().get(categoryId);
        if (this.selectedTier <= category.tiers().size()) {
            rows.add(new Row(null));
        }
        for (Identifier goal : defs.goalsIn(categoryId)) {
            if (defs.goals().get(goal).tier() == this.selectedTier) {
                rows.add(new Row(goal));
            }
        }
        return rows;
    }

    private int maxScroll() {
        int content = this.rows().size() * ROW_HEIGHT;
        return Math.max(0, content - (this.listBottom() - this.listTop()));
    }

    // ---- rendering -------------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        this.claimButtons.clear();
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
        this.selectedTier = Math.min(this.selectedTier, this.shownTiers(defs, progress, categoryId));
        Category category = defs.categories().get(categoryId);
        this.renderTabs(g, defs, progress, mouseX, mouseY);
        this.renderTrack(g, defs, progress, categoryId, category, mouseX, mouseY);
        this.renderRows(g, defs, progress, categoryId, category, mouseX, mouseY);
    }

    private void renderHeader(GuiGraphicsExtractor g, GoalDefinitions defs, PlayerGoalProgress progress) {
        int total = progress.totalPoints(defs);
        g.text(this.font, this.title, this.left + 8, this.top + 6, GoalRender.GOLD, false);
        Component level = Component.translatable("minegoals.screen.level", progress.level(defs), total);
        g.text(this.font, level, this.left + this.panelWidth - 8 - this.font.width(level), this.top + 6, GoalRender.WHITE, false);
        int claimable = progress.claimableCount(defs);
        if (claimable > 0) {
            Component claim = Component.translatable("minegoals.screen.claimable", claimable);
            g.centeredText(this.font, claim, this.left + this.panelWidth / 2, this.top + 6, GoalRender.GREEN);
        }
        GoalRender.bar(g, this.left + 8, this.top + 18, this.panelWidth - 16, 3,
                GoalRender.fraction(total % Constants.POINTS_PER_LEVEL, Constants.POINTS_PER_LEVEL), GoalRender.GOLD);
    }

    private void renderTabs(GuiGraphicsExtractor g, GoalDefinitions defs, PlayerGoalProgress progress, int mouseX, int mouseY) {
        int tabWidth = this.tabWidth();
        int y = this.tabsY();
        for (int i = 0; i < this.categoryIds.size(); i++) {
            Identifier categoryId = this.categoryIds.get(i);
            Category category = defs.categories().get(categoryId);
            int x = this.left + 4 + i * tabWidth;
            boolean selected = i == this.selectedCategory;
            boolean hovered = inside(mouseX, mouseY, x, y, tabWidth - 2, TAB_HEIGHT);
            g.fill(x, y, x + tabWidth - 2, y + TAB_HEIGHT, selected ? GoalRender.PANEL_LIGHT : hovered ? 0x80303040 : 0x60000000);
            if (selected) {
                g.fill(x, y + TAB_HEIGHT - 2, x + tabWidth - 2, y + TAB_HEIGHT, GoalRender.GOLD);
            }
            if (tabWidth >= MIN_TAB_WIDTH_WITH_TITLE) {
                g.item(new ItemStack(category.icon()), x + 2, y + 2);
                GoalRender.clippedText(g, this.font, category.title(), x + 20, y + 6, tabWidth - 28,
                        selected ? GoalRender.WHITE : GoalRender.GRAY);
            } else {
                // Narrow tabs (many categories): icon only, title in a tooltip.
                g.item(new ItemStack(category.icon()), x + (tabWidth - 2) / 2 - 8, y + 2);
                if (hovered) {
                    g.setTooltipForNextFrame(this.font, List.of(category.title()), Optional.empty(), mouseX, mouseY);
                }
            }
            if (this.hasClaimable(defs, progress, categoryId)) {
                g.fill(x + tabWidth - 7, y + 2, x + tabWidth - 3, y + 6, GoalRender.GREEN);
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
        int box = this.tierBoxWidth(tiers);
        int y = this.trackY();

        for (int i = 0; i < tiers; i++) {
            int tier = i + 1;
            int x = this.tierBoxX(tiers, i);
            TierState state = tierState(defs, progress, categoryId, tier, unlocked);
            boolean revealed = visibility.revealsContent(tier, unlocked);
            if (i > 0) {
                int prevEnd = this.tierBoxX(tiers, i - 1) + box;
                g.fill(prevEnd, y + TRACK_HEIGHT / 2 - 1, x, y + TRACK_HEIGHT / 2 + 1,
                        tier <= unlocked ? GoalRender.GREEN : GoalRender.DARK_GRAY);
            }
            g.fill(x, y, x + box, y + TRACK_HEIGHT, state.background);
            if (tier == this.selectedTier) {
                g.outline(x - 1, y - 1, box + 2, TRACK_HEIGHT + 2, GoalRender.WHITE);
            }

            // Long tracks get narrow boxes: drop the words and keep the numbers.
            boolean wide = box >= 50;
            Component name = !revealed
                    ? Component.literal("???")
                    : wide ? Component.translatable("minegoals.screen.tier_short", tier) : Component.literal(String.valueOf(tier));
            g.centeredText(this.font, name, x + box / 2, y + (TRACK_HEIGHT - 8) / 2, tier <= unlocked ? GoalRender.WHITE : GoalRender.GRAY);
            if (state == TierState.CLAIMABLE) {
                g.fill(x + box - 5, y + 1, x + box - 1, y + 5, GoalRender.GREEN);
            }

            if (inside(mouseX, mouseY, x, y, box, TRACK_HEIGHT)) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable("minegoals.screen.tier", tier).withStyle(ChatFormatting.GOLD));
                if (revealed) {
                    lines.add(state.label(tier).copy().withStyle(ChatFormatting.DARK_GRAY));
                } else {
                    lines.add(Component.translatable("minegoals.screen.tier_hidden").withStyle(ChatFormatting.DARK_GRAY));
                }
                g.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
            }
        }

        Component line = Component.empty().append(category.title().copy().withStyle(ChatFormatting.WHITE));
        if (progress.canClaimTier(defs, categoryId, unlocked)) {
            line = line.copy().append(Component.literal(" · "))
                    .append(Component.translatable("minegoals.screen.category_claim", unlocked).withStyle(ChatFormatting.GREEN));
        }
        g.centeredText(this.font, line, this.left + this.panelWidth / 2, y + TRACK_HEIGHT + 3, GoalRender.GRAY);
    }

    /**
     * Draws {@value #REWARD_SLOTS} fixed reward slots starting at ({@code x}, {@code y}): experience first as an XP
     * bottle, then items with their count, then commands as a nether star. Rewards that do not fit are summarised by
     * a "+N" label on the left and listed in the row tooltip. Claimed rewards get a green slot and a check mark
     * instead of their count.
     *
     * @return the tooltip of the hovered slot (or "+N" label), or null
     */
    private List<Component> renderRewardSlots(GuiGraphicsExtractor g, Rewards rewards, boolean claimed, int x, int y,
                                              int mouseX, int mouseY, boolean mouseInList) {
        List<RewardIcon> icons = rewardIcons(rewards);
        List<Component> tooltip = null;
        for (int i = 0; i < REWARD_SLOTS; i++) {
            int slotX = x + i * SLOT_SIZE;
            boolean filled = i < icons.size();
            boolean done = claimed && filled;
            g.fill(slotX, y, slotX + SLOT_SIZE - 1, y + SLOT_SIZE - 1, done ? 0x8020602A : 0x60000000);
            g.outline(slotX, y, SLOT_SIZE - 1, SLOT_SIZE - 1, done ? 0xC03FD24F : 0x30FFFFFF);
            if (!filled) {
                continue;
            }
            RewardIcon icon = icons.get(i);
            g.item(icon.stack(), slotX + 1, y + 1);
            if (done) {
                // Text is drawn above items, so the check mark sits on top of the icon.
                g.text(this.font, "✔", slotX + SLOT_SIZE - 1 - this.font.width("✔"), y + SLOT_SIZE - 9, GoalRender.GREEN, true);
            } else {
                g.itemDecorations(this.font, icon.stack(), slotX + 1, y + 1, icon.countText());
            }
            if (mouseInList && inside(mouseX, mouseY, slotX, y, SLOT_SIZE - 1, SLOT_SIZE - 1)) {
                tooltip = done
                        ? List.of(icon.label(), Component.translatable("minegoals.screen.claimed").withStyle(ChatFormatting.GREEN))
                        : List.of(icon.label());
            }
        }
        if (icons.size() > REWARD_SLOTS) {
            String more = "+" + (icons.size() - REWARD_SLOTS);
            int moreX = x - 2 - this.font.width(more);
            g.text(this.font, more, moreX, y + 5, GoalRender.GOLD, false);
            if (mouseInList && inside(mouseX, mouseY, moreX, y, this.font.width(more), SLOT_SIZE)) {
                tooltip = icons.subList(REWARD_SLOTS, icons.size()).stream().map(RewardIcon::label).toList();
            }
        }
        return tooltip;
    }

    private static List<RewardIcon> rewardIcons(Rewards rewards) {
        List<RewardIcon> icons = new ArrayList<>();
        // Experience always comes first.
        if (rewards.experience() > 0) {
            icons.add(new RewardIcon(new ItemStack(Items.EXPERIENCE_BOTTLE), String.valueOf(rewards.experience()),
                    Component.translatable("minegoals.screen.reward_xp", rewards.experience())));
        }
        for (ItemStackTemplate item : rewards.items()) {
            // A count of 1 keeps the slot clean; the real count is drawn as text so it is not tied to stack sizes.
            ItemStack stack = item.withCount(1).create();
            icons.add(new RewardIcon(stack, item.count() > 1 ? String.valueOf(item.count()) : null,
                    Component.literal(item.count() + "× ").append(stack.getHoverName())));
        }
        if (!rewards.commands().isEmpty()) {
            icons.add(new RewardIcon(new ItemStack(Items.NETHER_STAR), null,
                    Component.translatable("minegoals.screen.reward_special")));
        }
        return icons;
    }

    /** One reward shown in a slot: the item drawn, the count text over it, and its tooltip line. */
    private record RewardIcon(ItemStack stack, String countText, Component label) {
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

    /** Where a tier stands for the player, with its colour on the track. */
    private enum TierState {
        DONE(0xFF1F5A1F, "minegoals.screen.tier_state.done"),
        CLAIMABLE(0xFF6A561A, "minegoals.screen.tier_state.claimable"),
        IN_PROGRESS(0xFF2C3450, "minegoals.screen.tier_state.in_progress"),
        LOCKED(0xFF2A2A2A, "minegoals.screen.tier_state.locked");

        final int background;
        private final String key;

        TierState(int background, String key) {
            this.background = background;
            this.key = key;
        }

        Component label(int tier) {
            return Component.translatable(this.key, tier);
        }
    }

    private void renderRows(GuiGraphicsExtractor g, GoalDefinitions defs, PlayerGoalProgress progress,
                            Identifier categoryId, Category category, int mouseX, int mouseY) {
        int listTop = this.listTop();
        int listBottom = this.listBottom();
        int x = this.left + 6;
        int width = this.panelWidth - 12;

        if (!this.selectedTierRevealed(defs, progress, categoryId)) {
            g.centeredText(this.font, Component.translatable("minegoals.screen.tier_hidden"),
                    this.left + this.panelWidth / 2, listTop + 10, GoalRender.GRAY);
            return;
        }
        List<Row> rows = this.rows();
        if (rows.isEmpty()) {
            g.centeredText(this.font, Component.translatable("minegoals.screen.no_goals"),
                    this.left + this.panelWidth / 2, listTop + 10, GoalRender.GRAY);
            return;
        }

        boolean mouseInList = mouseY >= listTop && mouseY < listBottom;
        g.enableScissor(x, listTop, x + width, listBottom);
        int y = listTop - (int) this.scroll;
        List<Component> tooltip = null;
        for (Row row : rows) {
            boolean hovered = mouseInList && inside(mouseX, mouseY, x, y, width, ROW_HEIGHT - 2);
            List<Component> rowTooltip = row.goal() == null
                    ? this.renderTierRewardRow(g, defs, progress, categoryId, category, x, y, width, mouseX, mouseY, mouseInList)
                    : this.renderGoalRow(g, defs, progress, row.goal(), x, y, width, mouseX, mouseY, mouseInList);
            if (hovered) {
                tooltip = rowTooltip;
            }
            y += ROW_HEIGHT;
        }
        g.disableScissor();

        if (tooltip != null && !tooltip.isEmpty()) {
            g.setTooltipForNextFrame(this.font, tooltip, Optional.empty(), mouseX, mouseY);
        }
    }

    private List<Component> renderTierRewardRow(GuiGraphicsExtractor g, GoalDefinitions defs, PlayerGoalProgress progress,
                                                Identifier categoryId, Category category, int x, int y, int width,
                                                int mouseX, int mouseY, boolean mouseInList) {
        int tier = this.selectedTier;
        Rewards rewards = category.tiers().get(tier - 1).rewards();
        int unlocked = progress.tier(defs, categoryId);
        TierState state = tierState(defs, progress, categoryId, tier, unlocked);
        boolean claimed = progress.isTierClaimed(categoryId, tier);
        boolean showClaim = !rewards.isEmpty() && (state == TierState.CLAIMABLE || claimed);

        int slotsX = x + width - 4 - SLOTS_WIDTH;
        int right = slotsX - 6;
        g.fill(x, y, x + width, y + ROW_HEIGHT - 2, 0xE0302818);
        g.item(new ItemStack(Items.CHEST), x + 4, y + 7);
        Component title = rewards.isEmpty()
                ? Component.translatable("minegoals.screen.tier", tier)
                : Component.translatable("minegoals.screen.tier_reward", tier);
        GoalRender.clippedText(g, this.font, title, x + 24, y + 3, right - (x + 24), GoalRender.GOLD);
        GoalRender.clippedText(g, this.font, state.label(tier), x + 24, y + 13,
                right - (x + 24) - 4 - (showClaim ? CLAIM_WIDTH : 0), GoalRender.GRAY);

        if (showClaim) {
            this.renderClaimArea(g, right - CLAIM_WIDTH, y + 15, state == TierState.CLAIMABLE, claimed, null,
                    MineGoalsNetwork.ClaimReward.tier(categoryId, tier), mouseX, mouseY, mouseInList);
        } else {
            TierProgress tierProgress = tierProgress(defs, progress, categoryId, category, tier, unlocked);
            int counterWidth = this.font.width(tierProgress.text());
            if (tierProgress.fraction() >= 0) {
                GoalRender.bar(g, x + 24, y + 24, right - (x + 24) - 4 - counterWidth, 3, tierProgress.fraction(),
                        state == TierState.LOCKED ? GoalRender.DARK_GRAY : GoalRender.GOLD);
            }
            g.text(this.font, tierProgress.text(), right - counterWidth, y + 22, GoalRender.GRAY, false);
        }
        List<Component> slotTooltip = this.renderRewardSlots(g, rewards, claimed, slotsX, y + 6, mouseX, mouseY, mouseInList);
        if (slotTooltip != null) {
            return slotTooltip;
        }

        List<Component> lines = new ArrayList<>();
        lines.add(title.copy().withStyle(ChatFormatting.GOLD));
        lines.addAll(describeRewards(rewards));
        lines.add(Component.translatable("minegoals.screen.tier_reward_hint").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    /**
     * Progress towards completing {@code tier}: category points against the next tier's threshold, or, for the last
     * tier, completed goals. When the category hides the next threshold, only the points are shown (no bar).
     */
    private static TierProgress tierProgress(GoalDefinitions defs, PlayerGoalProgress progress, Identifier categoryId,
                                             Category category, int tier, int unlocked) {
        int points = progress.categoryPoints(defs, categoryId);
        if (tier < category.tiers().size()) {
            if (tier + 1 > unlocked + 1 || (tier == unlocked && !category.tierVisibility().revealsNextThreshold())) {
                return new TierProgress(points + " pts", -1F);
            }
            int start = category.pointsForTier(tier);
            int end = category.pointsForTier(tier + 1);
            return new TierProgress(Math.min(points, end) + " / " + end + " pts",
                    GoalRender.fraction(Math.max(0, points - start), end - start));
        }
        int total = 0;
        int done = 0;
        for (Identifier goal : defs.goalsIn(categoryId)) {
            if (defs.goals().get(goal).tier() == tier) {
                total++;
                if (progress.isCompleted(goal)) {
                    done++;
                }
            }
        }
        return new TierProgress(Component.translatable("minegoals.screen.tier_goals", done, total).getString(),
                GoalRender.fraction(done, total));
    }

    /** Text and bar fill (negative for no bar) of a tier's progress. */
    private record TierProgress(String text, float fraction) {
    }

    private List<Component> renderGoalRow(GuiGraphicsExtractor g, GoalDefinitions defs, PlayerGoalProgress progress,
                                          Identifier id, int x, int y, int width, int mouseX, int mouseY, boolean mouseInList) {
        Goal goal = defs.goals().get(id);
        boolean completed = progress.isCompleted(id);
        boolean unlocked = progress.isUnlocked(defs, id);
        int count = progress.count(id);

        // Reward slots on the right; everything else fits between the icon and the slots.
        int slotsX = x + width - 4 - SLOTS_WIDTH;
        int right = slotsX - 6;
        g.fill(x, y, x + width, y + ROW_HEIGHT - 2, GoalRender.PANEL_LIGHT);
        g.item(new ItemStack(goal.icon()), x + 4, y + 7);

        int rightWidth = completed ? CLAIM_WIDTH : 0;
        int titleColor = completed ? GoalRender.GREEN : unlocked ? GoalRender.WHITE : GoalRender.DARK_GRAY;
        String pointsText = "+" + goal.points() + " pts";
        int pointsWidth = this.font.width(pointsText);
        GoalRender.clippedText(g, this.font, goal.title(), x + 24, y + 3, right - (x + 24) - 4 - pointsWidth, titleColor);
        g.text(this.font, pointsText, right - pointsWidth, y + 3, GoalRender.GOLD, false);

        Component subtitle;
        if (completed) {
            subtitle = Component.translatable("minegoals.screen.completed");
        } else if (!unlocked) {
            subtitle = this.lockReason(defs, progress, goal);
        } else {
            subtitle = goal.description().orElse(Component.empty());
        }
        GoalRender.clippedText(g, this.font, subtitle, x + 24, y + 13, right - (x + 24) - 4 - rightWidth, GoalRender.GRAY);

        // Every completed goal is claimed: claiming grants its rewards and its points.
        if (completed) {
            boolean claimed = progress.isGoalClaimed(id);
            this.renderClaimArea(g, right - CLAIM_WIDTH, y + 15, !claimed, claimed, null,
                    MineGoalsNetwork.ClaimReward.goal(id), mouseX, mouseY, mouseInList);
        } else {
            String counter = count + "/" + goal.count();
            int counterWidth = this.font.width(counter);
            GoalRender.bar(g, x + 24, y + 24, right - (x + 24) - 4 - counterWidth, 3,
                    GoalRender.fraction(count, goal.count()), completed ? GoalRender.GREEN : unlocked ? GoalRender.GOLD : GoalRender.DARK_GRAY);
            g.text(this.font, counter, right - counterWidth, y + 22, GoalRender.GRAY, false);
        }

        List<Component> slotTooltip = this.renderRewardSlots(g, goal.rewards(), progress.isGoalClaimed(id), slotsX, y + 6,
                mouseX, mouseY, mouseInList);
        if (slotTooltip != null) {
            return slotTooltip;
        }

        List<Component> lines = new ArrayList<>();
        lines.add(goal.title().copy().withStyle(ChatFormatting.YELLOW));
        goal.description().ifPresent(d -> lines.add(d.copy().withStyle(ChatFormatting.GRAY)));
        lines.addAll(describeRewards(goal.rewards()));
        return lines;
    }

    /** Draws a "Claim" button (registered for clicks), a "Claimed" label, or a state text. */
    private void renderClaimArea(GuiGraphicsExtractor g, int x, int y, boolean claimable, boolean claimed,
                                 Component state, MineGoalsNetwork.ClaimReward payload,
                                 int mouseX, int mouseY, boolean mouseInList) {
        if (claimable) {
            boolean hovered = mouseInList && inside(mouseX, mouseY, x, y, CLAIM_WIDTH, CLAIM_HEIGHT);
            g.fill(x, y, x + CLAIM_WIDTH, y + CLAIM_HEIGHT, hovered ? 0xFF3FA03F : 0xFF2E7D2E);
            g.outline(x, y, CLAIM_WIDTH, CLAIM_HEIGHT, GoalRender.GREEN);
            g.centeredText(this.font, Component.translatable("minegoals.screen.claim"), x + CLAIM_WIDTH / 2, y + 2, GoalRender.WHITE);
            // Only buttons fully inside the scrolled list area can be clicked.
            if (y >= this.listTop() && y + CLAIM_HEIGHT <= this.listBottom()) {
                this.claimButtons.add(new ClaimButton(x, y, payload));
            }
        } else if (claimed) {
            g.centeredText(this.font, Component.translatable("minegoals.screen.claimed"), x + CLAIM_WIDTH / 2, y + 2, GoalRender.DARK_GRAY);
        } else if (state != null) {
            Component text = state;
            g.text(this.font, text, x + CLAIM_WIDTH - this.font.width(text), y + 2, GoalRender.DARK_GRAY, false);
        }
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
            lines.add(Component.literal(" • " + item.count() + "× ")
                    .append(item.create().getHoverName()));
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
        for (ClaimButton button : this.claimButtons) {
            if (inside(mx, my, button.x(), button.y(), CLAIM_WIDTH, CLAIM_HEIGHT)) {
                ClientGoalState.claim(button.payload());
                return true;
            }
        }
        int tabWidth = this.tabWidth();
        for (int i = 0; i < this.categoryIds.size(); i++) {
            if (inside(mx, my, this.left + 4 + i * tabWidth, this.tabsY(), tabWidth - 2, TAB_HEIGHT)) {
                this.selectCategory(i);
                return true;
            }
        }
        Identifier categoryId = this.currentCategoryId();
        if (categoryId != null) {
            int tiers = this.shownTiers(ClientGoalState.definitions(), ClientGoalState.progress(), categoryId);
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

    /** A list row: a goal, or the selected tier's reward when {@code goal} is null. */
    private record Row(Identifier goal) {
    }

    private record ClaimButton(int x, int y, MineGoalsNetwork.ClaimReward payload) {
    }
}
