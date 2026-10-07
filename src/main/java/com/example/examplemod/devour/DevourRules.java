package com.example.examplemod.devour;

import com.example.examplemod.config.SurvivalConfig;
import com.example.examplemod.init.ModTags;
import com.example.examplemod.scale.PixelScaleHelper;
import com.example.examplemod.survival.SurvivalRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Server-only sessions; client presentation reads per-player synced entity data. */
public final class DevourRules {
    private static final Map<Player, Session> SESSIONS = new HashMap<>();
    private static final Map<Entity, Long> COOLDOWNS = new WeakHashMap<>();

    private static final class Session {
        final Mob captor;
        final long startedAt;
        final int duration;
        Vec3 releasePoint;
        Vec3 captorPosition;
        long lastTick = Long.MIN_VALUE;
        DamageSource finishingSource;
        boolean mounting;

        Session(Mob captor, Player player) {
            this.captor = captor;
            startedAt = player.level().getGameTime();
            duration = SurvivalConfig.DEVOUR_DURATION.get();
            releasePoint = player.position();
            captorPosition = captor.position();
        }
    }

    public static boolean isCaptured(Player player) {
        return ((DevourPlayerAccess) player).examplemod$getCaptorId() >= 0;
    }

    public static boolean shouldBlockDamage(Player player, DamageSource source) {
        if (!isCaptured(player)) return false;
        if (player.level().isClientSide()) return true;
        Session session = SESSIONS.get(player);
        return session == null || session.finishingSource != source;
    }

    public static boolean isCandidate(Mob captor, Player player) {
        return captor != null && player != null && !player.level().isClientSide() && captor.level() == player.level()
                && player.isAlive() && !player.isRemoved() && !player.isCreative() && !player.isSpectator()
                && PixelScaleHelper.isTiny(player) && player.getHealth() <= 2.0F
                && !isCaptured(player) && !player.isPassenger() && !player.isSleeping()
                && eligibleCaptor(captor, player) && !captor.isPassenger() && captor.getPassengers().isEmpty()
                && captor.distanceToSqr(player) <= 2.5 * 2.5 && captor.hasLineOfSight(player);
    }

    private static boolean eligibleCaptor(Mob captor, Player player) {
        return captor.isAlive() && !captor.isRemoved() && captor.getType().is(ModTags.HUMANOID_DEVOURERS)
                && captor.getBbHeight() >= player.getBbHeight() * 4;
    }

    /** Roll is supplied by the accepted-attack event; failed eligible attempts also cool down. */
    public static boolean attemptCapture(Mob captor, Player player, double roll) {
        if (!isCandidate(captor, player)) return false;
        long now = player.level().getGameTime();
        if (COOLDOWNS.getOrDefault(player, Long.MIN_VALUE) > now
                || COOLDOWNS.getOrDefault(captor, Long.MIN_VALUE) > now) return false;
        int cooldown = SurvivalConfig.DEVOUR_COOLDOWN.get();
        COOLDOWNS.put(player, now + cooldown);
        COOLDOWNS.put(captor, now + cooldown);
        if (!Double.isFinite(roll) || roll < 0 || roll >= SurvivalConfig.DEVOUR_CHANCE.get()) return false;

        Vec3 anchor = anchorPosition(captor, player, 0);
        if (!clearPosition(player, anchor) || !clearPath(player, player.position(), anchor)) return false;
        Session session = new Session(captor, player);
        SESSIONS.put(player, session);
        session.mounting = true;
        setState(player, captor.getId(), 0);
        boolean mounted;
        try {
            mounted = player.startRiding(captor, true);
        } finally {
            session.mounting = false;
        }
        if (!mounted) {
            setState(player, -1, 0);
            SESSIONS.remove(player);
            return false;
        }
        SurvivalRules.clearPlayer(player.getUUID());
        player.stopUsingItem();
        player.setShiftKeyDown(false);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        captor.positionRider(player);
        return true;
    }

    public static boolean allowMount(Player player, Entity vehicle, boolean mounting) {
        if (!isCaptured(player)) return true;
        // Client passenger packets are authoritative and may precede metadata updates.
        if (player.level().isClientSide()) return true;
        Session session = SESSIONS.get(player);
        return mounting && session != null && session.mounting && vehicle == session.captor;
    }

    public static void tick(Player player) {
        if (player.level().isClientSide()) return;
        Session session = SESSIONS.get(player);
        if (session == null) return;
        long now = player.level().getGameTime();
        if (session.lastTick == now) return;
        session.lastTick = now;
        Mob captor = session.captor;
        if (!player.isAlive() || player.isRemoved() || player.isCreative() || player.isSpectator()
                || captor.level() != player.level() || !eligibleCaptor(captor, player)
                || captor.isPassenger() || player.getVehicle() != captor
                || captor.position().distanceToSqr(session.captorPosition) > 64) {
            release(player);
            return;
        }
        session.captorPosition = captor.position();
        Vec3 safe = nearbySafePosition(player, captor);
        if (safe != null) session.releasePoint = safe;
        float progress = Math.clamp((float) (now - session.startedAt) / session.duration, 0, 1);
        Vec3 anchor = anchorPosition(captor, player, progress);
        if (!clearPosition(player, anchor) || !clearPath(player, player.position(), anchor)) {
            release(player);
            return;
        }
        setState(player, captor.getId(), progress);
        player.setShiftKeyDown(false);
        player.stopUsingItem();
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        captor.positionRider(player);
        if (progress >= 0.7F && (now - session.startedAt) % 8 == 0) {
            player.level().playSound(null, captor.getX(), captor.getY(), captor.getZ(),
                    SoundEvents.GENERIC_EAT, SoundSource.HOSTILE, 0.7F, 0.9F);
        }
        if (now - session.startedAt >= session.duration) finish(player, session);
    }

    public static void finishDevour(Mob captor, LivingEntity victim) {
        if (captor == null || victim == null || victim.level().isClientSide()) {
            return;
        }
        victim.hurt(new DevourFinishDamageSource(victim.level(), captor), Float.MAX_VALUE);
    }

    private static void finish(Player player, Session session) {
        // Identity, not merely the damage type, opens this one-call exception.
        session.finishingSource = new DevourFinishDamageSource(player.level(), session.captor);
        try {
            player.hurt(session.finishingSource, Float.MAX_VALUE);
        } finally {
            session.finishingSource = null;
            release(player);
        }
    }

    public static void release(Player player) {
        release(player, true);
    }

    private static void release(Player player, boolean relocate) {
        if (player.level().isClientSide()) return;
        Session session = SESSIONS.remove(player);
        if (session == null && !isCaptured(player)) return;
        setState(player, -1, 0);
        if (relocate) player.stopRiding();
        else player.removeVehicle(); // stopRiding() implicitly runs vanilla dismount positioning.
        if (relocate && session != null && session.captor.level() == player.level()) {
            Vec3 safe = nearbySafePosition(player, session.captor);
            Vec3 point = safe != null ? safe : session.releasePoint;
            if (clearPosition(player, point)) {
                if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
                    serverPlayer.connection.teleport(point.x, point.y, point.z, player.getYRot(), player.getXRot());
                } else {
                    player.setPos(point);
                }
            }
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        player.clearFire();
        player.setAirSupply(player.getMaxAirSupply());
        player.setShiftKeyDown(false);
        player.hurtMarked = true;
    }

    public static void releaseFor(Entity entity) {
        if (entity.level().isClientSide()) return;
        if (entity instanceof Player player) release(player);
        for (Map.Entry<Player, Session> entry : Map.copyOf(SESSIONS).entrySet()) {
            if (entry.getValue().captor == entity) release(entry.getKey());
        }
    }

    /** Leave callbacks can run inside entity-section iteration; never move another entity here. */
    public static void releaseForLeave(Entity entity) {
        if (entity.level().isClientSide()) return;
        if (entity instanceof Player player) release(player, false);
        for (Map.Entry<Player, Session> entry : Map.copyOf(SESSIONS).entrySet()) {
            if (entry.getValue().captor == entity) release(entry.getKey(), false);
        }
    }

    public static void clearAll() {
        for (Player player : SESSIONS.keySet().toArray(Player[]::new)) release(player);
        SESSIONS.clear();
        COOLDOWNS.clear();
    }

    private static void setState(Player player, int captorId, float progress) {
        ((DevourPlayerAccess) player).examplemod$setDevourState(captorId, progress);
    }

    /** Player feet, outside the torso/head; body yaw and scale are authoritative geometry. */
    public static Vec3 anchorPosition(Mob captor, Player player, float progress) {
        float t = Math.clamp(progress, 0, 1);
        double smooth = t * t * (3 - 2 * t);
        double height = captor.getBbHeight();
        double side = (captor.getMainArm() == HumanoidArm.RIGHT ? -1 : 1)
                * height * (0.24 * (1 - smooth) + 0.04 * smooth);
        double forward = Math.max(captor.getBbWidth() * 0.5 + player.getBbWidth() + 0.04,
                height * (0.28 * (1 - smooth) + 0.20 * smooth));
        double y = height * (0.58 * (1 - smooth) + 0.84 * smooth);
        double radians = Math.toRadians(captor.yBodyRot);
        // Forward=(-sin(yaw), cos(yaw)); local right/left is perpendicular.
        return captor.position().add(Math.cos(radians) * side - Math.sin(radians) * forward,
                y, Math.sin(radians) * side + Math.cos(radians) * forward);
    }

    private static Vec3 nearbySafePosition(Player player, Mob captor) {
        double radius = captor.getBbWidth() * 0.5 + player.getBbWidth() + 0.12;
        for (double dy : new double[]{0, -0.5, 0.5, -1, 1}) {
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                Vec3 pos = captor.position().add(Math.cos(angle) * radius, dy, Math.sin(angle) * radius);
                if (!clearPosition(player, pos)) continue;
                AABB box = player.getDimensions(Pose.STANDING).makeBoundingBox(pos);
                if (!player.level().noBlockCollision(player, box.move(0, -0.04, 0))) return pos;
            }
        }
        return null;
    }

    private static boolean clearPosition(Player player, Vec3 pos) {
        return player.level().hasChunkAt(BlockPos.containing(pos))
                && player.level().noBlockCollision(player, player.getDimensions(Pose.STANDING).makeBoundingBox(pos));
    }

    private static boolean clearPath(Player player, Vec3 from, Vec3 to) {
        Vec3 eye = new Vec3(0, player.getEyeHeight(), 0);
        return player.level().clip(new ClipContext(from.add(eye), to.add(eye),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }

    private DevourRules() { }
}
