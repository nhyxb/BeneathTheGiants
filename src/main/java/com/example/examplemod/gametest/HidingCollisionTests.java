package com.example.examplemod.gametest;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.hiding.HidingRules;
import com.example.examplemod.collision.SelectiveCollisionRules;
import com.example.examplemod.scale.PixelScaleHelper;
import com.example.examplemod.scale.ScaleEvents;
import com.example.examplemod.devour.DevourPlayerAccess;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Lifecycle event probes do not claim real logout, dimension transfer or network coverage. */
@GameTestHolder(ExampleMod.MODID)
@PrefixGameTestTemplate(false)
public final class HidingCollisionTests {
    @GameTest(template = "empty")
    public static void hidingOnlyAppliesToSurvivalAndAdventure(GameTestHelper h) {
        for (var mode : net.minecraft.world.level.GameType.values()) {
            Player p = h.makeMockPlayer(mode);
            PixelScaleHelper.ensurePlayerMini(p);
            p.setNoGravity(true);
            p.setPos(h.absoluteVec(new Vec3(4.5, 2, 3)));
            hide(p);
            h.assertTrue(HidingRules.isHidden(p) == (mode == net.minecraft.world.level.GameType.SURVIVAL
                    || mode == net.minecraft.world.level.GameType.ADVENTURE), "Hiding qualification: " + mode);
            HidingRules.clear(p);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void hidingBoundaryRotationAndDuplicateTick(GameTestHelper h) {
        Player p = player(h);
        for (int t = 1; t <= 59; t++) {
            p.setYRot(t * 7);
            HidingRules.tick(p, t);
            HidingRules.tick(p, t);
        }
        h.assertTrue(HidingRules.getHideTicks(p) == 59 && !HidingRules.isHidden(p), "59 distinct ticks, rotation and duplicate calls cannot hide early");
        HidingRules.tick(p, 60);
        h.assertTrue(HidingRules.getHideTicks(p) == 60 && HidingRules.isHidden(p), "Sixtieth consecutive tick hides");
        p.setShiftKeyDown(false);
        HidingRules.tick(p, 61);
        h.assertTrue(!HidingRules.isHidden(p) && HidingRules.getHideTicks(p) == 0, "Release clears progress");
        HidingRules.clear(p);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void cumulativeMicroMovementUsesFixedAnchor(GameTestHelper h) {
        Player p = player(h);
        Vec3 anchor = p.position();
        HidingRules.tick(p, 1);
        p.setPos(anchor.add(0.00006, 0, 0));
        HidingRules.tick(p, 2);
        h.assertTrue(HidingRules.getHideTicks(p) == 2, "First movement below tolerance retains progress");
        p.setPos(anchor.add(0.00012, 0, 0));
        HidingRules.tick(p, 3);
        h.assertTrue(HidingRules.getHideTicks(p) <= 1 && !HidingRules.isHidden(p), "Accumulated displacement above tolerance resets despite each step being small");
        HidingRules.clear(p);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void releaseAndRepressInSameTickCannotRetainHiding(GameTestHelper h) {
        Player p = player(h);
        hide(p);
        h.assertTrue(HidingRules.isHidden(p), "Fixture is hidden before release");
        p.setShiftKeyDown(false);
        h.assertTrue(!HidingRules.isHidden(p) && HidingRules.getHideTicks(p) == 0,
                "Release immediately clears hiding without another tick");
        p.setShiftKeyDown(true);
        h.assertTrue(!HidingRules.isHidden(p), "Repress in the same tick cannot restore old progress");
        HidingRules.tick(p, 60);
        h.assertTrue(HidingRules.getHideTicks(p) == 1 && !HidingRules.isHidden(p),
                "Fresh crouch starts at one even when its clock equals the old final tick");
        HidingRules.clear(p);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void hiddenTargetingRangeCurrentTargetAndVanillaFloor(GameTestHelper h) {
        Player p = player(h);
        Cow observer = cow(h);
        observer.setPos(p.position().add(8, 0, 0));
        TargetingConditions conditions = TargetingConditions.forNonCombat().range(20);
        h.assertTrue(conditions.test(observer, p), "Unhidden crouching player at eight blocks remains visible with real line of sight");
        hide(p);
        h.assertTrue(!conditions.test(observer, p), "Hidden new target at eight blocks fails actual targeting range");
        observer.setTarget(p);
        h.assertTrue(conditions.test(observer, p), "Current target is still visible");
        observer.setTarget(null);
        observer.setPos(p.position().add(1.9, 0, 0));
        h.assertTrue(conditions.test(observer, p), "Vanilla two-block minimum remains effective");
        HidingRules.clear(p);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void hidingPreservesFriendlyRetaliation(GameTestHelper h) {
        Player p = player(h);
        Cow observer = cow(h);
        observer.setPos(p.position().add(4, 0, 0));
        var goal = ScaleEvents.getOrAttachRetaliateGoal(observer);
        goal.setRetaliateTarget(p);
        int remaining = goal.getTicksRemaining();
        hide(p);
        h.assertTrue(goal.getRetaliateTarget() == p && goal.getTicksRemaining() == remaining && goal.canUse(), "Hiding cannot erase or shorten friendly retaliation memory");
        HidingRules.clear(p);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void passengerCaptureAndDeathResetHiding(GameTestHelper h) {
        Player p = player(h);
        Cow mount = cow(h);
        hide(p);
        p.startRiding(mount, true);
        HidingRules.tick(p, 61);
        h.assertTrue(!HidingRules.isHidden(p), "Passenger cannot hide");
        p.stopRiding();
        hide(p);
        ((DevourPlayerAccess) p).examplemod$setDevourState(mount.getId(), .5F);
        HidingRules.tick(p, 61);
        h.assertTrue(!HidingRules.isHidden(p), "F21 metadata alone resets hiding");
        ((DevourPlayerAccess) p).examplemod$setDevourState(-1, 0);
        hide(p);
        p.setHealth(0);
        HidingRules.tick(p, 61);
        h.assertTrue(!HidingRules.isHidden(p), "Dead player cannot remain hidden");
        HidingRules.clear(p);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void lifecycleEventProbesClearHiding(GameTestHelper h) {
        Player p = player(h);
        hide(p);
        NeoForge.EVENT_BUS.post(new EntityTeleportEvent.TeleportCommand(p, p.getX() + 10, p.getY(), p.getZ()));
        h.assertTrue(!HidingRules.isHidden(p), "Teleport event clears immediately");
        hide(p);
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(p));
        h.assertTrue(!HidingRules.isHidden(p), "Logout event clears immediately");
        hide(p);
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(p, Level.OVERWORLD, Level.NETHER));
        h.assertTrue(!HidingRules.isHidden(p), "Dimension event clears immediately");
        HidingRules.clear(p);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void realWorldTicksReachHiding(GameTestHelper h) {
        Player p = player(h);
        HidingRules.clear(p);
        h.getLevel().addFreshEntity(p);
        h.runAfterDelay(59, () -> {
            h.assertTrue(!HidingRules.isHidden(p), "Real world ticking must not hide before sixty ticks");
            h.runAfterDelay(2, () -> {
                h.assertTrue(HidingRules.isHidden(p), "Production tick listener hides a still crouching player after sixty real ticks");
                HidingRules.clear(p);
                p.discard();
                h.succeed();
            });
        });
    }

    @GameTest(template = "empty")
    public static void actualMovePassesLeavesAndBothFenceFamilies(GameTestHelper h) {
        Player p = player(h);
        p.setShiftKeyDown(false);
        for (Block block : new Block[]{Blocks.OAK_LEAVES, Blocks.OAK_FENCE, Blocks.NETHER_BRICK_FENCE, Blocks.OAK_FENCE_GATE}) {
            obstacle(h, block);
            placeAtStart(h, p);
            p.move(MoverType.SELF, new Vec3(0, 0, 3));
            h.assertTrue(p.getZ() > h.absoluteVec(new Vec3(4.5, 2, 5.5)).z, "Actual player move passes " + block);
            h.assertTrue(!p.horizontalCollision && !p.onClimbable(), "Passable block cannot spuriously trigger wall climbing: " + block);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualTravelPassesLeavesAndFences(GameTestHelper h) {
        Player p = player(h);
        p.setShiftKeyDown(false);
        for (Block block : new Block[]{Blocks.OAK_LEAVES, Blocks.OAK_FENCE, Blocks.NETHER_BRICK_FENCE, Blocks.OAK_FENCE_GATE}) {
            obstacle(h, block);
            placeAtStart(h, p);
            p.setDeltaMovement(0, 0, 3);
            p.travel(Vec3.ZERO);
            h.assertTrue(p.getZ() > h.absoluteVec(new Vec3(4.5, 2, 5.5)).z, "Server authoritative travel passes " + block);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void sneakingPassesSupportedObstaclesButStopsAtRealCliff(GameTestHelper h) {
        Player p = player(h);
        for (Block block : new Block[]{Blocks.OAK_LEAVES, Blocks.OAK_FENCE, Blocks.NETHER_BRICK_FENCE, Blocks.OAK_FENCE_GATE}) {
            for (int x = 1; x <= 10; x++) for (int z = 1; z <= 10; z++)
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            obstacle(h, block);
            placeAtStart(h, p);
            p.setShiftKeyDown(true);
            p.setOnGround(true);
            p.move(MoverType.SELF, new Vec3(0, 0, 3));
            h.assertTrue(p.getZ() > h.absoluteVec(new Vec3(4.5, 2, 5.5)).z,
                    "Real crouching move passes supported obstacle: " + block);
            h.assertTrue(!h.getLevel().noCollision(p, p.getBoundingBox().move(0, -0.0625, 0)),
                    "Crouching destination retains actual stone support: " + block);

            for (int x = 1; x <= 10; x++) for (int z = 1; z <= 10; z++)
                h.setBlock(new BlockPos(x, 1, z), Blocks.AIR);
            h.setBlock(new BlockPos(4, 1, 3), Blocks.STONE);
            placeAtStart(h, p);
            p.setOnGround(true);
            p.move(MoverType.SELF, new Vec3(0, 0, 3));
            h.assertTrue(p.getZ() < h.absoluteVec(new Vec3(4.5, 2, 4.1)).z,
                    "Passable obstacle cannot substitute for missing cliff support: " + block);
            h.assertTrue(!h.getLevel().noCollision(p, p.getBoundingBox().move(0, -0.0625, 0)),
                    "Sneak prevention leaves player above real starting support: " + block);
            for (int y = 2; y <= 4; y++) h.setBlock(new BlockPos(4, y, 4), Blocks.AIR);
            h.setBlock(new BlockPos(4, 1, 3), Blocks.AIR);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualMoveRetainsStoneAndWallCollision(GameTestHelper h) {
        Player p = player(h);
        p.setShiftKeyDown(false);
        for (Block block : new Block[]{Blocks.STONE, Blocks.COBBLESTONE_WALL}) {
            obstacle(h, block);
            placeAtStart(h, p);
            p.move(MoverType.SELF, new Vec3(0, 0, 3));
            h.assertTrue(p.getZ() < h.absoluteVec(new Vec3(4.5, 2, 4.6)).z && p.horizontalCollision, "Actual move remains blocked by " + block);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void normalAndWandScaledCowsRetainCollision(GameTestHelper h) {
        for (boolean tiny : new boolean[]{false, true}) {
            Cow cow = cow(h);
            if (tiny) PixelScaleHelper.applyTinyModifiers(cow);
            for (Block block : new Block[]{Blocks.OAK_LEAVES, Blocks.OAK_FENCE, Blocks.NETHER_BRICK_FENCE, Blocks.OAK_FENCE_GATE}) {
                obstacle(h, block);
                placeAtStart(h, cow);
                cow.move(MoverType.SELF, new Vec3(0, 0, 3));
                h.assertTrue(cow.getZ() < h.absoluteVec(new Vec3(4.5, 2, 4.6)).z, "Cow movement blocked; wand tiny=" + tiny + ", block=" + block);
                h.assertTrue(SelectiveCollisionRules.overrideShape(cow, block.defaultBlockState()) == null, "Non-player shape override remains absent");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyContextAndPlayerOutlineColliderRaysRemainSolid(GameTestHelper h) {
        Player p = player(h);
        BlockPos pos = h.absolutePos(new BlockPos(4, 2, 4));
        for (Block block : new Block[]{Blocks.OAK_LEAVES, Blocks.OAK_FENCE, Blocks.NETHER_BRICK_FENCE, Blocks.OAK_FENCE_GATE}) {
            obstacle(h, block);
            var state = h.getLevel().getBlockState(pos);
            h.assertTrue(!state.getCollisionShape(h.getLevel(), pos, CollisionContext.empty()).isEmpty(), "Empty context retains original collision shape");
            for (ClipContext.Block mode : new ClipContext.Block[]{ClipContext.Block.OUTLINE, ClipContext.Block.COLLIDER}) {
                Vec3 from = h.absoluteVec(new Vec3(4.5, 2.5, 3));
                Vec3 to = h.absoluteVec(new Vec3(4.5, 2.5, 6));
                var hit = h.getLevel().clip(new ClipContext(from, to, mode, ClipContext.Fluid.NONE, p));
                h.assertTrue(hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos), "Player-context ray still hits " + block + " in " + mode);
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void leafInteriorIsSafeButStoneStillSuffocates(GameTestHelper h) {
        Player p = player(h);
        obstacle(h, Blocks.OAK_LEAVES);
        p.setPos(h.absoluteVec(new Vec3(4.5, 2.4, 4.5)));
        h.assertTrue(!p.isInWall() && h.getLevel().noCollision(p, p.getBoundingBox()), "Leaf interior neither suffocates nor collides for tiny player");
        obstacle(h, Blocks.STONE);
        h.assertTrue(p.isInWall() && !h.getLevel().noCollision(p, p.getBoundingBox()), "Actual stone interior remains suffocating and solid");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void openAndClosedGatesPassRealMoveAndTravel(GameTestHelper h) {
        Player p = player(h);
        p.setShiftKeyDown(false);
        for (boolean open : new boolean[]{false, true}) {
            for (Block gate : new Block[]{Blocks.OAK_FENCE_GATE, Blocks.SPRUCE_FENCE_GATE,
                    Blocks.BIRCH_FENCE_GATE, Blocks.JUNGLE_FENCE_GATE, Blocks.ACACIA_FENCE_GATE,
                    Blocks.DARK_OAK_FENCE_GATE, Blocks.MANGROVE_FENCE_GATE, Blocks.CHERRY_FENCE_GATE,
                    Blocks.BAMBOO_FENCE_GATE, Blocks.CRIMSON_FENCE_GATE, Blocks.WARPED_FENCE_GATE}) {
                for (int y = 2; y <= 4; y++) h.setBlock(new BlockPos(4, y, 4), gate.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.FenceGateBlock.OPEN, open));
                placeAtStart(h, p);
                p.move(MoverType.SELF, new Vec3(0, 0, 3));
                h.assertTrue(p.getZ() > h.absoluteVec(new Vec3(4.5, 2, 5.5)).z,
                        "Real move passes gate " + gate + " open=" + open);
                placeAtStart(h, p);
                p.setDeltaMovement(0, 0, 3);
                p.travel(Vec3.ZERO);
                h.assertTrue(p.getZ() > h.absoluteVec(new Vec3(4.5, 2, 5.5)).z,
                        "Real travel passes gate " + gate + " open=" + open);
            }
        }
        h.succeed();
    }

    private static void hide(Player p) {
        HidingRules.clear(p);
        p.setShiftKeyDown(true);
        for (int t = 1; t <= 60; t++) HidingRules.tick(p, t);
    }
    private static Player player(GameTestHelper h) {
        Player p = new TestPlayer(h.getLevel());
        PixelScaleHelper.ensurePlayerMini(p);
        p.setNoGravity(true);
        p.setShiftKeyDown(true);
        p.setPos(h.absoluteVec(new Vec3(4.5, 2, 3)));
        return p;
    }
    private static Cow cow(GameTestHelper h) {
        Cow cow = EntityType.COW.create(h.getLevel());
        h.assertTrue(cow != null, "Cow fixture created");
        cow.setNoAi(true);
        cow.setNoGravity(true);
        return cow;
    }
    private static void obstacle(GameTestHelper h, Block block) {
        for (int y = 2; y <= 4; y++) h.setBlock(new BlockPos(4, y, 4), block);
    }
    private static void placeAtStart(GameTestHelper h, net.minecraft.world.entity.Entity entity) {
        entity.setPos(h.absoluteVec(new Vec3(4.5, 2, 3)));
        entity.setDeltaMovement(Vec3.ZERO);
    }
    /** Local authority enables vanilla Player.travel on the server without injecting collision flags. */
    private static final class TestPlayer extends Player {
        TestPlayer(Level level) { super(level, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "hiding-collision-test")); }
        @Override public boolean isCreative() { return false; }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isLocalPlayer() { return true; }
    }
    private HidingCollisionTests() { }
}
