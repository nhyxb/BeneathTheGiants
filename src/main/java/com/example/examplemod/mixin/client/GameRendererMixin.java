package com.example.examplemod.mixin.client;

import com.example.examplemod.client.TinyCameraHelper;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @ModifyConstant(
        method = "getProjectionMatrix(D)Lorg/joml/Matrix4f;",
        constant = @Constant(floatValue = 0.05F)
    )
    private float examplemod$shrinkNearPlaneForTinyPlayer(float vanillaNear) {
        return TinyCameraHelper.effectiveNearPlane(vanillaNear);
    }
}
