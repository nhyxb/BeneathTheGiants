package com.example.examplemod.mixin;

import com.example.examplemod.survival.SurvivalRules;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.WeightedPressurePlateBlock;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WeightedPressurePlateBlock.class)
public abstract class WeightedPressurePlateBlockMixin {
    @Shadow @Final private int maxWeight;

    @WrapOperation(
            method = "getSignalStrength",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/WeightedPressurePlateBlock;getEntityCount(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/AABB;Ljava/lang/Class;)I")
    )
    private int examplemod$excludeTinyPlayersFromHeavyPlate(Level level, AABB box, Class<? extends Entity> entityClass,
                                                            Operation<Integer> original) {
        if (this.maxWeight != 150) {
            return original.call(level, box, entityClass);
        }
        return level.getEntitiesOfClass(entityClass, box,
                EntitySelector.NO_SPECTATORS.and(entity -> !entity.isIgnoringBlockTriggers()
                        && !SurvivalRules.shouldIgnoreTriggerEntity(entity))).size();
    }
}
