package com.eikins.minegoals.mixin;

import com.eikins.minegoals.progress.GoalTracker;
import net.minecraft.advancements.triggers.BrewedPotionTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.alchemy.PotionContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Feeds potions taken out of a brewing stand into {@link GoalTracker} (vanilla has no statistic for them). */
@Mixin(BrewedPotionTrigger.class)
public abstract class BrewedPotionTriggerMixin {

    @Inject(method = "trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/alchemy/PotionContents;)V", at = @At("HEAD"))
    private void minegoals$onBrewed(ServerPlayer player, PotionContents contents, CallbackInfo ci) {
        GoalTracker.onBrew(player, contents);
    }
}
