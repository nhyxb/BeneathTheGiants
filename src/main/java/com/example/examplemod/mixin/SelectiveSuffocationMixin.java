package com.example.examplemod.mixin;

import com.example.examplemod.collision.SelectiveCollisionRules;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public abstract class SelectiveSuffocationMixin {

    @WrapOperation(
            method = "lambda$isInWall$8",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;"
            )
    )
    private VoxelShape examplemod$selectiveSuffocationShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            Operation<VoxelShape> original) {
        if ((Object) this instanceof Player player && SelectiveCollisionRules.shouldPass(player, state)) {
            return Shapes.empty();
        }
        return original.call(state, level, pos);
    }
}
