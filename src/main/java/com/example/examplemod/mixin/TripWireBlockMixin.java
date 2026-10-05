package com.example.examplemod.mixin;

import com.example.examplemod.survival.SurvivalRules;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(TripWireBlock.class)
public abstract class TripWireBlockMixin {
    @WrapOperation(
            method = "checkPressed",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;")
    )
    private List<Entity> examplemod$excludeTinyPlayers(Level level, Entity except, AABB box,
                                                        Operation<List<Entity>> original) {
        return original.call(level, except, box).stream()
                .filter(entity -> !SurvivalRules.shouldIgnoreTriggerEntity(entity))
                .toList();
    }
}
