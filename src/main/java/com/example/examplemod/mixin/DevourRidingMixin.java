package com.example.examplemod.mixin;

import com.example.examplemod.devour.DevourPlayerAccess;
import com.example.examplemod.devour.DevourRules;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class DevourRidingMixin {

    @Inject(
            method = "positionRider(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$MoveFunction;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void examplemod$positionDevourRider(Entity passenger, Entity.MoveFunction callback, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self instanceof Mob mob && passenger instanceof Player player) {
            if (player instanceof DevourPlayerAccess access && access.examplemod$getCaptorId() == mob.getId()) {
                float progress = access.examplemod$getDevourProgress();
                Vec3 anchor = DevourRules.anchorPosition(mob, player, progress);
                if (anchor != null) {
                    callback.accept(passenger, anchor.x, anchor.y, anchor.z);
                    ci.cancel();
                }
            }
        }
    }

    @Inject(
            method = "removeVehicle",
            at = @At("HEAD"),
            cancellable = true
    )
    private void examplemod$preventRemoveVehicleWhenDevoured(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!self.level().isClientSide() && self instanceof Player player && player instanceof DevourPlayerAccess access) {
            int captorId = access.examplemod$getCaptorId();
            if (captorId != -1) {
                Entity vehicle = self.getVehicle();
                if (vehicle != null && vehicle.getId() == captorId && !vehicle.isRemoved() && vehicle.isAlive()) {
                    ci.cancel();
                }
            }
        }
    }

    @Inject(
            method = "startRiding(Lnet/minecraft/world/entity/Entity;Z)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void examplemod$restrictStartRidingWhenDevoured(Entity vehicle, boolean force, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (!self.level().isClientSide() && self instanceof Player player && player instanceof DevourPlayerAccess access) {
            int captorId = access.examplemod$getCaptorId();
            if (captorId != -1) {
                Entity currentVehicle = self.getVehicle();
                if (currentVehicle == null && vehicle != null && vehicle.getId() == captorId) {
                    return;
                }
                cir.setReturnValue(false);
            }
        }
    }
}
