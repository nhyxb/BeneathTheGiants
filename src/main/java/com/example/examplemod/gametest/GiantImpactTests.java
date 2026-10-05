package com.example.examplemod.gametest;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.impact.GiantImpactRules;
import com.example.examplemod.network.GiantImpactPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import com.example.examplemod.scale.PixelScaleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEvent.LivingJumpEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Geometry probes plus registered event and real gravity integration tests. */
@GameTestHolder(ExampleMod.MODID)
@PrefixGameTestTemplate(false)
public final class GiantImpactTests {
    @GameTest(template = "empty")
    public static void currentBodyHeightControlsSourceQualification(GameTestHelper helper) {
        Cow source = cow(helper, new Vec3(4, 2, 4));
        Player target = player(helper, GameType.SURVIVAL, new Vec3(5.8, 2, 4), false);
        helper.assertTrue(GiantImpactRules.applyImpact(source, target, true), "Adult cow is a giant source");
        source.setBaby(true);
        target.setDeltaMovement(Vec3.ZERO);
        helper.assertTrue(source.getBbHeight() < 1, "Natural baby cow is below one block");
        helper.assertTrue(!GiantImpactRules.applyImpact(source, target, true), "Baby cow cannot emit impact");
        source.setBaby(false);
        PixelScaleHelper.applyTinyModifiers(source);
        helper.assertTrue(!GiantImpactRules.applyImpact(source, target, true), "Wand-shrunk adult cannot emit impact");
        PixelScaleHelper.removeTinyModifiers(source);
        source.getAttribute(Attributes.SCALE).setBaseValue(1.0 / source.getBbHeight());
        source.refreshDimensions();
        helper.assertTrue(source.getBbHeight() == 1.0F, "Threshold fixture is exactly one block high; got " + source.getBbHeight());
        helper.assertTrue(GiantImpactRules.applyImpact(source, target, true), "One-block source is included");
        source.getAttribute(Attributes.SCALE).setBaseValue(0.70);
        source.refreshDimensions();
        helper.assertTrue(!GiantImpactRules.applyImpact(source, target, true), "Below-one-block source is excluded");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void feetDistanceHasInclusiveTwoBlockKnockbackAndSixBlockShakeLimit(GameTestHelper helper) {
        Cow source = cow(helper, new Vec3(4, 2, 4));
        Player target = player(helper, GameType.ADVENTURE, new Vec3(4.5, 2, 4), false);
        helper.assertTrue(GiantImpactRules.applyImpact(source, target, true), "Near center has no dead zone");
        helper.assertTrue(target.getDeltaMovement().x > 0, "Near-center player is pushed outwards");
        at(helper, target, new Vec3(6, 2, 4));
        target.setDeltaMovement(Vec3.ZERO);
        helper.assertTrue(GiantImpactRules.applyImpact(source, target, true), "Two-block boundary receives impact");
        helper.assertTrue(target.getDeltaMovement().x > 0 && target.getDeltaMovement().y > 0,
                "Exactly two blocks receives horizontal and upward impulse");
        at(helper, target, new Vec3(6.001, 2, 4));
        target.setDeltaMovement(Vec3.ZERO);
        helper.assertTrue(GiantImpactRules.applyImpact(source, target, true), "Outside knockback still receives shake");
        helper.assertTrue(target.getDeltaMovement().equals(Vec3.ZERO), "Beyond two blocks has no knockback");
        at(helper, target, new Vec3(4, 4.001, 4));
        helper.assertTrue(GiantImpactRules.applyImpact(source, target, true), "Vertical distance inside shake radius");
        helper.assertTrue(target.getDeltaMovement().equals(Vec3.ZERO), "Three-dimensional distance excludes vertical knockback");
        at(helper, target, new Vec3(9.999, 2, 4));
        helper.assertTrue(GiantImpactRules.applyImpact(source, target, true), "Just inside six blocks receives shake");
        at(helper, target, new Vec3(10, 2, 4));
        helper.assertTrue(!GiantImpactRules.applyImpact(source, target, true), "Six-block boundary has zero shake strength");
        at(helper, target, new Vec3(4, 8.001, 4));
        helper.assertTrue(!GiantImpactRules.applyImpact(source, target, true), "Vertical distance beyond six blocks is excluded");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void impulseDirectionStrengthAndHealthAreIndependentOfTinyKnockback(GameTestHelper helper) {
        Cow source = cow(helper, new Vec3(4, 2, 4));
        Player target = player(helper, GameType.SURVIVAL, new Vec3(5.8, 2, 4), false);
        float health = target.getHealth();
        GiantImpactRules.applyImpact(source, target, true);
        Vec3 landing = target.getDeltaMovement();
        helper.assertTrue(Math.abs(landing.x - 0.18) < 0.00001 && Math.abs(landing.y - 0.12) < 0.00001
                && Math.abs(landing.z) < 0.00001, "Landing impulse is radial .18/.12 without tiny knockback scaling");
        helper.assertTrue(target.hurtMarked, "Motion is marked for server synchronization");
        target.setDeltaMovement(Vec3.ZERO);
        GiantImpactRules.applyImpact(source, target, false);
        helper.assertTrue(target.getDeltaMovement().distanceTo(landing.scale(0.6)) < 0.00001,
                "Jump strength is 60 percent of landing");
        helper.assertTrue(target.getHealth() == health, "Impact does not deal additional damage");
        at(helper, target, new Vec3(4, 2, 4));
        target.setDeltaMovement(Vec3.ZERO);
        GiantImpactRules.applyImpact(source, target, true);
        Vec3 first = target.getDeltaMovement();
        target.setDeltaMovement(Vec3.ZERO);
        GiantImpactRules.applyImpact(source, target, true);
        helper.assertTrue(first.lengthSqr() > 0 && first.equals(target.getDeltaMovement()),
                "Coincident feet use a finite deterministic fallback direction");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void creativeSpectatorAndRidingTargetsAreImmune(GameTestHelper helper) {
        Cow source = cow(helper, new Vec3(4, 2, 4));
        for (GameType mode : new GameType[]{GameType.CREATIVE, GameType.SPECTATOR}) {
            Player target = player(helper, mode, new Vec3(5.8, 2, 4), false);
            helper.assertTrue(!GiantImpactRules.applyImpact(source, target, true), mode + " is immune");
            helper.assertTrue(target.getDeltaMovement().equals(Vec3.ZERO), mode + " motion unchanged");
        }
        Player rider = player(helper, GameType.SURVIVAL, new Vec3(5.8, 2, 4), false);
        helper.assertTrue(rider.startRiding(source, true), "Riding fixture mounts source");
        helper.assertTrue(!GiantImpactRules.applyImpact(source, rider, true), "Source passenger is immune");
        rider.stopRiding();
        Cow vehicle = cow(helper, new Vec3(4, 2, 6));
        source.startRiding(vehicle, true);
        helper.assertTrue(!GiantImpactRules.applyImpact(source, rider, true), "Passenger cannot be an impact source");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wallAndFloorCollisionShapesBlockPropagation(GameTestHelper helper) {
        Cow source = cow(helper, new Vec3(4.5, 2, 4.5));
        Player target = player(helper, GameType.SURVIVAL, new Vec3(6.3, 2, 4.5), false);
        helper.setBlock(new BlockPos(5, 2, 4), Blocks.STONE);
        helper.assertTrue(!GiantImpactRules.applyImpact(source, target, true), "Solid wall blocks foot-level impact");
        helper.setBlock(new BlockPos(5, 2, 4), Blocks.AIR);
        helper.assertTrue(GiantImpactRules.applyImpact(source, target, true), "Removing wall restores propagation");
        at(helper, target, new Vec3(4.5, 4, 4.5));
        target.setDeltaMovement(Vec3.ZERO);
        helper.setBlock(new BlockPos(4, 3, 4), Blocks.STONE);
        helper.assertTrue(!GiantImpactRules.applyImpact(source, target, true), "Solid floor blocks impact between levels");
        helper.assertTrue(target.getDeltaMovement().equals(Vec3.ZERO), "Blocked target keeps velocity");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void registeredJumpEventDeduplicatesAndIgnoresNonPlayers(GameTestHelper helper) {
        Cow source = cow(helper, new Vec3(4, 2, 4));
        Player target = player(helper, GameType.SURVIVAL, new Vec3(5.8, 2, 4), true);
        Cow nonPlayer = cow(helper, new Vec3(4, 2, 5.8));
        PixelScaleHelper.applyTinyModifiers(nonPlayer);
        GiantImpactRules.clear(source);
        source.jumpFromGround();
        Vec3 first = target.getDeltaMovement();
        helper.assertTrue(first.x > 0 && first.y > 0, "Registered jump listener applies actual event");
        NeoForge.EVENT_BUS.post(new LivingJumpEvent(source));
        helper.assertTrue(first.equals(target.getDeltaMovement()), "Duplicate jump in same tick applies once");
        helper.assertTrue(nonPlayer.getDeltaMovement().equals(Vec3.ZERO), "Tiny non-player is not an impact target");
        helper.runAtTickTime(2, () -> {
            at(helper, target, new Vec3(5.8, 2, 4));
            target.setDeltaMovement(Vec3.ZERO);
            NeoForge.EVENT_BUS.post(new LivingJumpEvent(source));
            helper.assertTrue(target.getDeltaMovement().x > 0, "Jump in later world tick can apply again");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void realGravityLandingTriggersOnceThenWalkingAndRestStayQuiet(GameTestHelper helper) {
        for (int x = 1; x <= 10; x++) for (int z = 1; z <= 10; z++)
            helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        Cow source = new Cow(EntityType.COW, helper.getLevel()) {
            @Override public boolean isControlledByLocalInstance() { return true; }
        };
        source.setNoAi(true);
        at(helper, source, new Vec3(4, 5, 4));
        helper.getLevel().addFreshEntity(source);
        Player target = player(helper, GameType.SURVIVAL, new Vec3(5.8, 2, 4), true);
        float health = target.getHealth();
        boolean[] sawDescending = {false};
        int[] impulses = {0};
        int[] groundedTicks = {0};
        helper.onEachTick(() -> {
            if (source.getDeltaMovement().y < -0.01) sawDescending[0] = true;
            if (target.getDeltaMovement().y > 0.05 && target.getDeltaMovement().x > 0.05) impulses[0]++;
            at(helper, target, new Vec3(5.8, 2, 4));
            target.setDeltaMovement(Vec3.ZERO);
            if (source.onGround()) {
                groundedTicks[0]++;
                // Real vanilla movement, first stationary then a short supported walk.
                // Keep vanilla downward velocity: zeroing Y makes the next stationary
                // move lose onGround even though the floor still supports the cow.
                Vec3 motion = source.getDeltaMovement();
                source.setDeltaMovement(0, motion.y,
                        groundedTicks[0] > 4 && groundedTicks[0] < 10 ? 0.015 : 0);
            }
        });
        helper.runAtTickTime(55, () -> {
            helper.assertTrue(sawDescending[0] && source.onGround(),
                    "Source really fell and landed through world ticks: position=" + source.position()
                            + ", velocity=" + source.getDeltaMovement() + ", onGround=" + source.onGround()
                            + ", alive=" + source.isAlive() + ", removed=" + source.isRemoved()
                            + ", sawDescending=" + sawDescending[0] + ", impulses=" + impulses[0]
                            + ", groundedTicks=" + groundedTicks[0]);
            helper.assertTrue(groundedTicks[0] > 15, "Observe multiple resting and walking ticks after landing");
            helper.assertTrue(impulses[0] == 1, "One landing impulse, no repeated walking/rest impulses; got " + impulses[0]);
            helper.assertTrue(target.getHealth() == health, "Landing at separated feet causes no extra damage");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void landingTransitionDeduplicatesWithinOneTick(GameTestHelper helper) {
        Cow source = cow(helper, new Vec3(4, 2.4, 4));
        Player target = player(helper, GameType.SURVIVAL, new Vec3(5.8, 2, 4), true);
        GiantImpactRules.clear(source);
        // Initial on-ground state is initialization, not a fall.
        source.setOnGround(true);
        source.setDeltaMovement(new Vec3(0, -0.4, 0));
        GiantImpactRules.recordPreTick(source);
        GiantImpactRules.postTick(source);
        helper.assertTrue(target.getDeltaMovement().equals(Vec3.ZERO), "Initialization cannot create a landing");
        source.setOnGround(false);
        source.setDeltaMovement(new Vec3(0, -0.4, 0));
        GiantImpactRules.recordPreTick(source);
        at(helper, source, new Vec3(4, 2, 4));
        source.setOnGround(true);
        GiantImpactRules.postTick(source);
        Vec3 first = target.getDeltaMovement();
        helper.assertTrue(first.x > 0 && first.y > 0, "Downward air-to-ground transition applies impact");
        at(helper, source, new Vec3(4, 2.4, 4));
        source.setOnGround(false);
        source.setDeltaMovement(new Vec3(0, -0.4, 0));
        GiantImpactRules.recordPreTick(source);
        at(helper, source, new Vec3(4, 2, 4));
        source.setOnGround(true);
        GiantImpactRules.postTick(source);
        helper.assertTrue(first.equals(target.getDeltaMovement()), "Duplicate landing transition in same tick applies once");
        GiantImpactRules.clear(source);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void swimmingClimbingAndDeadSourcesCannotEmit(GameTestHelper helper) {
        Cow source = cow(helper, new Vec3(4.5, 2, 4.5));
        Player target = player(helper, GameType.SURVIVAL, new Vec3(6.3, 2, 4.5), false);
        source.setSwimming(true);
        helper.assertTrue(!GiantImpactRules.applyImpact(source, target, true), "Swimming source is excluded");
        source.setSwimming(false);
        BlockPos ladderPos = source.blockPosition();
        helper.getLevel().setBlockAndUpdate(ladderPos.south(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(ladderPos,
                Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
        // applyImpact previously read AIR; moving across block positions invalidates
        // vanilla Entity's cached in-block state before the climbability probe.
        Vec3 ladderFeet = source.position();
        source.setPos(ladderFeet.x + 1, ladderFeet.y, ladderFeet.z);
        source.setPos(ladderFeet.x, ladderFeet.y, ladderFeet.z);
        helper.assertTrue(source.onClimbable(), "Supported ladder fixture is climbable: source="
                + source.position() + ", block=" + helper.getLevel().getBlockState(ladderPos));
        helper.assertTrue(!GiantImpactRules.applyImpact(source, target, true), "Climbing source is excluded");
        helper.getLevel().setBlockAndUpdate(ladderPos, Blocks.AIR.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(ladderPos.south(), Blocks.AIR.defaultBlockState());
        source.setPos(ladderFeet.x + 1, ladderFeet.y, ladderFeet.z);
        source.setPos(ladderFeet.x, ladderFeet.y, ladderFeet.z);
        source.setHealth(0);
        helper.assertTrue(!GiantImpactRules.applyImpact(source, target, true), "Dead source is excluded");
        helper.assertTrue(target.getDeltaMovement().equals(Vec3.ZERO), "Excluded sources leave target motion alone");
        Cow alive = cow(helper, new Vec3(4.5, 2, 4.5));
        target.setHealth(0);
        helper.assertTrue(!GiantImpactRules.applyImpact(alive, target, true), "Dead player is excluded");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void teleportAndHorizontalGroundTransitionDoNotCreateLanding(GameTestHelper helper) {
        Cow source = cow(helper, new Vec3(4, 9, 4));
        Player target = player(helper, GameType.SURVIVAL, new Vec3(5.8, 2, 4), true);
        source.setOnGround(false);
        GiantImpactRules.recordPreTick(source);
        at(helper, source, new Vec3(4, 2, 4));
        source.setOnGround(true);
        GiantImpactRules.postTick(source);
        helper.assertTrue(target.getDeltaMovement().equals(Vec3.ZERO), "Teleport to ground cannot impersonate falling");
        GiantImpactRules.clear(source);
        source.setOnGround(false);
        GiantImpactRules.recordPreTick(source);
        at(helper, source, new Vec3(4.1, 2, 4));
        source.setOnGround(true);
        GiantImpactRules.postTick(source);
        helper.assertTrue(target.getDeltaMovement().equals(Vec3.ZERO), "Ground transition without descent cannot emit");
        GiantImpactRules.clear(source);
        at(helper, source, new Vec3(4, 2.4, 4));
        source.setDeltaMovement(Vec3.ZERO);
        source.setOnGround(false);
        GiantImpactRules.recordPreTick(source);
        at(helper, source, new Vec3(4, 2, 4));
        source.setOnGround(true);
        GiantImpactRules.postTick(source);
        helper.assertTrue(target.getDeltaMovement().equals(Vec3.ZERO),
                "Even a small 0.4-block teleport without downward velocity cannot create landing");
        GiantImpactRules.clear(source);
        at(helper, source, new Vec3(4, 2.02, 4));
        source.setDeltaMovement(new Vec3(0, -0.02, 0));
        source.setOnGround(false);
        GiantImpactRules.recordPreTick(source);
        at(helper, source, new Vec3(4, 2, 4));
        source.setOnGround(true);
        GiantImpactRules.postTick(source);
        helper.assertTrue(target.getDeltaMovement().equals(Vec3.ZERO),
                "A 0.02-block ground jitter with matching velocity is too small for landing impact");
        GiantImpactRules.clear(source);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void teleportEventClearsEvenVelocityConsistentFall(GameTestHelper helper) {
        Cow source = cow(helper, new Vec3(4, 2.4, 4));
        Player target = player(helper, GameType.SURVIVAL, new Vec3(5.8, 2, 4), true);
        source.setOnGround(false);
        source.setDeltaMovement(0, -0.4, 0);
        GiantImpactRules.recordPreTick(source);
        Vec3 destination = helper.absoluteVec(new Vec3(4, 2, 4));
        NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.EntityTeleportEvent.TeleportCommand(
                source, destination.x, destination.y, destination.z));
        source.setPos(destination);
        source.setOnGround(true);
        helper.assertTrue(!GiantImpactRules.postTick(source), "Teleport event invalidates the falling snapshot");
        helper.assertTrue(target.getDeltaMovement().equals(Vec3.ZERO), "Velocity-consistent teleport cannot cause impact");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void downstreamIntensitySurvivesPayloadCodec(GameTestHelper helper) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            for (float intensity : new float[]{0.0F, 0.6F, 1.0F}) {
                GiantImpactPayload.STREAM_CODEC.encode(buffer, new GiantImpactPayload(intensity, 3.5F, 17));
                GiantImpactPayload decoded = GiantImpactPayload.STREAM_CODEC.decode(buffer);
                helper.assertTrue(decoded.intensity() == intensity, "Downstream codec preserves intensity " + intensity);
                helper.assertTrue(decoded.amplitude() == 3.5F, "Codec preserves server non-default amplitude");
                helper.assertTrue(decoded.duration() == 17, "Codec preserves server non-default duration");
                helper.assertTrue(decoded.type().equals(GiantImpactPayload.TYPE), "Payload retains its registered type");
            }
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    private static Cow cow(GameTestHelper helper, Vec3 local) {
        Cow cow = helper.spawn(EntityType.COW, local);
        cow.setNoAi(true);
        cow.setNoGravity(true);
        return cow;
    }

    private static Player player(GameTestHelper helper, GameType mode, Vec3 local, boolean addToWorld) {
        Player player = helper.makeMockPlayer(mode);
        PixelScaleHelper.ensurePlayerMini(player);
        player.setNoGravity(true);
        at(helper, player, local);
        if (addToWorld) helper.getLevel().addFreshEntity(player);
        return player;
    }

    private static void at(GameTestHelper helper, net.minecraft.world.entity.Entity entity, Vec3 local) {
        Vec3 pos = helper.absoluteVec(local);
        entity.setPos(pos.x, pos.y, pos.z);
    }

    private GiantImpactTests() { }
}
