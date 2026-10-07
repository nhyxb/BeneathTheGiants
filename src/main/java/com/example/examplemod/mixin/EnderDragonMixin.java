package com.example.examplemod.mixin;

import com.example.examplemod.scale.PehkuiScaleSupport;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnderDragon.class)
public abstract class EnderDragonMixin {

    @Unique
    private float examplemod$lastAppliedPartScale = Float.NaN;

    @Unique
    private void examplemod$checkAndRefreshParts() {
        if (PehkuiScaleSupport.isLoaded()) {
            return;
        }
        EnderDragon dragon = (EnderDragon) (Object) this;
        EnderDragonPart[] parts = dragon.getSubEntities();
        if (parts == null) {
            return;
        }
        float currentScale = dragon.getScale();
        if (Float.isNaN(this.examplemod$lastAppliedPartScale) || Math.abs(currentScale - this.examplemod$lastAppliedPartScale) > 1e-6F) {
            this.examplemod$lastAppliedPartScale = currentScale;
            for (EnderDragonPart part : parts) {
                if (part != null) {
                    part.refreshDimensions();
                }
            }
        }
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void examplemod$onAiStep(CallbackInfo ci) {
        this.examplemod$checkAndRefreshParts();
    }

    @Inject(method = "tickPart", at = @At("HEAD"), cancellable = true)
    private void examplemod$modifyTickPart(EnderDragonPart part, double offsetX, double offsetY, double offsetZ, CallbackInfo ci) {
        if (PehkuiScaleSupport.isLoaded()) {
            return;
        }
        this.examplemod$checkAndRefreshParts();
        EnderDragon dragon = (EnderDragon) (Object) this;
        float scale = dragon.getScale();
        part.setPos(dragon.getX() + offsetX * scale, dragon.getY() + offsetY * scale, dragon.getZ() + offsetZ * scale);
        ci.cancel();
    }
}
