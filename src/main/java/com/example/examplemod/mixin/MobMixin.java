package com.example.examplemod.mixin;

import com.example.examplemod.scale.PixelScaleHelper;
import com.example.examplemod.init.ModDamageTypes;
import com.example.examplemod.survival.SurvivalRules;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Mob.class)
public abstract class MobMixin {

    @WrapOperation(
            method = "doHurtTarget",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z")
    )
    private boolean examplemod$useDevouredDamageForTinyPlayers(Entity target, DamageSource source, float amount,
                                                                Operation<Boolean> original) {
        Mob attacker = (Mob) (Object) this;
        if (SurvivalRules.shouldUseDevouredDamage(attacker, target)) {
            source = ModDamageTypes.getDevouredSource(attacker.level(), attacker);
        }
        return original.call(target, source, amount);
    }

    @ModifyArg(
            method = "doHurtTarget",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V"),
            index = 0
    )
    private double examplemod$modifyMobKnockback(double strength) {
        if (PixelScaleHelper.isTiny((Mob) (Object) this)) {
            return strength * 0.25D;
        }
        return strength;
    }
}
