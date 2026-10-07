package com.example.examplemod.mixin;

import com.example.examplemod.maid.MaidHeadSeat;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MaidHeadSeatAiMixin {
    @Inject(method = "getControllingPassenger", at = @At("HEAD"), cancellable = true)
    private void examplemod$ownerControlsMaid(CallbackInfoReturnable<net.minecraft.world.entity.LivingEntity> cir) {
        Mob maid = (Mob) (Object) this;
        if (maid.getFirstPassenger() instanceof Player player && MaidHeadSeat.canSitOn(player, maid)) {
            cir.setReturnValue(player);
        }
    }

    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void examplemod$pauseMaidAiWhileRidden(CallbackInfo ci) {
        Mob maid = (Mob) (Object) this;
        if (maid.getFirstPassenger() instanceof Player player && MaidHeadSeat.canSitOn(player, maid)) {
            maid.getNavigation().stop();
            ci.cancel();
        }
    }
}
