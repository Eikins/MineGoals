package com.eikins.minegoals;

/**
 * Compile-time switches for features that are kept in the code but turned off for now.
 * Flip a flag and rebuild to bring the feature back.
 */
public final class FeatureFlags {

    /**
     * Top-right goals panel (global level, focused category, three active goals) and its key bindings:
     * H to hide it, J to cycle categories. When off, the HUD only shows the journal icon.
     */
    public static final boolean GOAL_PANEL = false;

    /** Toasts and chat messages when a goal is completed or a tier is reached. */
    public static final boolean NOTIFICATIONS = false;

    private FeatureFlags() {
    }
}
