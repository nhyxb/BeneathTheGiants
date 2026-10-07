package com.example.examplemod.mixin.client;

import com.example.examplemod.survival.SurvivalRules;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityWalkParticleMixin {
    @Inject(method = "canSpawnSprintParticle", at = @At("HEAD"), cancellable = true)
    private void examplemod$hideTinySelfWalkParticles(CallbackInfoReturnable<Boolean> cir) {
        Player viewer = Minecraft.getInstance().player;
        if (viewer != null && SurvivalRules.shouldHideOwnWalkParticles((Entity) (Object) this, viewer)) {
            cir.setReturnValue(false);
        }
    }
}
