package com.example.examplemod.survival;

import com.example.examplemod.config.SurvivalConfig;
import com.example.examplemod.devour.DevourRules;
import com.example.examplemod.init.ModTags;
import com.example.examplemod.network.SurvivalPayload;
import com.example.examplemod.scale.PixelScaleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SurvivalRules {
    private static final ResourceLocation SLOW_SURFACE_MODIFIER = id("slow_surface_drag");
    private static final ResourceLocation RAIN_SLOW_MODIFIER = id("rain_slowdown");
    private static final Map<UUID, BlockPos> WEB_POSITIONS = new HashMap<>();
    private static final Map<UUID, Long> WEB_VIBRATION_TICKS = new HashMap<>();
    private static final Map<UUID, NibbleSession> NIBBLE_SESSIONS = new HashMap<>();
    private static final Map<UUID, RainOxygenState> RAIN_OXYGEN_STATES = new HashMap<>();
    private static final Map<UUID, Boolean> CLIMBING_ENABLED = new HashMap<>();

    public static boolean isClimbingEnabled(Entity entity) {
        if (!(entity instanceof Player player)) {
            return true;
        }
        return CLIMBING_ENABLED.getOrDefault(player.getUUID(), Boolean.TRUE);
    }

    public static void setClimbingEnabled(UUID uuid, boolean enabled) {
        CLIMBING_ENABLED.put(uuid, enabled);
    }

    public static boolean canEat(Player player) {
        return player.getFoodData().getFoodLevel() < 20;
    }

    public static boolean giveNectar(Player player) {
        if (player.level().isClientSide || !PixelScaleHelper.isTiny(player) || !canEat(player)) {
            return false;
        }
        player.getFoodData().eat(2, 0.1F);
        player.gameEvent(net.minecraft.world.level.gameevent.GameEvent.EAT);
        player.playSound(SoundEvents.HONEY_DRINK, 0.25F, 1.3F);
        player.swing(InteractionHand.MAIN_HAND, true);
        return true;
    }

    public static boolean isNibbleInput(Player player) {
        if (!PixelScaleHelper.isTiny(player) || !player.isShiftKeyDown() || !canEat(player)) {
            return false;
        }
        HitResult result = player.pick(4.5F, 1.0F, false);
        return result instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK
                && player.level().getBlockState(blockHit.getBlockPos()).is(BlockTags.LEAVES);
    }

    public static boolean shouldSuppressStepEvent(Entity source) {
        return source instanceof Player player && PixelScaleHelper.isTiny(player);
    }

    public static boolean shouldIgnoreTriggerEntity(Entity entity) {
        return entity instanceof Player player && PixelScaleHelper.isTiny(player);
    }

    public static float cameraCollisionProbeRadius(Entity entity, float vanillaRadius) {
        return entity instanceof Player player && PixelScaleHelper.isTiny(player)
                ? Math.max(0.001F, Math.min(vanillaRadius, player.getBbWidth() * 0.5F))
                : vanillaRadius;
    }

    public static boolean isSlowSurface(BlockState state) {
        return state.is(ModTags.SLOW_SURFACES);
    }

    public static boolean shouldUseDevouredDamage(Mob attacker, Entity target) {
        return attacker.getType().is(ModTags.PREDATORY_INSECTS)
                && target instanceof Player player && PixelScaleHelper.isTiny(player);
    }

    public static void handlePayload(Player player, com.example.examplemod.network.SurvivalPayload payload) {
        if (!(player instanceof ServerPlayer serverPlayer) || !PixelScaleHelper.isTiny(serverPlayer) || DevourRules.isCaptured(player)) {
            return;
        }
        switch (payload.action()) {
            case TOGGLE_MOUNT -> toggleMount(serverPlayer, payload.entityId());
            case UPDATE_NIBBLE -> updateNibble(serverPlayer, payload.blockPos());
            case STOP_NIBBLE -> NIBBLE_SESSIONS.remove(serverPlayer.getUUID());
            case SET_CLIMBING -> {
                boolean enabled = payload.entityId() != 0;
                setClimbingEnabled(serverPlayer.getUUID(), enabled);
                serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        enabled ? "message.examplemod.climbing_enabled" : "message.examplemod.climbing_disabled"), true);
            }
        }
    }

    public static void tickPlayer(ServerPlayer player) {
        if (DevourRules.isCaptured(player)) return;
        updateSlimeSlowdown(player);
        tickRain(player);
        tickCobweb(player);
        tickNibble(player);
    }

    public static void clearPlayer(UUID uuid) {
        WEB_POSITIONS.remove(uuid);
        WEB_VIBRATION_TICKS.remove(uuid);
        NIBBLE_SESSIONS.remove(uuid);
        RAIN_OXYGEN_STATES.remove(uuid);
        CLIMBING_ENABLED.remove(uuid);
    }

    private static void toggleMount(ServerPlayer player, int targetEntityId) {
        if (player.isPassenger()) {
            Entity vehicle = player.getVehicle();
            if (vehicle != null && vehicle.getType().is(ModTags.RIDEABLE_LIVESTOCK)) {
                player.stopRiding();
            }
            return;
        }
        Entity target = player.level().getEntity(targetEntityId);
        if (!isValidMountTarget(player, target)) {
            return;
        }
        player.startRiding(target);
    }

    public static boolean isValidMountTarget(Player player, Entity target) {
        if (DevourRules.isCaptured(player) || target == null || !target.getType().is(ModTags.RIDEABLE_LIVESTOCK)
                || target == player || !target.isAlive()
                || player.distanceToSqr(target) > 4.5D * 4.5D
                || !player.hasLineOfSight(target)) {
            return false;
        }
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = start.add(look.scale(4.5D));
        AABB searchBox = player.getBoundingBox().expandTowards(look.scale(4.5D)).inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, start, end, searchBox,
                candidate -> candidate.isPickable() && !candidate.isSpectator(), 4.5D * 4.5D);
        return hit != null && hit.getEntity() == target;
    }

    private static void updateSlimeSlowdown(ServerPlayer player) {
        boolean onSlowSurface = player.onGround()
                && isSlowSurface(player.level().getBlockState(player.getOnPos()));
        updateSpeedModifier(player, SLOW_SURFACE_MODIFIER, onSlowSurface, -0.5D);
    }

    private static void tickRain(ServerPlayer player) {
        boolean inFluid = player.isInWaterOrBubble() || player.isInLava()
                || player.isEyeInFluid(FluidTags.WATER);
        boolean wet = !inFluid && player.level() instanceof ServerLevel level
                && level.isRainingAt(player.blockPosition());
        tickRain(player, wet, inFluid);
    }

    public static void tickRain(Player player, boolean wet, boolean inFluid) {
        updateSpeedModifier(player, RAIN_SLOW_MODIFIER, wet, -0.15D);

        UUID uuid = player.getUUID();
        if (!player.isAlive() || player.isRemoved()) {
            RAIN_OXYGEN_STATES.remove(uuid);
            return;
        }

        if (inFluid) {
            RAIN_OXYGEN_STATES.remove(uuid);
            return;
        }

        if (wet) {
            RainOxygenState state = RAIN_OXYGEN_STATES.computeIfAbsent(uuid,
                    id -> new RainOxygenState(Math.max(0, Math.min(player.getAirSupply(), player.getMaxAirSupply())), 0));
            state.rainTicks++;
            if (state.air > 0) {
                state.air = Math.max(0, state.air - com.example.examplemod.scale.TinyMotionTuning.rainOxygenPerTick);
            } else if (state.rainTicks >= 20) {
                state.rainTicks = 0;
                player.hurt(player.damageSources().drown(), 2.0F);
                if (!player.isAlive()) {
                    RAIN_OXYGEN_STATES.remove(uuid);
                    return;
                }
            }
            player.setAirSupply(state.air);
        } else {
            RainOxygenState state = RAIN_OXYGEN_STATES.get(uuid);
            if (state != null) {
                state.rainTicks = 0;
                state.air = Math.min(player.getMaxAirSupply(), state.air + 4);
                player.setAirSupply(state.air);
                if (state.air >= player.getMaxAirSupply()) {
                    RAIN_OXYGEN_STATES.remove(uuid);
                }
            }
        }
    }

    public static void setRainAirSupply(Player player, int air) {
        RainOxygenState state = RAIN_OXYGEN_STATES.computeIfAbsent(player.getUUID(),
                id -> new RainOxygenState(air, 0));
        state.air = air;
        state.rainTicks = 0;
        player.setAirSupply(air);
    }

    public static int getRainAirSupply(Player player) {
        RainOxygenState state = RAIN_OXYGEN_STATES.get(player.getUUID());
        return state != null ? state.air : player.getAirSupply();
    }

    public static boolean hasRainOxygenState(Player player) {
        return RAIN_OXYGEN_STATES.containsKey(player.getUUID());
    }

    private static void tickCobweb(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = player.blockPosition();
        boolean inWeb = player.getBoundingBox().intersects(new AABB(pos))
                && level.getBlockState(pos).is(Blocks.COBWEB);
        if (!inWeb) {
            leaveCobweb(player);
            return;
        }

        long now = level.getServer().overworld().getGameTime();
        if (shouldEmitCobwebVibration(player, pos, now)) {
            attractNearbySpiders(level, player);
        }
    }

    public static boolean shouldEmitCobwebVibration(Player player, BlockPos pos, long tick) {
        UUID uuid = player.getUUID();
        BlockPos previous = WEB_POSITIONS.put(uuid, pos.immutable());
        if (previous != null && previous.equals(pos)) {
            return false;
        }
        Long lastVibration = WEB_VIBRATION_TICKS.get(uuid);
        if (lastVibration != null && tick - lastVibration < 20L) {
            return false;
        }
        WEB_VIBRATION_TICKS.put(uuid, tick);
        return true;
    }

    public static void leaveCobweb(Player player) {
        WEB_POSITIONS.remove(player.getUUID());
    }

    private static void attractNearbySpiders(ServerLevel level, ServerPlayer player) {
        int range = SurvivalConfig.WEB_VIBRATION_RANGE.get();
        AABB searchBox = player.getBoundingBox().inflate(range);
        double rangeSqr = (double) range * range;
        for (Mob spider : level.getEntitiesOfClass(Mob.class, searchBox,
                mob -> mob.getType().is(ModTags.WEB_ATTRACTED_SPIDERS)
                        && mob.distanceToSqr(player) <= rangeSqr)) {
            spider.setTarget(player);
        }
    }

    public static void updateNibble(Player player, BlockPos pos) {
        if (!(player.level() instanceof ServerLevel level) || !isValidNibbleTarget(player, level, pos)) {
            NIBBLE_SESSIONS.remove(player.getUUID());
            return;
        }
        long now = level.getGameTime();
        NibbleSession session = NIBBLE_SESSIONS.get(player.getUUID());
        if (session == null || !session.dimension.equals(level.dimension())) {
            NIBBLE_SESSIONS.put(player.getUUID(),
                    new NibbleSession(pos.immutable(), now, now + 20L, level.dimension()));
        } else if (!session.pos.equals(pos)) {
            session.pos = pos.immutable();
            session.lastHeartbeat = now;
            session.nextNibbleAt = now + 20L;
        } else {
            session.lastHeartbeat = now;
        }
    }

    public static void tickNibble(Player player) {
        NibbleSession session = NIBBLE_SESSIONS.get(player.getUUID());
        if (session == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!session.dimension.equals(level.dimension())) {
            NIBBLE_SESSIONS.remove(player.getUUID());
            return;
        }
        long now = level.getGameTime();
        if (now - session.lastHeartbeat > 12L || !isValidNibbleTarget(player, level, session.pos)) {
            NIBBLE_SESSIONS.remove(player.getUUID());
            return;
        }
        if (now < session.nextNibbleAt) {
            if (now % 4L == 0L) {
                player.playSound(SoundEvents.GENERIC_EAT, 1.0F, 0.9F + player.getRandom().nextFloat() * 0.2F);
            }
            if (now % 3L == 0L) {
                player.swing(InteractionHand.MAIN_HAND, true);
            }
            return;
        }

        if (level.destroyBlock(session.pos, false, player)) {
            player.getFoodData().eat(2, 0.1F);
            player.gameEvent(net.minecraft.world.level.gameevent.GameEvent.EAT);
            player.playSound(SoundEvents.GENERIC_EAT, 1.0F, 1.0F);
            player.playSound(SoundEvents.PLAYER_BURP, 0.25F, 1.1F);
            player.swing(InteractionHand.MAIN_HAND, true);
            session.nextNibbleAt = now + 20L;
            if (!canEat(player)) {
                NIBBLE_SESSIONS.remove(player.getUUID());
            }
        } else {
            NIBBLE_SESSIONS.remove(player.getUUID());
        }
    }

    public static boolean isValidNibbleTarget(Player player, ServerLevel level, BlockPos pos) {
        if (!player.isShiftKeyDown() || !canEat(player) || !level.hasChunkAt(pos)
                || !level.getBlockState(pos).is(BlockTags.LEAVES)
                || player.distanceToSqr(Vec3.atCenterOf(pos)) > 4.5D * 4.5D) {
            return false;
        }
        HitResult hit = player.pick(4.5F, 1.0F, false);
        return hit instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK
                && blockHit.getBlockPos().equals(pos);
    }

    public static boolean hasNibbleSession(Player player) {
        return NIBBLE_SESSIONS.containsKey(player.getUUID());
    }

    public static BlockPos getNibbleTargetPos(Player player) {
        NibbleSession session = NIBBLE_SESSIONS.get(player.getUUID());
        return session != null ? session.pos : null;
    }

    public static Long getNibbleNextAt(Player player) {
        NibbleSession session = NIBBLE_SESSIONS.get(player.getUUID());
        return session != null ? session.nextNibbleAt : null;
    }

    public static void setNibbleSessionForTesting(Player player, BlockPos pos, long lastHeartbeat, long nextNibbleAt) {
        NIBBLE_SESSIONS.put(player.getUUID(),
                new NibbleSession(pos.immutable(), lastHeartbeat, nextNibbleAt, player.level().dimension()));
    }

    private static void updateSpeedModifier(Player player, net.minecraft.resources.ResourceLocation id,
                                            boolean active, double amount) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        if (active && !speed.hasModifier(id)) {
            speed.addTransientModifier(new AttributeModifier(id, amount,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        } else if (!active && speed.hasModifier(id)) {
            speed.removeModifier(id);
        }
    }

    private static final class NibbleSession {
        private BlockPos pos;
        private long lastHeartbeat;
        private long nextNibbleAt;
        private final ResourceKey<Level> dimension;

        private NibbleSession(BlockPos pos, long lastHeartbeat, long nextNibbleAt, ResourceKey<Level> dimension) {
            this.pos = pos;
            this.lastHeartbeat = lastHeartbeat;
            this.nextNibbleAt = nextNibbleAt;
            this.dimension = dimension;
        }
    }

    private static final class RainOxygenState {
        private int air;
        private int rainTicks;

        private RainOxygenState(int air, int rainTicks) {
            this.air = air;
            this.rainTicks = rainTicks;
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("examplemod", path);
    }

    private SurvivalRules() {
    }
}
