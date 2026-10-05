package com.example.examplemod.mixin;

import com.example.examplemod.survival.SurvivalRules;
import com.example.examplemod.hiding.HidingRules;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "setShiftKeyDown", at = @At("HEAD"))
    private void examplemod$resetHidingWhenSneakIsReleased(boolean shiftKeyDown, CallbackInfo ci) {
        if (!shiftKeyDown && (Object) this instanceof Player player && !player.level().isClientSide()) {
            HidingRules.clear(player);
        }
    }

    @WrapOperation(
            method = "vibrationAndSoundEffectsFromBlock",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;gameEvent(Lnet/minecraft/core/Holder;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)V")
    )
    private void examplemod$suppressTinyPlayerFootsteps(Level level, Holder<GameEvent> event, Vec3 pos,
                                                        GameEvent.Context context, Operation<Void> original) {
        if (event == GameEvent.STEP && SurvivalRules.shouldSuppressStepEvent((Entity) (Object) this)) {
            return;
        }
        original.call(level, event, pos, context);
    }
}
