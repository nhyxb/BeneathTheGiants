package com.example.examplemod.torch;

import com.example.examplemod.config.SurvivalConfig;
import com.example.examplemod.devour.DevourRules;
import com.example.examplemod.scale.PixelScaleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/** Native burning, with contact geometry matching the player-only collision override. */
public final class TorchContactRules {
    private static final double CONTACT_EPSILON = 0.002;

    public static boolean tick(Player player) {
        if (player.level().isClientSide() || !player.isAlive() || player.isRemoved()
                || !PixelScaleHelper.isTiny(player) || player.isCreative() || player.isSpectator()
                || player.fireImmune() || player.isInWaterRainOrBubble() || DevourRules.isCaptured(player)
                || !standingOnHotTorch(player)) return false;
        int before = player.getRemainingFireTicks();
        player.igniteForTicks(SurvivalConfig.TORCH_BURN_TICKS.get());
        int after = player.getRemainingFireTicks();
        if (before > 0 && after > before) {
            // Extend only by complete vanilla damage periods. Resetting to 80 every
            // frame would make baseTick's fireTicks % 20 damage gate run every frame.
            player.setRemainingFireTicks(before + ((after - before) / 20) * 20);
        }
        return true;
    }

    public static boolean standingOnHotTorch(Player player) {
        if (!player.onGround()) return false;
        AABB body = player.getBoundingBox();
        double feet = body.minY;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(body.minX, feet - CONTACT_EPSILON, body.minZ),
                BlockPos.containing(body.maxX, feet + CONTACT_EPSILON, body.maxZ))) {
            if (!player.level().hasChunkAt(pos)) continue;
            var state = player.level().getBlockState(pos);
            if (!TorchShapes.isHot(state)) continue;
            var shape = TorchShapes.collisionShape(state);
            if (shape == null || shape.isEmpty()) continue;
            double top = shape.max(Direction.Axis.Y);
            if (Math.abs(feet - (pos.getY() + top)) > CONTACT_EPSILON) continue;
            for (AABB part : shape.toAabbs()) {
                // Only the highest surface contains the flame; wall-rod ledges and
                // a merely touching side face cannot stand in for that surface.
                if (Math.abs(part.maxY - top) > 1e-7) continue;
                if (body.maxX > pos.getX() + part.minX + 1e-7
                        && body.minX < pos.getX() + part.maxX - 1e-7
                        && body.maxZ > pos.getZ() + part.minZ + 1e-7
                        && body.minZ < pos.getZ() + part.maxZ - 1e-7) return true;
            }
        }
        return false;
    }

    private TorchContactRules() { }
}
