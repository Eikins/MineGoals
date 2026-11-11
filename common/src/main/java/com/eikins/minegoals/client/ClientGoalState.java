package com.eikins.minegoals.client;

import com.eikins.minegoals.data.Goal;
import com.eikins.minegoals.data.GoalDefinitions;
import com.eikins.minegoals.network.MineGoalsNetwork;
import com.eikins.minegoals.progress.PlayerGoalProgress;
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
    private static Consumer<MineGoalsNetwork.ClaimReward> claimSender = payload -> { };

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

    /**
     * Category shown in the HUD: the one picked with the cycle key, else the last one that progressed, else the
     * first with an active goal.
     */
    public static @Nullable Identifier focusedCategory() {
        if (manualCategory != null) {
            return manualCategory;
        }
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

    /** Shows the next category in the HUD (wrapping around) and stops following progress. */
    public static void cycleCategory() {
        List<Identifier> ids = List.copyOf(definitions.categories().keySet());
        if (ids.isEmpty()) {
            return;
        }
        Identifier current = focusedCategory();
        int index = current == null ? -1 : ids.indexOf(current);
        manualCategory = ids.get((index + 1) % ids.size());
    }

    public static void setClaimSender(Consumer<MineGoalsNetwork.ClaimReward> sender) {
        claimSender = sender;
    }

    public static void claim(MineGoalsNetwork.ClaimReward payload) {
        claimSender.accept(payload);
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
