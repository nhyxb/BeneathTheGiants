package com.example.examplemod.stomp;

import com.example.examplemod.init.ModDamageTypes;
import com.example.examplemod.mixin.LivingEntityAccessor;
import com.example.examplemod.scale.PixelScaleHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public class StompHandler {
    private static final Map<LivingEntity, Long> STOMP_COOLDOWNS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<LivingEntity, FallData> FALL_TRACKER = Collections.synchronizedMap(new WeakHashMap<>());

    public record FallData(float preFallDistance, double preY, boolean preOnGround, long tick) {}

    public static void recordPreTick(LivingEntity entity) {
        if (entity != null && !entity.level().isClientSide) {
            FALL_TRACKER.put(entity, new FallData(entity.fallDistance, entity.getY(), entity.onGround(), entity.level().getGameTime()));
        }
    }

    public static void clearCooldown(LivingEntity entity) {
        if (entity != null) {
            STOMP_COOLDOWNS.remove(entity);
            FALL_TRACKER.remove(entity);
        }
    }

    public static void clearCooldown(UUID uuid) {
        STOMP_COOLDOWNS.keySet().removeIf(e -> e.getUUID().equals(uuid));
        FALL_TRACKER.keySet().removeIf(e -> e.getUUID().equals(uuid));
    }

    public static void clearAllCooldowns() {
        STOMP_COOLDOWNS.clear();
        FALL_TRACKER.clear();
    }

    public static boolean tryStomp(LivingEntity stomper, LivingEntity victim) {
        return tryStomp(stomper, victim, -1.0F);
    }

    public static boolean tryStomp(LivingEntity stomper, LivingEntity victim, float customFallDistance) {
        if (stomper == null || victim == null || stomper == victim) {
            return false;
        }
        if (!stomper.isAlive() || !victim.isAlive() || stomper.isRemoved() || victim.isRemoved()) {
            return false;
        }

        // Mutual riding exemption
        if (stomper.isPassengerOfSameVehicle(victim) || stomper.getVehicle() == victim || victim.getVehicle() == stomper) {
            return false;
        }

        // Creative/spectator player immunity
        if (victim instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }

        // Target must already be shrunk: an opted-in player, or a mob marked by the wand.
        if (!PixelScaleHelper.isTiny(victim)) {
            return false;
        }

        // Height ratio threshold: stomper must be at least 4x victim's height
        double stomperHeight = stomper.getBbHeight();
        double victimHeight = Math.max(1e-4, victim.getBbHeight());
        if ((stomperHeight / victimHeight) < 4.0D) {
            return false;
        }

        // Determine effective fall distance
        float effectiveFallDistance = customFallDistance;
        if (effectiveFallDistance < 0.0F) {
            FallData data = FALL_TRACKER.get(stomper);
            if (data != null && (!data.preOnGround() || !stomper.onGround() || stomper.getY() < data.preY() - 1e-4)) {
                if (stomper.fallDistance > 0) {
                    effectiveFallDistance = stomper.fallDistance;
                } else if (data.preFallDistance() > 0) {
                    effectiveFallDistance = data.preFallDistance() + (float) Math.max(0.0, data.preY() - stomper.getY());
                } else if (stomper.getY() < data.preY() - 0.05 && !stomper.onGround()) {
                    effectiveFallDistance = (float) Math.max(0.0, data.preY() - stomper.getY());
                } else {
                    effectiveFallDistance = 0.0F;
                }
            } else {
                effectiveFallDistance = stomper.fallDistance;
            }
        }

        // Movement check: stomper must be moving horizontally or falling
        Vec3 movement = stomper.position().subtract(stomper.xo, stomper.yo, stomper.zo);
        boolean isMovingHorizontally = (movement.x * movement.x + movement.z * movement.z) >= 1e-4;
        boolean isMovingDown = movement.y < -1e-4;
        boolean isFalling = effectiveFallDistance > 0 || stomper.fallDistance > 0;
        if (!isMovingHorizontally && !isMovingDown && !isFalling) {
            return false;
        }

        // Horizontal footprint overlap
        double sMinX = stomper.getX() - stomper.getBbWidth() / 2.0;
        double sMaxX = stomper.getX() + stomper.getBbWidth() / 2.0;
        double sMinZ = stomper.getZ() - stomper.getBbWidth() / 2.0;
        double sMaxZ = stomper.getZ() + stomper.getBbWidth() / 2.0;

        double vMinX = victim.getX() - victim.getBbWidth() / 2.0;
        double vMaxX = victim.getX() + victim.getBbWidth() / 2.0;
        double vMinZ = victim.getZ() - victim.getBbWidth() / 2.0;
        double vMaxZ = victim.getZ() + victim.getBbWidth() / 2.0;

        boolean horizontalOverlap = sMinX < vMaxX && sMaxX > vMinX && sMinZ < vMaxZ && sMaxZ > vMinZ;
        if (!horizontalOverlap) {
            return false;
        }

        // Vertical relationship with ~0.02 tolerance
        double sFootY = stomper.getY();
        double sPrevFootY = stomper.yo;
        double vBottomY = victim.getY();
        double vTopY = victim.getY() + victim.getBbHeight();

        boolean footNearTop = (sFootY >= vBottomY - 0.02) && (sFootY <= vTopY + 0.02);
        boolean downwardSwept = (sPrevFootY >= vTopY - 0.02) && (sFootY <= vTopY + 0.02);
        if (!footNearTop && !downwardSwept) {
            return false;
        }

        // Solid block barrier check: ensure no solid block separating stomper foot and victim top
        double lowY = Math.min(sFootY, vTopY);
        double highY = Math.max(sFootY, vTopY);
        if (highY - lowY > 0.05) {
            AABB gap = new AABB(
                    Math.max(sMinX, vMinX), lowY + 0.01, Math.max(sMinZ, vMinZ),
                    Math.min(sMaxX, vMaxX), highY - 0.01, Math.min(sMaxZ, vMaxZ)
            );
            if (!stomper.level().noBlockCollision(null, gap)) {
                return false;
            }
        }

        // 10-tick independent cooldown per victim
        long currentTick = stomper.level().getGameTime();
        Long lastStompTick = STOMP_COOLDOWNS.get(victim);
        if (lastStompTick != null && (currentTick - lastStompTick) < 10) {
            return false;
        }

        // Damage calculation: 2 for walking, 2 + min(fallDistance, 8) for falling
        float damage = 2.0F;
        float fallDist = Math.max(effectiveFallDistance, stomper.fallDistance);
        if (fallDist > 0.0F) {
            damage = 2.0F + Math.min(fallDist, 8.0F);
        }

        DamageSource source = ModDamageTypes.getStompSource(stomper.level(), stomper);
        int prevInvulnerableTime = victim.invulnerableTime;
        float prevLastHurt = ((LivingEntityAccessor) victim).examplemod$getLastHurt();
        boolean hurtSuccess = false;
        try {
            hurtSuccess = victim.hurt(source, damage);
        } finally {
            victim.invulnerableTime = prevInvulnerableTime;
            ((LivingEntityAccessor) victim).examplemod$setLastHurt(prevLastHurt);
        }

        if (hurtSuccess) {
            STOMP_COOLDOWNS.put(victim, currentTick);
        }
        return hurtSuccess;
    }

    public static void checkStompOnTick(LivingEntity stomper) {
        if (stomper.level().isClientSide || !stomper.isAlive()) {
            return;
        }

        Vec3 movement = stomper.position().subtract(stomper.xo, stomper.yo, stomper.zo);
        boolean isMovingHorizontally = (movement.x * movement.x + movement.z * movement.z) >= 1e-4;
        boolean isMovingDown = movement.y < -1e-4;
        boolean isFalling = stomper.fallDistance > 0;

        FallData data = FALL_TRACKER.get(stomper);
        if (data != null && (data.preFallDistance() > 0 || stomper.getY() < data.preY() - 1e-4)) {
            isFalling = true;
        }

        if (!isMovingHorizontally && !isMovingDown && !isFalling) {
            return;
        }

        double minFootY = Math.min(stomper.getY(), stomper.yo) - 0.05;
        double maxFootY = Math.max(stomper.getY(), stomper.yo) + 0.05;
        AABB queryAabb = stomper.getBoundingBox().setMinY(minFootY).setMaxY(maxFootY);

        List<LivingEntity> candidates = stomper.level().getEntitiesOfClass(
                LivingEntity.class,
                queryAabb,
                e -> e != stomper && e.isAlive() && PixelScaleHelper.isTiny(e)
        );

        for (LivingEntity candidate : candidates) {
            tryStomp(stomper, candidate);
        }
    }
}
