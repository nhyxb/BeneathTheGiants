package com.example.examplemod.mixin;

import com.example.examplemod.init.ModAttributes;
import com.example.examplemod.init.ModDamageTypes;
import com.example.examplemod.scale.PixelScaleHelper;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Shadow protected boolean jumping;

    @Inject(method = "getScale", at = @At("RETURN"), cancellable = true)
    private void examplemod$modifyScale(CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof Player) {
            cir.setReturnValue(PixelScaleHelper.PLAYER_TINY_SCALE_FLOAT);
            return;
        }
        LivingEntity entity = (LivingEntity) (Object) this;
        if (PixelScaleHelper.hasAttribute(entity, ModAttributes.PIXEL_SCALE)) {
            double pixelScale = entity.getAttributeValue(ModAttributes.PIXEL_SCALE);
            cir.setReturnValue((float) (cir.getReturnValueF() * pixelScale));
        }
    }

    @org.spongepowered.asm.mixin.injection.ModifyVariable(
            method = "updateWalkAnimation",
            at = @At("HEAD"),
            argsOnly = true
    )
    private float examplemod$scaleWalkAnimationDistance(float distance) {
        if (distance <= 0.0F) {
            return distance;
        }
        LivingEntity entity = (LivingEntity) (Object) this;
        if (!PixelScaleHelper.isTiny(entity)) {
            return distance;
        }
        float scale = entity.getScale();
        if (Float.isFinite(scale) && scale > 0.0F && Math.abs(scale - 1.0F) > 1e-5F) {
            return distance / scale;
        }
        return distance;
    }

    @WrapOperation(
            method = "hurt",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V")
    )
    private void examplemod$wrapHurtKnockback(
            LivingEntity instance, double strength, double x, double z,
            Operation<Void> original,
            @Local(argsOnly = true) DamageSource source
    ) {
        if (source.is(ModDamageTypes.AXOLOTL_BITE)) return;
        Entity attacker = source.getEntity();
        if (attacker instanceof LivingEntity livingAttacker && PixelScaleHelper.isTiny(livingAttacker)) {
            strength *= 0.25D;
        }
        if (PixelScaleHelper.isTiny(instance)) {
            strength *= 0.25D;
        }
        original.call(instance, strength, x, z);
    }

    @Inject(method = "getDimensions", at = @At("RETURN"), cancellable = true)
    private void examplemod$modifySleepingDimensions(net.minecraft.world.entity.Pose pose, CallbackInfoReturnable<net.minecraft.world.entity.EntityDimensions> cir) {
        if (pose == net.minecraft.world.entity.Pose.SLEEPING) {
            float scale = ((LivingEntity) (Object) this).getScale();
            net.minecraft.world.entity.EntityDimensions original = cir.getReturnValue();
            cir.setReturnValue(new net.minecraft.world.entity.EntityDimensions(
                    original.width() * scale,
                    original.height() * scale,
                    original.eyeHeight() * scale,
                    original.attachments().scale(scale, scale, scale),
                    false
            ));
        }
    }

    @Unique
    private static boolean examplemod$isWallClimbable(LivingEntity entity) {
        if (!PixelScaleHelper.isTiny(entity)) {
            return false;
        }
        if (!com.example.examplemod.survival.SurvivalRules.isClimbingEnabled(entity)) {
            return false;
        }
        if (entity.isPassenger()
                || entity.isFallFlying()
                || entity.isInWater()
                || entity.isInLava()
                || (entity instanceof Player p && p.getAbilities().flying)) {
            return false;
        }
        if (entity.horizontalCollision) {
            return true;
        }
        return examplemod$isTouchingWall(entity);
    }

    @Unique
    private static boolean examplemod$isTouchingWall(LivingEntity entity) {
        if (entity.level() == null) {
            return false;
        }
        AABB bb = entity.getBoundingBox();
        double width = Math.min(bb.getXsize(), bb.getZsize());
        double d = Math.min(0.02D, Math.max(0.008D, width * 0.5D));
        double yMargin = Math.max(0.002D, bb.getYsize() * 0.1D);
        if (bb.getYsize() <= yMargin * 2.0D) {
            yMargin = bb.getYsize() * 0.05D;
        }
        AABB probe = new AABB(
                bb.minX - d, bb.minY + yMargin, bb.minZ - d,
                bb.maxX + d, bb.maxY - yMargin, bb.maxZ + d
        );
        return !entity.level().noBlockCollision(entity, probe);
    }

    @Inject(method = "onClimbable", at = @At("RETURN"), cancellable = true)
    private void examplemod$modifyOnClimbable(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            return;
        }
        LivingEntity entity = (LivingEntity) (Object) this;
        if (examplemod$isWallClimbable(entity)) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    private boolean examplemod$wasClimbing = false;

    @Inject(method = "travel", at = @At("TAIL"))
    private void examplemod$tinyAirHorizontalDrag(CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (!PixelScaleHelper.isTiny(entity) || entity.onGround() || entity.onClimbable()
                || entity.isInWater() || entity.isInLava() || entity.isPassenger()
                || entity.isFallFlying()) {
            return;
        }
        Vec3 movement = entity.getDeltaMovement();
        entity.setDeltaMovement(movement.x * com.example.examplemod.scale.TinyMotionTuning.airDrag, movement.y, movement.z * com.example.examplemod.scale.TinyMotionTuning.airDrag);
    }

    @Inject(
            method = "handleOnClimbable",
            at = @At("RETURN"),
            cancellable = true
    )
    private void examplemod$modifyClimbingDeltaBeforeMove(Vec3 deltaMovement, CallbackInfoReturnable<Vec3> cir) {
        LivingEntity entity = (LivingEntity) (Object) this;
        this.examplemod$wasClimbing = false;
        if (examplemod$isWallClimbable(entity)
                && entity.onClimbable()
                && !entity.isPassenger()
                && !entity.isFallFlying()
                && !entity.isInWater()
                && !entity.isInLava()
                && (!(entity instanceof Player p) || !p.getAbilities().flying)) {

            boolean isNormalLadder = net.neoforged.neoforge.common.CommonHooks.isLivingOnLadder(
                    entity.getInBlockState(),
                    entity.level(),
                    entity.blockPosition(),
                    entity
            ).isPresent();

                if (!isNormalLadder) {
                    this.examplemod$wasClimbing = true;
                    Vec3 current = cir.getReturnValue();
                    boolean sneaking = entity.isShiftKeyDown() || entity.isCrouching();
                    if (sneaking) {
                        cir.setReturnValue(new Vec3(current.x, 0.0D, current.z));
                        entity.resetFallDistance();
                    } else if (entity.zza > 0 || this.jumping) {
                        double climb = PixelScaleHelper.TINY_WALL_CLIMB_SPEED;
                        double y = this.jumping
                                ? Math.max(entity.getAttributeValue(Attributes.JUMP_STRENGTH), climb)
                                : climb;
                        cir.setReturnValue(new Vec3(current.x, y, current.z));
                        entity.resetFallDistance();
                    } else {
                        if (current.y > 0.0D) {
                            cir.setReturnValue(new Vec3(current.x, 0.0D, current.z));
                        }
                    }
                }
        }
    }

    @Inject(method = "handleRelativeFrictionAndCalculateMovement", at = @At("RETURN"), cancellable = true)
    private void examplemod$modifyClimbingMovement(Vec3 deltaMovement, float friction, CallbackInfoReturnable<Vec3> cir) {
        if (this.examplemod$wasClimbing) {
            this.examplemod$wasClimbing = false;
            LivingEntity entity = (LivingEntity) (Object) this;
            Vec3 current = cir.getReturnValue();
            double gravity = entity.getGravity();
            boolean sneaking = entity.isShiftKeyDown() || entity.isCrouching();
            if (sneaking) {
                cir.setReturnValue(new Vec3(current.x, gravity, current.z));
                entity.resetFallDistance();
            } else if (entity.zza > 0 || this.jumping) {
                double climb = (PixelScaleHelper.TINY_WALL_CLIMB_SPEED / 0.98D) + gravity;
                double y = this.jumping
                        ? Math.max(entity.getAttributeValue(Attributes.JUMP_STRENGTH), (PixelScaleHelper.TINY_WALL_CLIMB_SPEED / 0.98D)) + gravity
                        : climb;
                cir.setReturnValue(new Vec3(current.x, y, current.z));
                entity.resetFallDistance();
            } else {
                cir.setReturnValue(new Vec3(current.x, Math.min(0.0D, current.y), current.z));
            }
        }
    }
}
