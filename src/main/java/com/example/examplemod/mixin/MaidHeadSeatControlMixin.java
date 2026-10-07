package com.example.examplemod.mixin;

import com.example.examplemod.maid.MaidHeadSeat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MaidHeadSeatControlMixin {
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void examplemod$copyMaidRiderJump(CallbackInfo ci) {
        LivingEntity maid = (LivingEntity) (Object) this;
        if (maid.getControllingPassenger() instanceof Player player && MaidHeadSeat.canSitOn(player, maid)) {
            maid.setJumping(((LivingEntityAccessor) player).examplemod$isJumping());
        }
    }

    @Inject(method = "tickRidden", at = @At("HEAD"))
    private void examplemod$faceMaidWithRider(Player player, Vec3 input, CallbackInfo ci) {
        LivingEntity maid = (LivingEntity) (Object) this;
        if (!MaidHeadSeat.canSitOn(player, maid)) {
            return;
        }
        float yaw = player.getYRot();
        maid.setYRot(yaw);
        maid.setXRot(player.getXRot() * 0.5F);
        maid.yRotO = yaw;
        maid.yBodyRot = yaw;
        maid.yHeadRot = yaw;
    }

    @Inject(method = "getRiddenInput", at = @At("HEAD"), cancellable = true)
    private void examplemod$maidRiderInput(Player player, Vec3 travel, CallbackInfoReturnable<Vec3> cir) {
        if (MaidHeadSeat.canSitOn(player, (LivingEntity) (Object) this)) {
            cir.setReturnValue(MaidHeadSeat.riddenInput(player));
        }
    }

    @Inject(method = "getRiddenSpeed", at = @At("HEAD"), cancellable = true)
    private void examplemod$maidRiderSpeed(Player player, CallbackInfoReturnable<Float> cir) {
        LivingEntity maid = (LivingEntity) (Object) this;
        if (MaidHeadSeat.canSitOn(player, maid)) {
            cir.setReturnValue(MaidHeadSeat.riddenSpeed(maid));
        }
    }
}
