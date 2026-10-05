package com.example.examplemod.gametest;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.scale.PixelScaleHelper;
import com.example.examplemod.collision.SelectiveCollisionRules;
import com.example.examplemod.devour.DevourPlayerAccess;
import com.example.examplemod.torch.TorchShapes;
import com.example.examplemod.torch.TorchContactRules;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Server authority fixtures exercise movement; they do not simulate network clients. */
@GameTestHolder(ExampleMod.MODID)
@PrefixGameTestTemplate(false)
public final class TorchFeatureTests {
    private static final BlockPos TORCH = new BlockPos(4, 2, 4);

    @GameTest(template = "empty")
    public static void sixTorchVariantsKeepVanillaContextsAndRays(GameTestHelper h) {
        Player p = player(h);
        for (var block : new net.minecraft.world.level.block.Block[]{Blocks.TORCH, Blocks.SOUL_TORCH,
                Blocks.REDSTONE_TORCH, Blocks.WALL_TORCH, Blocks.SOUL_WALL_TORCH, Blocks.REDSTONE_WALL_TORCH}) {
            BlockState state = block.defaultBlockState();
            place(h, state);
            BlockPos absolute = h.absolutePos(TORCH);
            h.assertTrue(TorchShapes.collisionShape(state) != null, "Tagged variant has tiny shape: " + block);
            h.assertTrue(state.getCollisionShape(h.getLevel(), absolute, CollisionContext.empty()).isEmpty(),
                    "Empty context retains vanilla empty collision: " + block);
            for (boolean tiny : new boolean[]{false, true}) {
                var cow = EntityType.COW.create(h.getLevel());
                h.assertTrue(cow != null, "Cow fixture exists");
                cow.setNoGravity(true);
                if (tiny) PixelScaleHelper.applyTinyModifiers(cow);
                h.assertTrue(SelectiveCollisionRules.overrideShape(cow, state) == null,
                        "Cow receives no torch collision override, tiny=" + tiny);
                boolean alongX = state.hasProperty(WallTorchBlock.FACING)
                        && state.getValue(WallTorchBlock.FACING).getAxis() == Direction.Axis.Z;
                cow.setPos(h.absoluteVec(alongX ? new Vec3(3, 2, 4.5) : new Vec3(4.5, 2, 3)));
                cow.move(MoverType.SELF, alongX ? new Vec3(3, 0, 0) : new Vec3(0, 0, 3));
                h.assertTrue((alongX ? cow.getX() > h.absoluteVec(new Vec3(5.5, 2, 4.5)).x
                        : cow.getZ() > h.absoluteVec(new Vec3(4.5, 2, 5.5)).z),
                        "Cow actually crosses torch, tiny=" + tiny);
                h.assertTrue(!cow.isOnFire(), "Torch does not ignite non-player");
            }
            AABB outline = state.getShape(h.getLevel(), absolute).bounds();
            Vec3 center = outline.getCenter().add(absolute.getX(), absolute.getY(), absolute.getZ());
            boolean rayAlongZ = state.hasProperty(WallTorchBlock.FACING)
                    && state.getValue(WallTorchBlock.FACING).getAxis() == Direction.Axis.X;
            Vec3 rayOffset = rayAlongZ ? new Vec3(0, 0, .9) : new Vec3(.9, 0, 0);
            var outlineHit = h.getLevel().clip(new ClipContext(center.subtract(rayOffset), center.add(rayOffset),
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
            h.assertTrue(outlineHit.getType() == HitResult.Type.BLOCK && outlineHit.getBlockPos().equals(absolute),
                    "Original outline remains selectable: " + block);
            var colliderHit = h.getLevel().clip(new ClipContext(center.subtract(rayOffset), center.add(rayOffset),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            h.assertTrue(colliderHit.getType() == HitResult.Type.MISS, "Collider ray retains original empty shape");
        }
        h.assertTrue(TorchShapes.collisionShape(Blocks.STONE.defaultBlockState()) == null, "Unlisted block has no override");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void realMoveAndTravelStopAtRodButPassAdjacentAir(GameTestHelper h) {
        place(h, Blocks.TORCH.defaultBlockState());
        Player p = player(h);
        for (boolean travel : new boolean[]{false, true}) {
            p.setPos(h.absoluteVec(new Vec3(4.5, 2, 3.5)));
            p.setDeltaMovement(0, 0, 1);
            if (travel) p.travel(Vec3.ZERO); else p.move(MoverType.SELF, new Vec3(0, 0, 1));
            h.assertTrue(p.horizontalCollision && p.getZ() < h.absoluteVec(new Vec3(4.5, 2, 4.5)).z,
                    "Actual movement stops against thin rod, travel=" + travel);
            h.assertTrue(!TorchContactRules.standingOnHotTorch(p), "Rod side is not the highest supported surface");
            h.assertTrue(!TorchContactRules.tick(p) && p.getRemainingFireTicks() <= 0, "Side collision cannot ignite");
            p.clearFire();
            p.setPos(h.absoluteVec(new Vec3(4.1, 2, 3.5)));
            p.move(MoverType.SELF, new Vec3(0, 0, 1));
            h.assertTrue(!p.horizontalCollision && !TorchContactRules.standingOnHotTorch(p),
                    "Air alongside the same block neither blocks nor ignites");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void gravityTravelLandsOnStandingAndFourWallTops(GameTestHelper h) {
        land(h, Blocks.TORCH.defaultBlockState(), 10.0 / 16);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            land(h, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, facing), 13.0 / 16);
        }
        h.succeed();
    }

    private static void land(GameTestHelper h, BlockState state, double expectedTop) {
        place(h, state);
        VoxelShape shape = TorchShapes.collisionShape(state);
        h.assertTrue(shape != null, "Tiny shape exists");
        AABB top = shape.toAabbs().stream().max(java.util.Comparator.comparingDouble(b -> b.maxY)).orElseThrow();
        h.assertTrue(Math.abs(top.maxY - expectedTop) < 1.0E-6, "Specified top height: " + state);
        Player p = player(h);
        p.setNoGravity(false);
        p.setPos(h.absoluteVec(new Vec3(4 + (top.minX + top.maxX) / 2, 3.5, 4 + (top.minZ + top.maxZ) / 2)));
        for (int i = 0; i < 60 && !p.onGround(); i++) p.travel(Vec3.ZERO);
        h.assertTrue(p.onGround() && Math.abs(p.getY() - (h.absolutePos(TORCH).getY() + expectedTop)) < 1.0E-5,
                "Gravity and authoritative travel land on actual torch top: " + state);
        h.assertTrue(TorchContactRules.tick(p) && p.isOnFire(), "Top contact ignites: " + state);
        p.clearFire();
        // Each facing also exercises its side surface through real entity movement.
        sideMove(h, p, state, top);
        h.assertTrue(p.horizontalCollision && !TorchContactRules.standingOnHotTorch(p), "Side remains solid without qualifying as standing: " + state);
        h.assertTrue(!TorchContactRules.tick(p) && p.getRemainingFireTicks() <= 0, "Leaving the top for the side stops ignition: " + state);
    }

    @GameTest(template = "empty")
    public static void fourWallSidesLowerLedgesAndAirborneTopsDoNotBurn(GameTestHelper h) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockState state = Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, facing);
            place(h, state);
            Player p = player(h);
            VoxelShape shape = TorchShapes.collisionShape(state);
            AABB top = shape.toAabbs().stream().max(java.util.Comparator.comparingDouble(b -> b.maxY)).orElseThrow();
            sideMove(h, p, state, top);
            h.assertTrue(p.horizontalCollision, "Wall side fixture actually collides: " + facing);
            assertDoesNotBurn(h, p, "Wall side: " + facing);

            // The middle ledge is solid, but only the highest flame surface is hot.
            double x = facing == Direction.EAST ? 3.0 / 16 : facing == Direction.WEST ? 13.0 / 16 : .5;
            double z = facing == Direction.SOUTH ? 3.0 / 16 : facing == Direction.NORTH ? 13.0 / 16 : .5;
            p.setPos(h.absoluteVec(new Vec3(4 + x, 2 + 10.0 / 16, 4 + z)));
            p.setOnGround(true);
            h.assertTrue(!h.getLevel().noCollision(p, p.getBoundingBox().move(0, -.001, 0)),
                    "Middle ledge has real support: " + facing);
            assertDoesNotBurn(h, p, "Supported middle ledge: " + facing);

            p.setPos(h.absoluteVec(new Vec3(4 + (top.minX + top.maxX) / 2,
                    2 + top.maxY, 4 + (top.minZ + top.maxZ) / 2)));
            p.setOnGround(false);
            assertDoesNotBurn(h, p, "Airborne at the exact top plane: " + facing);
            p.setPos(p.position().add(0, .01, 0));
            p.setOnGround(true);
            assertDoesNotBurn(h, p, "Above top contact tolerance: " + facing);
        }
        h.succeed();
    }

    private static void assertDoesNotBurn(GameTestHelper h, Player p, String fixture) {
        h.assertTrue(!TorchContactRules.standingOnHotTorch(p) && !TorchContactRules.tick(p)
                && p.getRemainingFireTicks() <= 0, fixture);
    }

    @GameTest(template = "empty")
    public static void wallBoundingBoxGapsDoNotCollideOrIgnite(GameTestHelper h) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockState state = Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, facing);
            place(h, state);
            Player p = player(h);
            double x = facing == Direction.EAST ? .14 : facing == Direction.WEST ? .86 : .5;
            double z = facing == Direction.SOUTH ? .14 : facing == Direction.NORTH ? .86 : .5;
            p.setPos(h.absoluteVec(new Vec3(4 + x, 2.64, 4 + z)));
            AABB outer = TorchShapes.collisionShape(state).bounds().move(h.absolutePos(TORCH));
            h.assertTrue(outer.intersects(p.getBoundingBox()), "Fixture overlaps broad shape bounds: " + facing);
            h.assertTrue(h.getLevel().noCollision(p, p.getBoundingBox()), "Actual segmented gap has no collision: " + facing);
            h.assertTrue(!TorchContactRules.standingOnHotTorch(p) && !TorchContactRules.tick(p),
                    "Broad bounds must not cause false ignition: " + facing);
            Vec3 movement = facing.getAxis() == Direction.Axis.X ? new Vec3(0, 0, .05) : new Vec3(.05, 0, 0);
            Vec3 before = p.position();
            p.move(MoverType.SELF, movement);
            h.assertTrue(p.position().distanceTo(before.add(movement)) < 1.0E-6 && !p.horizontalCollision,
                    "Real movement inside segment gap remains free: " + facing);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void unlitRedstoneStillBlocksButDoesNotBurn(GameTestHelper h) {
        for (var block : new net.minecraft.world.level.block.Block[]{Blocks.REDSTONE_TORCH, Blocks.REDSTONE_WALL_TORCH}) {
            BlockState cold = block.defaultBlockState().setValue(RedstoneTorchBlock.LIT, false);
            place(h, cold);
            Player p = player(h);
            VoxelShape shape = TorchShapes.collisionShape(cold);
            AABB box = shape.toAabbs().stream().max(java.util.Comparator.comparingDouble(b -> b.maxY)).orElseThrow();
            sideMove(h, p, cold, box);
            h.assertTrue(p.horizontalCollision, "Unlit redstone retains rod collision");
            h.assertTrue(!TorchShapes.isHot(cold) && !TorchContactRules.tick(p) && !p.isOnFire(), "Unlit rod cannot ignite");
            p.setPos(h.absoluteVec(new Vec3(4 + (box.minX + box.maxX) / 2,
                    2 + box.maxY, 4 + (box.minZ + box.maxZ) / 2)));
            p.setOnGround(true);
            assertDoesNotBurn(h, p, "Unlit redstone highest supported surface");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void qualificationLongFireAndPhaseArePreserved(GameTestHelper h) {
        place(h, Blocks.TORCH.defaultBlockState());
        for (GameType mode : GameType.values()) {
            Player p = h.makeMockPlayer(mode);
            PixelScaleHelper.ensurePlayerMini(p);
            onTop(h, p);
            boolean eligible = mode == GameType.SURVIVAL || mode == GameType.ADVENTURE;
            h.assertTrue(TorchContactRules.tick(p) == eligible && p.isOnFire() == eligible, "Mode qualification: " + mode);
        }
        Player p = player(h);
        onTop(h, p);
        p.setRemainingFireTicks(37);
        TorchContactRules.tick(p);
        h.assertTrue(p.getRemainingFireTicks() >= 60 && p.getRemainingFireTicks() % 20 == 17,
                "Renewal preserves vanilla damage phase");
        p.setRemainingFireTicks(201);
        TorchContactRules.tick(p);
        h.assertTrue(p.getRemainingFireTicks() == 201, "Existing longer fire is not shortened");
        p.clearFire();
        ((DevourPlayerAccess) p).examplemod$setDevourState(123, .5F);
        h.assertTrue(!TorchContactRules.tick(p) && !p.isOnFire(), "Simulated F21 marker excludes ignition; not a real capture");
        ((DevourPlayerAccess) p).examplemod$setDevourState(-1, 0);
        p.setHealth(0);
        h.assertTrue(!TorchContactRules.tick(p), "Dead player cannot ignite");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 35)
    public static void realRodTravelClimbsAndSneakHoldsHeight(GameTestHelper h) {
        place(h, Blocks.TORCH.defaultBlockState());
        Player p = player(h);
        p.setNoGravity(false);
        p.setYRot(0);
        p.setPos(h.absoluteVec(new Vec3(4.5, 2.05, 4 + 7.0 / 16 - p.getBbWidth() / 2 - .001)));
        double start = p.getY();
        double[] held = {0};
        // As in the wall baseline, an unattached fixture receives one real travel per world tick.
        // No collision flag is injected, and the short ascent stays below the thin rod's top.
        for (int tick = 1; tick <= 5; tick++) {
            final int current = tick;
            h.runAtTickTime(tick, () -> {
                p.zza = 1;
                p.travel(new Vec3(0, 0, 1));
                h.assertTrue(p.horizontalCollision, "Forward travel actually collides with rod");
                h.assertTrue(!TorchContactRules.tick(p) && p.getRemainingFireTicks() <= 0,
                        "Climbing beside the rod never ignites");
                if (current == 5) {
                    double rise = p.getY() - start;
                    h.assertTrue(rise > .13 && rise < .16, "Five real travel ticks climb about .147 blocks, rise=" + rise);
                    held[0] = p.getY();
                }
            });
        }
        for (int tick = 6; tick <= 13; tick++) {
            h.runAtTickTime(tick, () -> {
                p.setShiftKeyDown(true);
                p.zza = 0;
                p.travel(Vec3.ZERO);
                h.assertTrue(Math.abs(p.getY() - held[0]) < .01,
                        "Sneaking without forward input holds actual rod-side height");
                h.assertTrue(!TorchContactRules.tick(p) && p.getRemainingFireTicks() <= 0,
                        "Holding height beside the rod never ignites");
            });
        }
        h.runAtTickTime(14, () -> {
            p.setShiftKeyDown(false);
            p.zza = 0;
        });
        for (int tick = 15; tick <= 19; tick++) h.runAtTickTime(tick, () -> p.travel(Vec3.ZERO));
        h.runAtTickTime(20, () -> {
            h.assertTrue(p.getY() < held[0] - .03, "Releasing sneak resumes gravity beside rod");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void worldTicksRenewWithoutAcceleratingDamageThenExpire(GameTestHelper h) {
        place(h, Blocks.TORCH.defaultBlockState());
        Player p = player(h);
        onTop(h, p);
        p.setNoGravity(false);
        // Extra fixture health prevents death from masquerading as natural fire expiry.
        p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(40);
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(10);
        p.getFoodData().setSaturation(0);
        h.getLevel().addFreshEntity(p);
        float health = p.getHealth();
        h.runAfterDelay(42, () -> {
            h.assertTrue(p.isOnFire() && p.onGround(), "Production player post tick renews fire on actual supported top");
            float lost = health - p.getHealth();
            h.assertTrue(lost >= 2 && lost <= 3, "42 real ticks have vanilla 20-tick fire damage cadence, lost=" + lost);
            p.setPos(h.absoluteVec(new Vec3(7.5, 2, 7.5)));
            p.setNoGravity(true);
            int remaining = p.getRemainingFireTicks();
            h.runAfterDelay(5, () -> {
                h.assertTrue(p.getRemainingFireTicks() <= remaining - 5, "Leaving rod stops renewal");
                h.runAfterDelay(85, () -> {
                    h.assertTrue(p.isAlive(), "Fixture remains alive until natural fire expiry");
                    h.assertTrue(!p.isOnFire(), "Fire expires naturally after leaving");
                    p.discard();
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void waterExtinguishesAndFireResistanceProtects(GameTestHelper h) {
        place(h, Blocks.SOUL_TORCH.defaultBlockState());
        Player p = player(h);
        onTop(h, p);
        p.setNoGravity(false);
        p.getFoodData().setFoodLevel(10);
        p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200));
        h.getLevel().addFreshEntity(p);
        float health = p.getHealth();
        h.runAfterDelay(42, () -> {
            h.assertTrue(p.isOnFire() && p.getHealth() == health, "Vanilla fire resistance prevents real ongoing fire damage");
            h.setBlock(new BlockPos(7, 2, 7), Blocks.WATER);
            p.setPos(h.absoluteVec(new Vec3(7.5, 2.1, 7.5)));
            h.runAfterDelay(3, () -> {
                h.assertTrue(!p.isOnFire(), "Actual water naturally extinguishes fire");
                h.assertTrue(!TorchContactRules.tick(p), "Water does not renew torch burning");
                p.discard();
                h.succeed();
            });
        });
    }

    private static void sideMove(GameTestHelper h, Player p, BlockState state, AABB box) {
        boolean alongX = state.hasProperty(WallTorchBlock.FACING)
                && state.getValue(WallTorchBlock.FACING).getAxis() == Direction.Axis.Z;
        double x = 4 + (box.minX + box.maxX) / 2;
        double z = 4 + (box.minZ + box.maxZ) / 2;
        p.setPos(h.absoluteVec(new Vec3(x - (alongX ? .4 : 0), 2 + box.minY,
                z - (alongX ? 0 : .4))));
        p.move(MoverType.SELF, new Vec3(alongX ? .8 : 0, 0, alongX ? 0 : .8));
    }

    private static void place(GameTestHelper h, BlockState state) {
        h.setBlock(TORCH.below(), Blocks.STONE);
        // Stable support for each possible wall orientation.
        for (Direction d : Direction.Plane.HORIZONTAL) h.setBlock(TORCH.relative(d), Blocks.STONE);
        h.setBlock(TORCH, state);
        // Remove non-supporting neighbors so probes do not hit unrelated full blocks.
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (!state.hasProperty(WallTorchBlock.FACING) || d != state.getValue(WallTorchBlock.FACING).getOpposite())
                h.setBlock(TORCH.relative(d), Blocks.AIR);
        }
    }
    private static void onTop(GameTestHelper h, Player p) {
        p.setPos(h.absoluteVec(new Vec3(4.5, 2 + 10.0 / 16, 4.5)));
        p.setDeltaMovement(Vec3.ZERO);
        p.setOnGround(true);
    }
    private static Player player(GameTestHelper h) {
        Player p = new AuthorityPlayer(h.getLevel());
        PixelScaleHelper.ensurePlayerMini(p);
        p.setNoGravity(true);
        return p;
    }
    private static final class AuthorityPlayer extends Player {
        AuthorityPlayer(Level level) { super(level, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "torch-test")); }
        @Override public boolean isCreative() { return false; }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isLocalPlayer() { return true; }
    }
    private TorchFeatureTests() { }
}
