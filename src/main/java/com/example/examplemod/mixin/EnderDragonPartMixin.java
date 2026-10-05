package com.example.examplemod.mixin;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.boss.EnderDragonPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnderDragonPart.class)
public abstract class EnderDragonPartMixin {

    @Shadow public net.minecraft.world.entity.boss.enderdragon.EnderDragon parentMob;

    @Inject(method = "getDimensions", at = @At("RETURN"), cancellable = true)
    private void examplemod$modifyPartDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        net.minecraft.world.entity.boss.enderdragon.EnderDragon parent = this.parentMob;
        if (parent == null) {
            parent = ((EnderDragonPart) (Object) this).getParent();
        }
        if (parent != null) {
            float scale = parent.getScale();
            if (scale != 1.0F) {
                cir.setReturnValue(cir.getReturnValue().scale(scale));
            }
        }
    }
}
