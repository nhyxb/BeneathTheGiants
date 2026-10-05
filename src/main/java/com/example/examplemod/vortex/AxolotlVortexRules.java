package com.example.examplemod.vortex;

import com.example.examplemod.config.SurvivalConfig;
import com.example.examplemod.devour.DevourRules;
import com.example.examplemod.init.ModDamageTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Loaded-source arbitration and player motion/damage run on the logical server only. */
public final class AxolotlVortexRules {
    private static final Set<Axolotl> SOURCES = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<Player, FeedingSession> SESSIONS = new HashMap<>();
    private static final Map<Player, AppliedStamp> APPLIED = new WeakHashMap<>();
    private static final Map<ServerLevel, Long> LEVEL_TICKS = new WeakHashMap<>();

    private record AppliedStamp(int levelIdentity, long tick) { }
    private record Candidate(Axolotl source, Player player, double distance) { }
    private static final class FeedingSession {
        final Axolotl source;
        long nextBite = Long.MIN_VALUE;
        FeedingSession(Axolotl source) { this.source = source; }
    }

    public static void register(Axolotl source) {
        if (!source.level().isClientSide()) SOURCES.add(source);
    }

    public static boolean hasSession(Player player) { return SESSIONS.containsKey(player); }

    public static Vec3 mouthPosition(Axolotl source) {
        Vec3 forward = forward(source);
        return source.position().add(0, source.getBbHeight() * 0.4, 0)
                .add(forward.scale(source.getBbWidth() * 0.75));
    }

    private static Vec3 forward(Axolotl source) {
        return Vec3.directionFromRotation(source.getXRot(), source.getYHeadRot());
    }

    private static boolean eligibleSource(Axolotl source) {
        return !source.level().isClientSide() && source.isAlive() && !source.isRemoved()
                && !source.isPlayingDead() && !source.isPassenger()
                && waterAt(source.level(), source.getBoundingBox().getCenter())
                && waterAt(source.level(), mouthPosition(source));
    }

    public static boolean isEligibleTarget(Axolotl source, Player player) {
        if (source == null || player == null || !eligibleSource(source)
                || player.level() != source.level() || !player.isAlive() || player.isRemoved()
                || player.isCreative() || player.isSpectator() || player.isPassenger()
                || DevourRules.isCaptured(player)
                || !com.example.examplemod.scale.PixelScaleHelper.isTiny(player)
                || !waterAt(player.level(), player.getBoundingBox().getCenter())
                || !waterAt(player.level(), player.getEyePosition())) return false;
        double range = SurvivalConfig.AXOLOTL_VORTEX_RANGE.get();
        Vec3 mouth = mouthPosition(source);
        Vec3 delta = player.getBoundingBox().getCenter().subtract(mouth);
        double distance = delta.length();
        if (range <= 0 || distance > range) return false;
        if (distance > SurvivalConfig.AXOLOTL_BITE_RANGE.get()
                && delta.normalize().dot(forward(source)) < 0.5) return false;
        // Traverse every crossed voxel before clipping, so neither query loads chunks.
        if (!hasWaterPath(player.level(), mouth, player.getBoundingBox().getCenter())) return false;
        return player.level().clip(new ClipContext(mouth, player.getBoundingBox().getCenter(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }

    public static void tickLevel(ServerLevel level) {
        long now = level.getGameTime();
        if (LEVEL_TICKS.getOrDefault(level, Long.MIN_VALUE) == now) return;
        LEVEL_TICKS.put(level, now);
        List<Axolotl> sources = List.copyOf(SOURCES).stream().filter(source -> source.level() == level).toList();
        List<Candidate> candidates = new ArrayList<>();
        double range = SurvivalConfig.AXOLOTL_VORTEX_RANGE.get();
        for (Axolotl source : sources) {
            if (!eligibleSource(source) || range <= 0) continue;
            // Mouth lies outside the collision body; include that offset in the broad-phase box.
            double queryRadius = range + source.getBbWidth();
            for (Player player : level.getEntitiesOfClass(Player.class, source.getBoundingBox().inflate(queryRadius))) {
                if (isEligibleTarget(source, player)) {
                    candidates.add(new Candidate(source, player,
                            player.getBoundingBox().getCenter().distanceToSqr(mouthPosition(source))));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::distance)
                .thenComparing(candidate -> candidate.source().getUUID())
                .thenComparing(candidate -> candidate.player().getUUID()));
        Set<Axolotl> assignedSources = new HashSet<>();
        Set<Player> assignedPlayers = new HashSet<>();
        for (Candidate candidate : candidates) {
            if (assignedSources.contains(candidate.source()) || assignedPlayers.contains(candidate.player())) continue;
            assignedSources.add(candidate.source());
            assignedPlayers.add(candidate.player());
            applyPair(candidate.source(), candidate.player(), now);
        }
        for (Player player : List.copyOf(SESSIONS.keySet())) {
            if (player.level() == level && !assignedPlayers.contains(player)) stopSession(player);
        }
        for (Axolotl source : sources) {
            if (!assignedSources.contains(source)) setState(source, -1, 0);
        }
    }

    /** Shared with deterministic probes; production always arbitrates first. */
    public static boolean applyPair(Axolotl source, Player player, long now) {
        if (!isEligibleTarget(source, player)) {
            FeedingSession old = SESSIONS.get(player);
            if (old != null && old.source == source) stopSession(player);
            return false;
        }
        AppliedStamp previous = APPLIED.get(player);
        int levelIdentity = System.identityHashCode(player.level());
        if (previous != null && previous.levelIdentity() == levelIdentity && previous.tick() == now) return false;
        APPLIED.put(player, new AppliedStamp(levelIdentity, now));
        FeedingSession session = SESSIONS.get(player);
        if (session == null || session.source != source) {
            stopSession(player);
            session = new FeedingSession(source);
            SESSIONS.put(player, session);
        }
        Vec3 delta = mouthPosition(source).subtract(player.getBoundingBox().getCenter());
        double distance = delta.length();
        if (distance > 1e-6) {
            Vec3 direction = delta.scale(1 / distance);
            Vec3 oldMotion = player.getDeltaMovement();
            double amount = Math.min(SurvivalConfig.AXOLOTL_VORTEX_PULL.get(),
                    Math.max(0, SurvivalConfig.AXOLOTL_VORTEX_MAX_SPEED.get() - oldMotion.dot(direction)));
            if (amount > 0) {
                player.setDeltaMovement(oldMotion.add(direction.scale(amount)));
                player.hurtMarked = true;
            }
        }
        boolean biting = distance <= SurvivalConfig.AXOLOTL_BITE_RANGE.get();
        setState(source, player.getId(), biting ? 2 : 1);
        if (!biting) {
            session.nextBite = Long.MIN_VALUE;
        } else if (session.nextBite == Long.MIN_VALUE) {
            session.nextBite = now + SurvivalConfig.AXOLOTL_BITE_INTERVAL.get();
        } else if (now >= session.nextBite) {
            session.nextBite = now + SurvivalConfig.AXOLOTL_BITE_INTERVAL.get();
            float damage = SurvivalConfig.AXOLOTL_BITE_DAMAGE.get().floatValue();
            if (damage > 0 && player.hurt(ModDamageTypes.getAxolotlBiteSource(player.level(), source), damage)) {
                player.level().playSound(null, source.getX(), source.getY(), source.getZ(),
                        SoundEvents.AXOLOTL_ATTACK, SoundSource.HOSTILE, 0.7F, 1.0F);
            }
            if (!player.isAlive()) stopSession(player);
        }
        if (now % 4 == 0 && player.isAlive() && source.level() instanceof ServerLevel level) {
            Vec3 mouth = mouthPosition(source);
            Vec3 center = player.getBoundingBox().getCenter();
            Vec3 ribbon = mouth.lerp(center, 0.5);
            level.sendParticles(ParticleTypes.BUBBLE, ribbon.x, ribbon.y, ribbon.z, 3, 0.06, 0.06, 0.06, 0.01);
        }
        return true;
    }

    private static void stopSession(Player player) {
        FeedingSession session = SESSIONS.remove(player);
        if (session != null && ((AxolotlVortexAccess) session.source).examplemod$getVortexTargetId() == player.getId()) {
            setState(session.source, -1, 0);
        }
    }

    public static void clearFor(Entity entity) {
        if (entity.level().isClientSide()) return;
        if (entity instanceof Player player) stopSession(player);
        if (entity instanceof Axolotl source) {
            for (Map.Entry<Player, FeedingSession> entry : Map.copyOf(SESSIONS).entrySet()) {
                if (entry.getValue().source == source) stopSession(entry.getKey());
            }
            setState(source, -1, 0);
            if (source.isRemoved()) SOURCES.remove(source);
        }
    }

    public static void unregister(Axolotl source) {
        clearFor(source);
        SOURCES.remove(source);
    }

    public static void clearAll() {
        for (Player player : List.copyOf(SESSIONS.keySet())) stopSession(player);
        SOURCES.clear();
        APPLIED.clear();
        LEVEL_TICKS.clear();
    }

    private static void setState(Axolotl source, int targetId, int phase) {
        ((AxolotlVortexAccess) source).examplemod$setVortexState(targetId, phase);
    }

    private static boolean waterAt(Level level, Vec3 point) {
        BlockPos pos = BlockPos.containing(point);
        if (!level.hasChunkAt(pos)) return false;
        var fluid = level.getFluidState(pos);
        return fluid.is(FluidTags.WATER) && point.y < pos.getY() + fluid.getHeight(level, pos);
    }

    public static boolean hasWaterPath(Level level, Vec3 from, Vec3 to) {
        if (from.equals(to)) return waterAt(level, from);
        Vec3 delta = to.subtract(from);
        return BlockGetter.traverseBlocks(from, to, level, (world, pos) -> {
            if (!world.hasChunkAt(pos)) return Boolean.FALSE;
            double enter = 0, exit = 1;
            double[] start = {from.x, from.y, from.z};
            double[] direction = {delta.x, delta.y, delta.z};
            int[] cell = {pos.getX(), pos.getY(), pos.getZ()};
            for (int axis = 0; axis < 3; axis++) {
                if (Math.abs(direction[axis]) < 1e-12) {
                    if (start[axis] < cell[axis] || start[axis] > cell[axis] + 1) return null;
                } else {
                    double a = (cell[axis] - start[axis]) / direction[axis];
                    double b = (cell[axis] + 1 - start[axis]) / direction[axis];
                    enter = Math.max(enter, Math.min(a, b));
                    exit = Math.min(exit, Math.max(a, b));
                }
            }
            if (exit - enter < 1e-9) return null; // Ignore cells merely touched at an edge.
            var fluid = world.getFluidState(pos);
            if (!fluid.is(FluidTags.WATER)) return Boolean.FALSE;
            double highestY = Math.max(from.y + delta.y * enter, from.y + delta.y * exit);
            return highestY <= pos.getY() + fluid.getHeight(world, pos) + 1e-7 ? null : Boolean.FALSE;
        }, world -> Boolean.TRUE);
    }

    private AxolotlVortexRules() { }
}
