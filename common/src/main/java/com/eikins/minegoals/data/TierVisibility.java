package com.eikins.minegoals.data;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** How much of a category's tier track a player can see before reaching it. */
public enum TierVisibility implements StringRepresentable {
    /** Every tier, its goals and its rewards are shown. */
    VISIBLE("visible"),
    /** Reached tiers are shown; the next one appears as "???" without its goals or rewards; later ones are hidden. */
    NEXT("next"),
    /** Only reached tiers are shown. */
    HIDDEN("hidden");

    public static final Codec<TierVisibility> CODEC = StringRepresentable.fromEnum(TierVisibility::values);

    private final String name;

    TierVisibility(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    /** Number of tiers drawn on the track for a player who reached {@code reached} tiers. */
    public int shownTiers(int reached, int total) {
        return switch (this) {
            case VISIBLE -> total;
            case NEXT -> Math.min(total, reached + 1);
            case HIDDEN -> Math.min(total, reached);
        };
    }

    /** Whether the goals and rewards of {@code tier} can be seen by a player who reached {@code reached} tiers. */
    public boolean revealsContent(int tier, int reached) {
        return this == VISIBLE || tier <= reached;
    }

    /** Whether the points a tier needs can be shown (hidden categories only show the points earned). */
    public boolean revealsNextThreshold() {
        return this != HIDDEN;
    }
}
