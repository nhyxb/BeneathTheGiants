package com.example.examplemod.mixin.client;

import com.example.examplemod.scale.PixelScaleHelper;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {

    @ModifyExpressionValue(
            method = "getFieldOfViewModifier",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/AbstractClientPlayer;getAttributeValue(Lnet/minecraft/core/Holder;)D"
            )
    )
    private double examplemod$neutralizeTinySpeedFovModifier(double speed) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        AttributeInstance speedInst = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedInst != null) {
            AttributeModifier modifier = speedInst.getModifier(PixelScaleHelper.TINY_MOVEMENT_SPEED_ID);
            if (modifier != null && modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                double factor = 1.0D + modifier.amount();
                if (Double.isFinite(factor) && factor > 0.0D) {
                    return speed / factor;
                }
            }
        }
        return speed;
    }
}
