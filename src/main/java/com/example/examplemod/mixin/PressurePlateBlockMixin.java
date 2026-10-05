package com.example.examplemod.mixin;

import com.example.examplemod.survival.SurvivalRules;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PressurePlateBlock.class)
public abstract class PressurePlateBlockMixin {
    @WrapOperation(
            method = "getSignalStrength",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/PressurePlateBlock;getEntityCount(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/AABB;Ljava/lang/Class;)I")
    )
    private int examplemod$excludeTinyPlayers(Level level, AABB box, Class<? extends Entity> entityClass,
                                               Operation<Integer> original) {
        return level.getEntitiesOfClass(entityClass, box,
                EntitySelector.NO_SPECTATORS.and(entity -> !entity.isIgnoringBlockTriggers()
                        && !SurvivalRules.shouldIgnoreTriggerEntity(entity))).size();
    }
}
