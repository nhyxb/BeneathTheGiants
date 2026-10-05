package com.example.examplemod.torch;

import com.example.examplemod.init.ModTags;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Pure collision shapes and heat qualification for tiny torches (F23).
 * Shapes represent pure pole geometry (2/16 width) rather than the wider 4/16 outline shape.
 * All shapes are kept strictly within the [0, 1] block boundary so BlockCollisions cursor pre-filtering never skips them.
 */
public final class TorchShapes {

    public static final VoxelShape STANDING = Block.box(7.0, 0.0, 7.0, 9.0, 10.0, 9.0);

    public static final VoxelShape WALL_EAST = Shapes.or(
            Block.box(1.0, 3.0, 7.0, 3.0, 6.0, 9.0),
            Block.box(2.0, 6.0, 7.0, 4.0, 10.0, 9.0),
            Block.box(4.0, 10.0, 7.0, 6.0, 13.0, 9.0)
    ).optimize();

    public static final VoxelShape WALL_WEST = Shapes.or(
            Block.box(13.0, 3.0, 7.0, 15.0, 6.0, 9.0),
            Block.box(12.0, 6.0, 7.0, 14.0, 10.0, 9.0),
            Block.box(10.0, 10.0, 7.0, 12.0, 13.0, 9.0)
    ).optimize();

    public static final VoxelShape WALL_SOUTH = Shapes.or(
            Block.box(7.0, 3.0, 1.0, 9.0, 6.0, 3.0),
            Block.box(7.0, 6.0, 2.0, 9.0, 10.0, 4.0),
            Block.box(7.0, 10.0, 4.0, 9.0, 13.0, 6.0)
    ).optimize();

    public static final VoxelShape WALL_NORTH = Shapes.or(
            Block.box(7.0, 3.0, 13.0, 9.0, 6.0, 15.0),
            Block.box(7.0, 6.0, 12.0, 9.0, 10.0, 14.0),
            Block.box(7.0, 10.0, 10.0, 9.0, 13.0, 12.0)
    ).optimize();

    private TorchShapes() {
    }

    /**
     * Returns the physical collision shape for a tiny torch block state.
     * Returns null if the block state is not in the #examplemod:tiny_torches tag.
     *
     * @param state the block state to test
     * @return the overridden VoxelShape, or null if not in tiny_torches tag
     */
    @Nullable
    public static VoxelShape collisionShape(@Nullable BlockState state) {
        if (state == null || !state.is(ModTags.TINY_TORCHES)) {
            return null;
        }
        if (state.hasProperty(WallTorchBlock.FACING)) {
            Direction facing = state.getValue(WallTorchBlock.FACING);
            return switch (facing) {
                case NORTH -> WALL_NORTH;
                case SOUTH -> WALL_SOUTH;
                case WEST -> WALL_WEST;
                case EAST -> WALL_EAST;
                default -> STANDING;
            };
        }
        return STANDING;
    }

    /**
     * Determines whether the given block state qualifies as hot (emitting heat/fire).
     * Returns true if the block is in #examplemod:tiny_torches and either has no LIT property
     * or its LIT property evaluates to true.
     * Unlit redstone torches return false.
     *
     * @param state the block state to test
     * @return true if hot, false otherwise
     */
    public static boolean isHot(@Nullable BlockState state) {
        if (state == null || !state.is(ModTags.TINY_TORCHES)) {
            return false;
        }
        if (state.hasProperty(BlockStateProperties.LIT)) {
            return state.getValue(BlockStateProperties.LIT);
        }
        return true;
    }
}
