package com.eikins.minegoals.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

import java.util.List;
import java.util.function.Predicate;

/**
 * When a category becomes available to a player. A category is unlocked by default unless {@code default} is false;
 * a locked category is hidden and unlocks when its advancement conditions are met ({@code all} or {@code any} of them)
 * or through {@code /minegoals unlock}. Once unlocked, it stays unlocked (until {@code /minegoals lock}).
 * <pre>
 * "unlock": {"default": false, "mode": "any", "advancements": ["minecraft:story/enter_the_nether"]}
 * </pre>
 */
public record CategoryUnlock(boolean byDefault, Mode mode, List<Identifier> advancements) {

    public static final CategoryUnlock ALWAYS = new CategoryUnlock(true, Mode.ALL, List.of());

    public static final Codec<CategoryUnlock> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("default", true).forGetter(CategoryUnlock::byDefault),
            Mode.CODEC.optionalFieldOf("mode", Mode.ALL).forGetter(CategoryUnlock::mode),
            Identifier.CODEC.listOf().optionalFieldOf("advancements", List.of()).forGetter(CategoryUnlock::advancements)
    ).apply(i, CategoryUnlock::new));

    /** Whether the conditions are met, given a test for "the player has this advancement". No conditions: never. */
    public boolean conditionsMet(Predicate<Identifier> hasAdvancement) {
        if (this.advancements.isEmpty()) {
            return false;
        }
        return this.mode == Mode.ALL
                ? this.advancements.stream().allMatch(hasAdvancement)
                : this.advancements.stream().anyMatch(hasAdvancement);
    }

    public enum Mode implements StringRepresentable {
        ALL("all"),
        ANY("any");

        public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
