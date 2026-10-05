package com.example.examplemod.collision;

import com.example.examplemod.init.ModTags;
import com.example.examplemod.scale.PixelScaleHelper;
import com.example.examplemod.torch.TorchShapes;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public final class SelectiveCollisionRules {

    private SelectiveCollisionRules() {
    }

    /**
     * Determines whether an entity can selectively pass through a block.
     * Only tiny players can pass through leaves, fences, or fence gates.
     * Excludes walls and non-player entities.
     *
     * @param entity the entity checking collision
     * @param state  the block state being collided with
     * @return true if the entity should pass through without collision
     */
    public static boolean shouldPass(Entity entity, BlockState state) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        if (state == null) {
            return false;
        }
        if (!PixelScaleHelper.isTiny(player)) {
            return false;
        }
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.FENCES) || state.is(BlockTags.FENCE_GATES);
    }

    /**
     * Provides an overridden collision shape for the entity and block state.
     * Returns Shapes.empty() to allow tiny players to pass through allowed blocks (leaves, fences, fence gates),
     * returns custom torch collision shapes for tiny players encountering blocks in ModTags.TINY_TORCHES,
     * or null to retain the default collision shape.
     *
     * @param entity the entity checking collision
     * @param state  the block state being collided with
     * @return overridden VoxelShape, or null for default shape
     */
    @Nullable
    public static VoxelShape overrideShape(Entity entity, BlockState state) {
        if (shouldPass(entity, state)) {
            return Shapes.empty();
        }
        if (entity instanceof Player player && PixelScaleHelper.isTiny(player)) {
            if (state != null && state.is(ModTags.TINY_TORCHES)) {
                return TorchShapes.collisionShape(state);
            }
        }
        return null;
    }
}
