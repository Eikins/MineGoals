package com.eikins.minegoals.data.criterion;

import com.eikins.minegoals.Constants;
import com.eikins.minegoals.data.RegistryMatcher;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.OptionalInt;

/** Have at least {@code count} matching items in the inventory at the same time. */
public record HoldItemCriterion(RegistryMatcher<Item> item, int count) implements GoalCriterion {

    public static final MapCodec<HoldItemCriterion> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryMatcher.codec(Registries.ITEM).fieldOf("item").forGetter(HoldItemCriterion::item),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(HoldItemCriterion::count)
    ).apply(i, HoldItemCriterion::new));

    @Override
    public Identifier type() {
        return Constants.id("hold_item");
    }

    @Override
    public OptionalInt poll(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && this.item.matches(stack.typeHolder())) {
                total += stack.getCount();
            }
        }
        return OptionalInt.of(total);
    }
}
