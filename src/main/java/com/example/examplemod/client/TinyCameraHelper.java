package com.example.examplemod.client;

import com.example.examplemod.scale.PixelScaleHelper;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class TinyCameraHelper {
    private static final float TINY_NEAR_PLANE = 0.01F;

    public static float effectiveNearPlane(float vanillaNear) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return vanillaNear;
        }
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Entity entity = camera.getEntity();
        if (!(entity instanceof Player player) || !PixelScaleHelper.isTiny(player)) {
            return vanillaNear;
        }
        if (minFaceDistance(minecraft.level, camera, player) >= vanillaNear) {
            return vanillaNear;
        }
        return Math.min(vanillaNear, TINY_NEAR_PLANE);
    }

    private static double minFaceDistance(Level level, Camera camera, Player player) {
        BlockPos center = BlockPos.containing(camera.getPosition());
        List<AABB> bounds = new ArrayList<>(27);
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
            VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
            if (!shape.isEmpty()) {
                bounds.add(shape.bounds().move(pos));
            }
        }
        if (bounds.isEmpty()) {
            return Double.MAX_VALUE;
        }

        Vec3 base = camera.getPosition();
        Vec3 left = new Vec3(camera.getLeftVector().x, camera.getLeftVector().y, camera.getLeftVector().z);
        Vec3 up = new Vec3(camera.getUpVector().x, camera.getUpVector().y, camera.getUpVector().z);
        float partialTick = camera.getPartialTickTime();
        float walkDelta = player.walkDist - player.walkDistO;
        float walkPhase = -(player.walkDist + walkDelta * partialTick);
        float bobStrength = Mth.lerp(partialTick, player.oBob, player.bob);
        double bobX = Math.abs(Mth.sin(walkPhase * (float) Math.PI) * bobStrength * 0.5F);
        double bobY = Math.abs(Mth.cos(walkPhase * (float) Math.PI) * bobStrength);

        double min = Double.MAX_VALUE;
        for (int ix = -1; ix <= 1; ix++) {
            for (int iy = -1; iy <= 1; iy++) {
                Vec3 probe = base.add(left.scale(ix * bobX)).add(up.scale(iy * bobY));
                for (AABB box : bounds) {
                    min = Math.min(min, faceDistance(box, probe));
                }
            }
        }
        return min;
    }

    private static double faceDistance(AABB bounds, Vec3 point) {
        double dx = Math.max(bounds.minX - point.x, Math.max(0.0, point.x - bounds.maxX));
        double dy = Math.max(bounds.minY - point.y, Math.max(0.0, point.y - bounds.maxY));
        double dz = Math.max(bounds.minZ - point.z, Math.max(0.0, point.z - bounds.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private TinyCameraHelper() {
    }
}
