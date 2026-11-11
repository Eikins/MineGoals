package com.eikins.minegoals.mixin;

import com.eikins.minegoals.progress.GoalTracker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Feeds vanilla stat increments and a once-per-second tick into {@link GoalTracker}. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Inject(method = "awardStat(Lnet/minecraft/stats/Stat;I)V", at = @At("TAIL"))
    private void minegoals$onAwardStat(Stat<?> stat, int count, CallbackInfo ci) {
        GoalTracker.onStat((ServerPlayer) (Object) this, stat, count);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void minegoals$onTick(CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (self.tickCount % 20 == 0) {
            GoalTracker.tickPlayer(self);
        }
    }
}
