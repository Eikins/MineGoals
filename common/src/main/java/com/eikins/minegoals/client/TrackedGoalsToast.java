package com.eikins.minegoals.client;

import com.eikins.minegoals.data.Goal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Progress toast for tracked goals. There is only ever one: progress reported while it is on screen updates it
 * instead of queueing another toast, so mining a stack of blocks or progressing several goals at once never floods the
 * toast area.
 * <ul>
 *   <li>The toast shows one goal at a time: the one that progressed last.</li>
 *   <li>When several tracked goals progressed while it is open, it cycles through them and shows its position
 *       ("2/3") in the corner.</li>
 *   <li>Each update restarts its display time; it hides once nothing progressed for a while.</li>
 * </ul>
 */
public class TrackedGoalsToast implements Toast {

    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("toast/advancement");
    private static final Object TOKEN = new Object();
    private static final long DISPLAY_MS = 4000L;
    private static final long CYCLE_MS = 2000L;

    /** Goals shown by this toast, in the order they first progressed. */
    private final List<Entry> entries = new ArrayList<>();
    private int shown;
    private boolean changed = true;
    private long lastChangedMs;
    private long shownSinceMs;
    private Visibility visibility = Visibility.HIDE;

    private TrackedGoalsToast() {
    }

    /** Reports progress of a tracked goal: updates the open toast if there is one, otherwise opens a new one. */
    public static void report(Identifier id, Goal goal, int count, boolean completed) {
        ToastManager manager = Minecraft.getInstance().gui.toastManager();
        TrackedGoalsToast toast = manager.getToast(TrackedGoalsToast.class, TOKEN);
        if (toast == null) {
            toast = new TrackedGoalsToast();
            toast.put(new Entry(id, goal, count, completed));
            manager.addToast(toast);
        } else {
            toast.put(new Entry(id, goal, count, completed));
        }
    }

    /** Adds or replaces the goal's entry and shows it right away. */
    private void put(Entry entry) {
        int index = -1;
        for (int i = 0; i < this.entries.size(); i++) {
            if (this.entries.get(i).id().equals(entry.id())) {
                index = i;
                break;
            }
        }
        if (index >= 0) {
            this.entries.set(index, entry);
        } else {
            this.entries.add(entry);
            index = this.entries.size() - 1;
        }
        this.shown = index;
        this.changed = true;
    }

    @Override
    public Object getToken() {
        return TOKEN;
    }

    @Override
    public Visibility getWantedVisibility() {
        return this.visibility;
    }

    @Override
    public void update(ToastManager manager, long fullyVisibleForMs) {
        if (this.changed) {
            this.lastChangedMs = fullyVisibleForMs;
            this.shownSinceMs = fullyVisibleForMs;
            this.changed = false;
        }
        if (this.entries.size() > 1 && fullyVisibleForMs - this.shownSinceMs >= CYCLE_MS) {
            this.shown = (this.shown + 1) % this.entries.size();
            this.shownSinceMs = fullyVisibleForMs;
        }
        // Stay long enough for one full cycle through the merged goals.
        long display = Math.max(DISPLAY_MS, this.entries.size() * CYCLE_MS);
        double displayTime = display * manager.getNotificationDisplayTimeMultiplier();
        this.visibility = fullyVisibleForMs - this.lastChangedMs < displayTime ? Visibility.SHOW : Visibility.HIDE;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long fullyVisibleForMs) {
        int width = this.width();
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, 0, 0, width, this.height());
        if (this.entries.isEmpty()) {
            return;
        }
        Entry entry = this.entries.get(Math.min(this.shown, this.entries.size() - 1));
        Goal goal = entry.goal();
        graphics.item(new ItemStack(goal.icon()), 8, 8);

        int titleRight = width - 6;
        if (this.entries.size() > 1) {
            String position = (this.shown + 1) + "/" + this.entries.size();
            int positionWidth = font.width(position);
            graphics.text(font, position, width - 6 - positionWidth, 7, GoalRender.GRAY, false);
            titleRight -= positionWidth + 4;
        }
        GoalRender.clippedText(graphics, font, goal.title(), 30, 7, titleRight - 30, GoalRender.GOLD);

        if (entry.completed()) {
            GoalRender.clippedText(graphics, font, Component.translatable("minegoals.toast.tracked_completed"),
                    30, 18, width - 36, GoalRender.GREEN);
            return;
        }
        String counter = entry.count() + "/" + goal.count();
        int counterWidth = font.width(counter);
        graphics.text(font, counter, width - 6 - counterWidth, 18, GoalRender.WHITE, false);
        GoalRender.bar(graphics, 30, 21, width - 6 - counterWidth - 4 - 30, 3,
                GoalRender.fraction(entry.count(), goal.count()), GoalRender.GREEN);
    }

    /** Latest known progress of one tracked goal. */
    private record Entry(Identifier id, Goal goal, int count, boolean completed) {
    }
}
