package com.example.examplemod.mixin;

import com.example.examplemod.scale.PixelScaleHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void examplemod$playerInit(Level level, BlockPos pos, float yRot, GameProfile gameProfile, CallbackInfo ci) {
        ((Player) (Object) this).refreshDimensions();
    }

    @ModifyArg(
            method = "attack",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V"),
            index = 0
    )
    private double examplemod$modifyPlayerKnockback(double strength) {
        if (PixelScaleHelper.isTiny((Player) (Object) this)) {
            return strength * 0.25D;
        }
        return strength;
    }
}
