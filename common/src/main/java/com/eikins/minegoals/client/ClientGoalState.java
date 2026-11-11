package com.eikins.minegoals.client;

import com.eikins.minegoals.data.Goal;
import com.eikins.minegoals.data.GoalDefinitions;
import com.eikins.minegoals.progress.PlayerGoalProgress;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/** Client copy of the goal definitions and of the local player's progress, as last sent by the server. */
public final class ClientGoalState {

    private static GoalDefinitions definitions = GoalDefinitions.EMPTY;
    private static PlayerGoalProgress progress = new PlayerGoalProgress();
    private static @Nullable Identifier focusedCategory;
    private static boolean hudVisible = true;

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
    }

    /** Stores new progress and focuses the HUD on the category that changed most recently. */
    public static void setProgress(PlayerGoalProgress newProgress) {
        for (Map.Entry<Identifier, Goal> entry : definitions.goals().entrySet()) {
            Identifier id = entry.getKey();
            if (newProgress.count(id) != progress.count(id) || newProgress.isCompleted(id) != progress.isCompleted(id)) {
                focusedCategory = entry.getValue().category();
                break;
            }
        }
        progress = newProgress;
    }

    /** Category shown in the HUD: the last one that progressed, else the first with an active goal. */
    public static @Nullable Identifier focusedCategory() {
        if (focusedCategory != null) {
            return focusedCategory;
        }
        for (Identifier category : definitions.categories().keySet()) {
            for (Identifier goal : definitions.goalsIn(category)) {
                if (progress.isActive(definitions, goal)) {
                    return category;
                }
            }
        }
        return definitions.categories().keySet().stream().findFirst().orElse(null);
    }

    public static boolean isHudVisible() {
        return hudVisible;
    }

    public static void toggleHud() {
        hudVisible = !hudVisible;
    }

    /** Clears everything when leaving a world, so another server's goals never leak in. */
    public static void reset() {
        definitions = GoalDefinitions.EMPTY;
        progress = new PlayerGoalProgress();
        focusedCategory = null;
    }
}
