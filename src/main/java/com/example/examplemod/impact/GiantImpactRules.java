package com.example.examplemod.impact;

import com.example.examplemod.config.SurvivalConfig;
import com.example.examplemod.devour.DevourRules;
import com.example.examplemod.network.GiantImpactPayload;
import com.example.examplemod.scale.PixelScaleHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class GiantImpactRules {
    private static final Map<LivingEntity, PreTickData> PRE_TICK_DATA = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<LivingEntity, Long> LAST_JUMP_TICKS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<LivingEntity, Long> LAST_LANDING_TICKS = Collections.synchronizedMap(new WeakHashMap<>());

    private record PreTickData(Vec3 position, Vec3 velocity, boolean onGround,
                              long tick, double totalDescent) {}

    public static void recordPreTick(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide()) return;
        if (!isEligibleSource(entity)) {
            clear(entity);
            return;
        }
        long tick = entity.level().getGameTime();
        PreTickData previous = PRE_TICK_DATA.get(entity);
        double descent = 0.0;
        if (previous != null && previous.tick() == tick - 1 && !entity.onGround()) {
            double dy = previous.position().y - entity.getY();
            // Only retain descent consistent with ordinary travel, not position corrections.
            if (dy > 0 && dy <= Math.abs(previous.velocity().y) + 0.1
                    && entity.position().subtract(previous.position()).horizontalDistanceSqr() <= 16) {
                descent = previous.totalDescent() + dy;
            }
        }
        PRE_TICK_DATA.put(entity, new PreTickData(entity.position(), entity.getDeltaMovement(),
                entity.onGround(), tick, descent));
    }

    public static boolean postTick(LivingEntity entity) {
        if (entity == null || entity.level() == null || entity.level().isClientSide()) {
            return false;
        }
        if (!isEligibleSource(entity)) {
            return false;
        }

        PreTickData data = PRE_TICK_DATA.get(entity);
        if (data == null) {
            return false;
        }

        // Require a current snapshot, a real downward velocity, and a supported landing.
        // A teleport that only edits position/onGround cannot masquerade as travel.
        double deltaY = data.position().y - entity.getY();
        if (data.tick() != entity.level().getGameTime() || data.onGround() || !entity.onGround()
                || data.velocity().y >= -1e-4 || deltaY <= 1e-4
                || deltaY > Math.abs(data.velocity().y) + 0.1
                || data.totalDescent() + deltaY < 0.1
                || entity.position().subtract(data.position()).horizontalDistanceSqr() > 16) {
            return false;
        }

        // Deduplication per action in the same tick
        long currentTick = entity.level().getGameTime();
        Long lastLanding = LAST_LANDING_TICKS.get(entity);
        if (lastLanding != null && lastLanding == currentTick) {
            return false;
        }
        LAST_LANDING_TICKS.put(entity, currentTick);
        PRE_TICK_DATA.remove(entity);

        double maxRadius = Math.max(getShakeRadius(), getKnockbackRadius());
        AABB searchBox = entity.getBoundingBox().inflate(maxRadius);
        List<Player> nearby = entity.level().getEntitiesOfClass(
                Player.class,
                searchBox,
                p -> isValidVictim(entity, p)
        );

        for (Player p : nearby) {
            applyImpact(entity, p, true);
        }

        return true;
    }

    public static boolean onJump(LivingEntity entity) {
        if (entity == null || entity.level() == null || entity.level().isClientSide()) {
            return false;
        }
        if (!isEligibleSource(entity)) {
            return false;
        }

        long currentTick = entity.level().getGameTime();
        Long lastJump = LAST_JUMP_TICKS.get(entity);
        if (lastJump != null && lastJump == currentTick) {
            return false;
        }
        LAST_JUMP_TICKS.put(entity, currentTick);

        double maxRadius = Math.max(getShakeRadius(), getKnockbackRadius());
        AABB searchBox = entity.getBoundingBox().inflate(maxRadius);
        List<Player> nearby = entity.level().getEntitiesOfClass(
                Player.class,
                searchBox,
                p -> isValidVictim(entity, p)
        );

        for (Player p : nearby) {
            applyImpact(entity, p, false);
        }

        return true;
    }

    public static boolean applyImpact(LivingEntity source, Player victim, boolean landing) {
        if (source == null || victim == null || source.level().isClientSide() || source.level() != victim.level()) {
            return false;
        }
        if (!source.isAlive() || !victim.isAlive() || source.isRemoved() || victim.isRemoved()) {
            return false;
        }
        if (source == victim) {
            return false;
        }
        if (!isEligibleSource(source)) {
            return false;
        }
        if (!isValidVictim(source, victim)) {
            return false;
        }

        Vec3 sFeet = source.position();
        Vec3 vFeet = victim.position();
        double dist = sFeet.distanceTo(vFeet);

        double shakeRadius = getShakeRadius();
        double knockbackRadius = getKnockbackRadius();
        double maxRadius = Math.max(shakeRadius, knockbackRadius);

        if (dist > maxRadius) {
            return false;
        }

        // Raycast obstruction check at feet + ~0.03
        Vec3 start = sFeet.add(0.0, 0.03, 0.0);
        Vec3 end = vFeet.add(0.0, 0.03, 0.0);
        if (isObstructed(source.level(), source, start, end)) {
            return false;
        }

        double actionFactor = landing ? 1.0D : getJumpFactor();
        boolean affected = false;

        // Knockback within 2.0 blocks (inclusive of boundary)
        if (knockbackRadius > 0 && dist <= knockbackRadius) {
            double dx = vFeet.x - sFeet.x;
            double dz = vFeet.z - sFeet.z;
            double horizDist = Math.sqrt(dx * dx + dz * dz);
            double dirX;
            double dirZ;

            if (horizDist < 1e-4) {
                // Degenerate radial direction: use source view vector or yaw
                Vec3 look = source.getViewVector(1.0F);
                double lookH = Math.sqrt(look.x * look.x + look.z * look.z);
                if (lookH > 1e-4) {
                    dirX = look.x / lookH;
                    dirZ = look.z / lookH;
                } else {
                    float yRot = source.getYRot();
                    dirX = -Math.sin(Math.toRadians(yRot));
                    dirZ = Math.cos(Math.toRadians(yRot));
                    if (dirX * dirX + dirZ * dirZ < 1e-4) {
                        dirX = 0.0;
                        dirZ = 1.0;
                    }
                }
            } else {
                dirX = dx / horizDist;
                dirZ = dz / horizDist;
            }

            double hImpulse = getHorizontalImpulse() * actionFactor;
            double vImpulse = getVerticalImpulse() * actionFactor;

            victim.setDeltaMovement(victim.getDeltaMovement().add(dirX * hImpulse, vImpulse, dirZ * hImpulse));
            victim.hurtMarked = true;
            affected = true;
        }

        // Camera shake within 6.0 blocks (linear attenuation)
        if (shakeRadius > 0 && dist < shakeRadius) {
            double distFactor = Math.max(0.0D, 1.0D - (dist / shakeRadius));
            float intensity = (float) (distFactor * actionFactor);
            if (intensity > 0.0F) {
                if (victim instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
                    PacketDistributor.sendToPlayer(serverPlayer, new GiantImpactPayload(intensity, SurvivalConfig.GIANT_IMPACT_SHAKE_AMPLITUDE.get().floatValue(),
                            SurvivalConfig.GIANT_IMPACT_SHAKE_DURATION.get()));
                }
                affected = true;
            }
        }

        return affected;
    }

    public static boolean isEligibleSource(LivingEntity source) {
        if (source == null || !source.isAlive() || source.isRemoved()) {
            return false;
        }
        // Scaled bounding box height must be at least 1.0 block
        if (source.getBbHeight() < 1.0F) {
            return false;
        }
        if (source.isPassenger()) {
            return false;
        }
        if (isInFluid(source)) {
            return false;
        }
        if (source.onClimbable()) {
            return false;
        }
        if (source.isFallFlying() || source instanceof net.minecraft.world.entity.FlyingMob
                || source instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon) {
            return false;
        }
        if (source instanceof Player player) {
            if (player.getAbilities().flying || player.isSpectator() || player.isCreative()) {
                return false;
            }
        }
        return true;
    }

    public static boolean isValidVictim(LivingEntity source, Player victim) {
        if (victim == null || !victim.isAlive() || victim.isRemoved()) {
            return false;
        }
        if (victim == source) {
            return false;
        }
        if (!PixelScaleHelper.isTiny(victim)) {
            return false;
        }
        if (victim.isCreative() || victim.isSpectator() || DevourRules.isCaptured(victim)) {
            return false;
        }
        // Co-riding immunity
        if (victim.isPassengerOfSameVehicle(source)
                || victim.getVehicle() == source
                || source.getVehicle() == victim
                || (victim.getRootVehicle() == source.getRootVehicle() && (victim.isPassenger() || source.isPassenger()))) {
            return false;
        }
        return true;
    }

    public static boolean isObstructed(Level level, Entity source, Vec3 start, Vec3 end) {
        if (level == null || start.distanceToSqr(end) < 1e-6) {
            return false;
        }
        ClipContext context = new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                source
        );
        BlockHitResult hit = level.clip(context);
        return hit.getType() != HitResult.Type.MISS;
    }

    public static void clear(LivingEntity entity) {
        if (entity != null) {
            PRE_TICK_DATA.remove(entity);
            LAST_JUMP_TICKS.remove(entity);
            LAST_LANDING_TICKS.remove(entity);
        }
    }

    public static void invalidateMotion(LivingEntity entity) {
        PRE_TICK_DATA.remove(entity);
    }

    public static void clear(UUID uuid) {
        if (uuid != null) {
            PRE_TICK_DATA.keySet().removeIf(e -> e.getUUID().equals(uuid));
            LAST_JUMP_TICKS.keySet().removeIf(e -> e.getUUID().equals(uuid));
            LAST_LANDING_TICKS.keySet().removeIf(e -> e.getUUID().equals(uuid));
        }
    }

    public static void clearAll() {
        PRE_TICK_DATA.clear();
        LAST_JUMP_TICKS.clear();
        LAST_LANDING_TICKS.clear();
    }

    private static boolean isInFluid(LivingEntity entity) {
        return entity.isInWaterOrBubble() || entity.isInLava() || entity.isInFluidType() || entity.isSwimming();
    }

    private static double getShakeRadius() {
        return SurvivalConfig.GIANT_IMPACT_SHAKE_RADIUS.get();
    }

    private static double getKnockbackRadius() {
        return SurvivalConfig.GIANT_IMPACT_KNOCKBACK_RADIUS.get();
    }

    private static double getHorizontalImpulse() {
        return SurvivalConfig.GIANT_IMPACT_HORIZONTAL_IMPULSE.get();
    }

    private static double getVerticalImpulse() {
        return SurvivalConfig.GIANT_IMPACT_VERTICAL_IMPULSE.get();
    }

    private static double getJumpFactor() {
        return SurvivalConfig.GIANT_IMPACT_JUMP_FACTOR.get();
    }

    private GiantImpactRules() {
    }
}
