package com.example.examplemod.mixin;

import com.example.examplemod.init.ModTags;
import com.example.examplemod.scale.PixelScaleHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.animal.Pig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Pig.class)
public abstract class PigMixin {
    @Inject(method = "getControllingPassenger", at = @At("HEAD"), cancellable = true)
    private void examplemod$disableTinyRiderControl(CallbackInfoReturnable<LivingEntity> cir) {
        Pig pig = (Pig) (Object) this;
        Entity passenger = pig.getFirstPassenger();
        if (pig.getType().is(ModTags.RIDEABLE_LIVESTOCK)
                && passenger instanceof Player player && PixelScaleHelper.isTiny(player)) {
            cir.setReturnValue(null);
        }
    }
}
