package com.example.examplemod.maid;

import com.example.examplemod.config.SurvivalConfig;
import com.example.examplemod.devour.DevourRules;
import com.example.examplemod.init.ModDamageTypes;
import com.example.examplemod.mixin.LivingEntityAccessor;
import com.example.examplemod.scale.PixelScaleHelper;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.WeakHashMap;

/** Shrink, stomp, then maybe eat. No Touhou Little Maid types. */
public final class MaidTinyHunt {
    public static final float STOMP_DAMAGE = 2.0F;
    public static final double DEVOUR_CHANCE = 0.5D;
    public static final int OWNER_TARGET_TICKS = 200;

    public enum Result {
        IGNORED, SHRUNK, STOMPED, DEVOURING
    }

    private static final class Session {
        final Mob captor;
        final long startedAt;
        long lastTick = Long.MIN_VALUE;

        Session(Mob captor, long startedAt) {
            this.captor = captor;
            this.startedAt = startedAt;
        }
    }

    private static final Map<LivingEntity, Session> SESSIONS = new WeakHashMap<>();
    private static boolean releasing;

    private MaidTinyHunt() {
    }

    public static boolean isAllowedVictim(LivingEntity actor, LivingEntity target) {
        if (actor == null || target == null || target == actor || !target.isAlive() || target.isRemoved()) {
            return false;
        }
        if (target instanceof Player player) {
            if (player.isSpectator() || player.isCreative()) {
                return false;
            }
        }
        return !(actor instanceof TamableAnimal animal) || !target.getUUID().equals(animal.getOwnerUUID());
    }

    public static boolean isRecentOwnerTarget(LivingEntity owner, LivingEntity target) {
        if (owner == null || target == null || !target.isAlive() || owner.getLastHurtMob() != target) {
            return false;
        }
        int age = owner.tickCount - owner.getLastHurtMobTimestamp();
        return age >= 0 && age <= OWNER_TARGET_TICKS;
    }

    /** The attacker is who last hurt this entity, and it happened within the hunt memory window. */
    public static boolean wasRecentlyHurtBy(LivingEntity victim, LivingEntity attacker) {
        if (victim == null || attacker == null || !attacker.isAlive() || victim.getLastHurtByMob() != attacker) {
            return false;
        }
        int age = victim.tickCount - victim.getLastHurtByMobTimestamp();
        return age >= 0 && age <= OWNER_TARGET_TICKS;
    }

    public static boolean isHuntTarget(LivingEntity actor, LivingEntity owner, LivingEntity target) {
        if (!isAllowedVictim(actor, target) || target == owner) {
            return false;
        }
        if (target instanceof Player) {
            return wasRecentlyHurtBy(actor, target) || wasRecentlyHurtBy(owner, target);
        }
        if (isRecentOwnerTarget(owner, target)) {
            return true;
        }
        return target instanceof Enemy;
    }

    public static boolean isDevouring(LivingEntity victim) {
        return victim != null && SESSIONS.containsKey(victim);
    }

    /** Body contact, so the hunt connects by running into the target. */
    public static boolean isRamContact(LivingEntity actor, LivingEntity target) {
        return actor != null && target != null && actor.getBoundingBox().inflate(0.05D).intersects(target.getBoundingBox());
    }

    public static boolean blocksCaptorDamage(LivingEntity captor, DamageSource source) {
        if (captor == null || source == null) {
            return false;
        }
        return isHeldBy(source.getEntity(), captor) || isHeldBy(source.getDirectEntity(), captor);
    }

    public static Result strike(LivingEntity actor, LivingEntity target, double roll) {
        if (!(actor instanceof Mob captor) || !isAllowedVictim(actor, target) || isDevouring(target)) {
            return Result.IGNORED;
        }
        if (target instanceof Player player) {
            if (!PixelScaleHelper.isTiny(player)) {
                PixelScaleHelper.ensurePlayerMini(player);
                return Result.SHRUNK;
            }
        } else if (!PixelScaleHelper.isPlayerTinyScale(target)) {
            PixelScaleHelper.shrinkToPlayerScale(target);
            return Result.SHRUNK;
        }
        if (!applyStomp(captor, target) || !target.isAlive()) {
            return target.isAlive() ? Result.IGNORED : Result.STOMPED;
        }
        if (target.getHealth() <= target.getMaxHealth() * 0.5F
                && !isBusy(captor)
                && Double.isFinite(roll)
                && roll >= 0.0D
                && roll < DEVOUR_CHANCE
                && beginDevour(captor, target)) {
            return Result.DEVOURING;
        }
        return Result.STOMPED;
    }

    public static void tickDevour(LivingEntity victim, long now) {
        Session session = SESSIONS.get(victim);
        if (session == null || victim.level().isClientSide() || session.lastTick == now) {
            return;
        }
        session.lastTick = now;
        Mob captor = session.captor;
        if (!victim.isAlive() || victim.isRemoved() || !captor.isAlive() || captor.isRemoved()
                || captor.level() != victim.level() || victim.getVehicle() != captor) {
            release(victim);
            return;
        }
        captor.positionRider(victim);
        victim.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        if (victim instanceof Mob mob) {
            mob.setTarget(null);
        }
        int duration = SurvivalConfig.DEVOUR_DURATION.get();
        long elapsed = now - session.startedAt;
        if (elapsed >= duration * 7L / 10L && elapsed % 8L == 0L) {
            victim.level().playSound(null, captor.getX(), captor.getY(), captor.getZ(),
                    SoundEvents.GENERIC_EAT, SoundSource.HOSTILE, 0.7F, 0.9F);
        }
        if (elapsed >= duration) {
            finish(victim, session);
        }
    }

    public static boolean shouldKeepMounted(LivingEntity victim) {
        return !releasing && isDevouring(victim);
    }

    public static void release(LivingEntity entity) {
        if (!(entity instanceof LivingEntity victim)) {
            return;
        }
        Session session = SESSIONS.remove(victim);
        if (session == null) {
            releaseCaptor(entity);
            return;
        }
        releasing = true;
        try {
            if (victim.getVehicle() == session.captor) {
                victim.stopRiding();
            }
        } finally {
            releasing = false;
        }
    }

    public static void releaseCaptor(LivingEntity captor) {
        for (Map.Entry<LivingEntity, Session> entry : Map.copyOf(SESSIONS).entrySet()) {
            if (entry.getValue().captor == captor) {
                release(entry.getKey());
            }
        }
    }

    private static boolean isHeldBy(Entity attacker, LivingEntity captor) {
        if (!(attacker instanceof LivingEntity living)) {
            return false;
        }
        Session session = SESSIONS.get(living);
        return session != null && session.captor == captor;
    }

    private static boolean isBusy(Mob captor) {
        for (Session session : SESSIONS.values()) {
            if (session.captor == captor) {
                return true;
            }
        }
        return false;
    }

    private static boolean applyStomp(Mob actor, LivingEntity target) {
        int previousInvulnerableTime = target.invulnerableTime;
        float previousLastHurt = ((LivingEntityAccessor) target).examplemod$getLastHurt();
        try {
            return target.hurt(ModDamageTypes.getStompSource(target.level(), actor), STOMP_DAMAGE);
        } finally {
            target.invulnerableTime = previousInvulnerableTime;
            ((LivingEntityAccessor) target).examplemod$setLastHurt(previousLastHurt);
        }
    }

    private static boolean beginDevour(Mob captor, LivingEntity target) {
        if (target.isPassenger() || !target.startRiding(captor, true)) {
            return false;
        }
        SESSIONS.put(target, new Session(captor, target.level().getGameTime()));
        target.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        captor.positionRider(target);
        return true;
    }

    private static void finish(LivingEntity victim, Session session) {
        SESSIONS.remove(victim);
        releasing = true;
        try {
            DevourRules.finishDevour(session.captor, victim);
            if (victim.getVehicle() == session.captor) {
                victim.stopRiding();
            }
        } finally {
            releasing = false;
        }
    }
}
