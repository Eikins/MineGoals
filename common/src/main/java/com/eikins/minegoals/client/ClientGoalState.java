package com.eikins.minegoals.client;

import com.eikins.minegoals.data.Goal;
import com.eikins.minegoals.data.GoalDefinitions;
import com.eikins.minegoals.network.MineGoalsNetwork;
import com.eikins.minegoals.progress.PlayerGoalProgress;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Client copy of the goal definitions and of the local player's progress, as last sent by the server. */
public final class ClientGoalState {

    private static GoalDefinitions definitions = GoalDefinitions.EMPTY;
    private static PlayerGoalProgress progress = new PlayerGoalProgress();
    private static @Nullable Identifier focusedCategory;
    /** Category picked with the cycle key; once set, the HUD stops following progress. */
    private static @Nullable Identifier manualCategory;
    private static boolean hudVisible = true;
    /** Sends a payload to the server; set by each loader's client entry point. */
    private static Consumer<CustomPacketPayload> serverSender = payload -> { };

    private ClientGoalState() {
    }

    public static GoalDefinitions definitions() {
        return definitions;
    }

    public static PlayerGoalProgress progress() {
        return progress;
    }

    public static void setDefinitions(GoalDefinitions defs) {
        definitions = defs;
        if (focusedCategory != null && !defs.categories().containsKey(focusedCategory)) {
            focusedCategory = null;
        }
        if (manualCategory != null && !defs.categories().containsKey(manualCategory)) {
            manualCategory = null;
        }
    }

    /**
     * Stores new progress, focuses the HUD on the category that changed most recently, and reports tracked goals
     * that progressed to the tracking toast. A goal that completes is no longer tracked in {@code newProgress}, so
     * the previous state decides whether it was followed.
     */
    public static void setProgress(PlayerGoalProgress newProgress) {
        PlayerGoalProgress previous = progress;
        boolean focused = false;
        for (Map.Entry<Identifier, Goal> entry : definitions.goals().entrySet()) {
            Identifier id = entry.getKey();
            if (!focused && (newProgress.count(id) != previous.count(id) || newProgress.isCompleted(id) != previous.isCompleted(id))) {
                focusedCategory = entry.getValue().category();
                focused = true;
            }
            boolean progressed = newProgress.count(id) > previous.count(id)
                    || (newProgress.isCompleted(id) && !previous.isCompleted(id));
            if (progressed && previous.isTracked(id)) {
                TrackedGoalsToast.report(id, entry.getValue(), newProgress.count(id), newProgress.isCompleted(id));
            }
        }
        progress = newProgress;
    }

    /**
     * Starts or stops tracking a goal. The local copy changes at once so the screen reacts; the server confirms
     * with a progress sync.
     */
    public static void setTracked(Identifier goal, boolean tracked) {
        progress.setTracked(goal, tracked);
        serverSender.accept(new MineGoalsNetwork.TrackGoal(goal, tracked));
    }

    /** Categories the player can see, in display order: locked categories are hidden. */
    public static List<Identifier> visibleCategories() {
        return definitions.categories().keySet().stream()
                .filter(id -> progress.isCategoryUnlocked(definitions, id))
                .toList();
    }

    /**
     * Category shown in the HUD: the one picked with the cycle key, else the last one that progressed, else the
     * first with an active goal. Locked categories are skipped.
     */
    public static @Nullable Identifier focusedCategory() {
        List<Identifier> visible = visibleCategories();
        if (manualCategory != null && visible.contains(manualCategory)) {
            return manualCategory;
        }
        if (focusedCategory != null && visible.contains(focusedCategory)) {
            return focusedCategory;
        }
        for (Identifier category : visible) {
            for (Identifier goal : definitions.goalsIn(category)) {
                if (progress.isActive(definitions, goal)) {
                    return category;
                }
            }
        }
        return visible.stream().findFirst().orElse(null);
    }

    /** Shows the next category in the HUD (wrapping around) and stops following progress. */
    public static void cycleCategory() {
        List<Identifier> ids = visibleCategories();
        if (ids.isEmpty()) {
            return;
        }
        Identifier current = focusedCategory();
        int index = current == null ? -1 : ids.indexOf(current);
        manualCategory = ids.get((index + 1) % ids.size());
    }

    /** Sets how payloads reach the server; called by each loader's client entry point. */
    public static void setServerSender(Consumer<CustomPacketPayload> sender) {
        serverSender = sender;
    }

    public static void claim(MineGoalsNetwork.ClaimReward payload) {
        serverSender.accept(payload);
    }

    /** Asks the server to move pending reward items into the inventory. */
    public static void collectPendingItems() {
        serverSender.accept(MineGoalsNetwork.CollectPendingItems.INSTANCE);
    }

    public static boolean isHudVisible() {
        return hudVisible;
    }

    public static void toggleHud() {
        hudVisible = !hudVisible;
    }

    /**
     * Clears goal data when leaving a world, so another server's goals never leak in. The manual HUD category is
     * kept; {@link #setDefinitions} drops it if the next world does not have it.
     */
    public static void reset() {
        definitions = GoalDefinitions.EMPTY;
        progress = new PlayerGoalProgress();
        focusedCategory = null;
    }
}
