package com.example.examplemod.mixin.client;

import com.example.examplemod.survival.SurvivalRules;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow private Entity entity;

    @ModifyConstant(method = "getMaxZoom", constant = @Constant(floatValue = 0.1F))
    private float examplemod$shrinkCameraCollisionProbeForTinyPlayer(float vanillaRadius) {
        return SurvivalRules.cameraCollisionProbeRadius(this.entity, vanillaRadius);
    }
}
