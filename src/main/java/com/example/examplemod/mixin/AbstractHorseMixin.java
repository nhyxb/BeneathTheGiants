package com.example.examplemod.mixin;

import com.example.examplemod.init.ModTags;
import com.example.examplemod.scale.PixelScaleHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractHorse.class)
public abstract class AbstractHorseMixin {
    @Inject(method = "getControllingPassenger", at = @At("HEAD"), cancellable = true)
    private void examplemod$disableTinyRiderControl(CallbackInfoReturnable<LivingEntity> cir) {
        AbstractHorse horse = (AbstractHorse) (Object) this;
        Entity passenger = horse.getFirstPassenger();
        if (horse.getType().is(ModTags.RIDEABLE_LIVESTOCK)
                && passenger instanceof Player player && PixelScaleHelper.isTiny(player)) {
            cir.setReturnValue(null);
        }
    }
}
