package com.example.examplemod.gametest;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.config.SurvivalConfig;
import com.example.examplemod.init.ModDamageTypes;
import com.example.examplemod.init.ModTags;
import com.example.examplemod.scale.PixelScaleHelper;
import com.example.examplemod.survival.SurvivalRules;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.WeightedPressurePlateBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ExampleMod.MODID)
@PrefixGameTestTemplate(false)
public final class SurvivalFeatureTests {
    @GameTest(template = "empty")
    public static void nectarRestoresHungerAndStopsAtFull(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        PixelScaleHelper.ensurePlayerMini(player);
        player.getFoodData().setFoodLevel(10);
        player.getFoodData().setSaturation(0.0F);

        helper.assertTrue(SurvivalRules.giveNectar(player), "Hungry tiny player can gather nectar");
        helper.assertTrue(player.getFoodData().getFoodLevel() == 12, "Nectar restores two food points");
        helper.assertTrue(Math.abs(player.getFoodData().getSaturationLevel() - 0.4F) < 0.001F,
                "Nectar restores 0.4 saturation");
        player.getFoodData().setFoodLevel(20);
        helper.assertTrue(!SurvivalRules.giveNectar(player), "Full player cannot gather extra food");
        helper.assertTrue(player.getFoodData().getFoodLevel() == 20, "Full hunger stays capped");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void survivalTagsAndTargetFiltersAreScoped(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        SurvivalRules.clearPlayer(player.getUUID());
        PixelScaleHelper.ensurePlayerMini(player);
        Cow cow = helper.spawn(EntityType.COW, helper.absoluteVec(new net.minecraft.world.phys.Vec3(4.5, 2, 4.5)));
        Spider spider = helper.spawn(EntityType.SPIDER, helper.absoluteVec(new net.minecraft.world.phys.Vec3(6.5, 2, 4.5)));

        helper.assertTrue(EntityType.SPIDER.is(ModTags.PREDATORY_INSECTS), "Spider is a listed predator");
        helper.assertTrue(EntityType.SILVERFISH.is(ModTags.PREDATORY_INSECTS), "Silverfish is a listed predator");
        helper.assertTrue(EntityType.SPIDER.is(ModTags.WEB_ATTRACTED_SPIDERS), "Spider hears web vibration");
        helper.assertTrue(!EntityType.COW.is(ModTags.RIDEABLE_LIVESTOCK), "Cow is not an implicit mount");
        helper.assertTrue(EntityType.HORSE.is(ModTags.RIDEABLE_LIVESTOCK), "Horse is a listed mount");
        helper.assertTrue(SurvivalRules.shouldIgnoreTriggerEntity(player), "Tiny player is filtered from triggers");
        helper.assertTrue(!SurvivalRules.shouldIgnoreTriggerEntity(cow), "Other entities keep triggering redstone");
        helper.assertTrue(SurvivalRules.shouldSuppressStepEvent(player), "Tiny player footstep is suppressed");
        helper.assertTrue(!SurvivalRules.shouldSuppressStepEvent(cow), "Other entity game events remain enabled");
        helper.assertTrue(SurvivalRules.isSlowSurface(Blocks.SLIME_BLOCK.defaultBlockState()), "Slime block is a slow surface");
        helper.assertTrue(!SurvivalRules.isSlowSurface(Blocks.STONE.defaultBlockState()), "Stone stays unchanged");
        helper.assertTrue(SurvivalConfig.WEB_VIBRATION_RANGE.get() == 12, "Spider range default is 12 blocks");
        float probe = SurvivalRules.cameraCollisionProbeRadius(player, 0.1F);
        helper.assertTrue(probe > 0 && probe < 0.1F, "Tiny camera collision probe scales with player width");
        helper.assertTrue(SurvivalRules.cameraCollisionProbeRadius(cow, 0.1F) == 0.1F,
                "Camera probe remains vanilla for non-player entities");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cobwebVibrationCooldownSurvivesLeavingTheWeb(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        SurvivalRules.clearPlayer(player.getUUID());
        PixelScaleHelper.ensurePlayerMini(player);
        BlockPos firstWeb = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos secondWeb = helper.absolutePos(new BlockPos(2, 2, 1));

        helper.assertTrue(SurvivalRules.shouldEmitCobwebVibration(player, firstWeb, 100L),
                "Entering a web emits a vibration");
        helper.assertTrue(!SurvivalRules.shouldEmitCobwebVibration(player, firstWeb, 101L),
                "Staying in the same web does not emit repeated vibrations");
        SurvivalRules.leaveCobweb(player);
        helper.assertTrue(!SurvivalRules.shouldEmitCobwebVibration(player, secondWeb, 119L),
                "Leaving and re-entering cannot bypass the 20-tick cooldown");
        SurvivalRules.leaveCobweb(player);
        helper.assertTrue(SurvivalRules.shouldEmitCobwebVibration(player, secondWeb, 120L),
                "A new web vibration is allowed when the cooldown expires");
        SurvivalRules.clearPlayer(player.getUUID());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void mountTargetMustBeRideableAndUnderCrosshair(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        PixelScaleHelper.ensurePlayerMini(player);
        Vec3 playerPos = helper.absoluteVec(new Vec3(3.5, 2.0, 1.5));
        player.moveTo(playerPos.x, playerPos.y, playerPos.z, 0.0F, 0.0F);
        Cow cow = helper.spawn(EntityType.COW, new Vec3(5.5, 2.0, 3.5));
        Entity horse = helper.spawn(EntityType.HORSE, new Vec3(3.5, 2.0, 3.5));
        Entity horseOffCrosshair = helper.spawn(EntityType.HORSE, new Vec3(4.5, 2.0, 1.5));

        helper.assertTrue(!SurvivalRules.isValidMountTarget(player, cow),
                "A non-tagged cow cannot be mounted");
        helper.assertTrue(horse.getType().is(ModTags.RIDEABLE_LIVESTOCK), "Horse is in the mount tag");
        helper.assertTrue(player.distanceToSqr(horse) <= 4.5D * 4.5D,
                "Horse is within mount reach: d2=" + player.distanceToSqr(horse)
                        + ", player=" + player.position() + ", horse=" + horse.position());
        helper.assertTrue(player.hasLineOfSight(horse), "Horse is visible to the player");
        helper.assertTrue(SurvivalRules.isValidMountTarget(player, horse),
                "A tagged horse under the crosshair can be mounted");
        helper.assertTrue(!SurvivalRules.isValidMountTarget(player, horseOffCrosshair),
                "A visible but non-crosshair horse cannot be mounted");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void pressurePlateFilteringIsLimitedToSpecifiedTargets(GameTestHelper helper) {
        Player tiny = helper.makeMockPlayer(GameType.SURVIVAL);
        SurvivalRules.clearPlayer(tiny.getUUID());
        PixelScaleHelper.ensurePlayerMini(tiny);
        tiny.setNoGravity(true);
        Vec3 tinyPos = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
        tiny.moveTo(tinyPos.x, tinyPos.y, tinyPos.z, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(tiny);

        BlockPos stonePos = new BlockPos(2, 2, 2);
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        helper.setBlock(stonePos, Blocks.STONE_PRESSURE_PLATE.defaultBlockState());
        BlockPos absStonePos = helper.absolutePos(stonePos);
        helper.getBlockState(stonePos).entityInside(helper.getLevel(), absStonePos, tiny);
        helper.assertTrue(!helper.getBlockState(stonePos).getValue(PressurePlateBlock.POWERED),
                "Tiny player does not activate a stone pressure plate");

        helper.setBlock(stonePos, Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE.defaultBlockState());
        helper.getBlockState(stonePos).entityInside(helper.getLevel(), absStonePos, tiny);
        helper.assertTrue(helper.getBlockState(stonePos).getValue(WeightedPressurePlateBlock.POWER) == 0,
                "Tiny player does not activate a heavy weighted pressure plate");

        helper.setBlock(stonePos, Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState());
        helper.getBlockState(stonePos).entityInside(helper.getLevel(), absStonePos, tiny);
        helper.assertTrue(helper.getBlockState(stonePos).getValue(WeightedPressurePlateBlock.POWER) > 0,
                "Light weighted pressure plate keeps its vanilla tiny-player behavior");

        helper.setBlock(stonePos, Blocks.STONE_PRESSURE_PLATE.defaultBlockState());
        Cow cow = helper.spawn(EntityType.COW, new Vec3(2.5, 2.0, 2.5));
        helper.getBlockState(stonePos).entityInside(helper.getLevel(), absStonePos, cow);
        helper.assertTrue(helper.getBlockState(stonePos).getValue(PressurePlateBlock.POWERED),
                "Other entities still activate a stone pressure plate");
        SurvivalRules.clearPlayer(tiny.getUUID());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void predatorDamageHasSeparateDeathAttribution(GameTestHelper helper) {
        Spider spider = helper.spawn(EntityType.SPIDER, helper.absoluteVec(new net.minecraft.world.phys.Vec3(4.5, 2, 4.5)));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        PixelScaleHelper.ensurePlayerMini(player);
        var source = ModDamageTypes.getDevouredSource(helper.getLevel(), spider);
        helper.assertTrue(source.is(ModDamageTypes.DEVOURED), "Predation has a dedicated damage type");
        helper.assertTrue(source.getEntity() == spider, "Predation kill credit points to spider");
        helper.assertTrue(SurvivalRules.shouldUseDevouredDamage(spider, player), "Predator uses special damage for tiny player");
        helper.assertTrue(!SurvivalRules.shouldUseDevouredDamage((net.minecraft.world.entity.Mob) cow(helper), player),
                "Unlisted mob keeps ordinary damage source");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rainOxygenAccumulationAndShelteredRecovery(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        SurvivalRules.clearPlayer(player.getUUID());
        PixelScaleHelper.ensurePlayerMini(player);
        player.setAirSupply(300);

        // 1. Ticking in rain for 20 ticks: air should decrement by 1 per tick to 280 despite vanilla recovery attempt
        for (int i = 0; i < 20; i++) {
            player.setAirSupply(300);
            SurvivalRules.tickRain(player, true, false);
        }
        helper.assertTrue(player.getAirSupply() == 280, "Rain oxygen decrements to 280 after 20 ticks in rain");
        helper.assertTrue(SurvivalRules.getRainAirSupply(player) == 280, "State matches air supply");

        // 2. Next 20 ticks: air should decrement to 260 despite vanilla recovery attempt
        for (int i = 0; i < 20; i++) {
            player.setAirSupply(300);
            SurvivalRules.tickRain(player, true, false);
        }
        helper.assertTrue(player.getAirSupply() == 260, "Rain oxygen depletion accumulates across ticks");

        // 3. Sheltered recovery: restores 4 per tick until full
        SurvivalRules.tickRain(player, false, false);
        helper.assertTrue(player.getAirSupply() == 264, "Sheltered player restores 4 air per tick");
        for (int i = 0; i < 20 && SurvivalRules.hasRainOxygenState(player); i++) {
            SurvivalRules.tickRain(player, false, false);
        }
        helper.assertTrue(player.getAirSupply() == 300, "Sheltered player fully recovers to max air");
        helper.assertTrue(!SurvivalRules.hasRainOxygenState(player), "Rain oxygen state cleaned up once fully recovered");

        // 4. Fluid handover: entering water/bubble column clears rain state
        SurvivalRules.setRainAirSupply(player, 250);
        helper.assertTrue(SurvivalRules.hasRainOxygenState(player), "Rain state set to 250");
        SurvivalRules.tickRain(player, false, true);
        helper.assertTrue(!SurvivalRules.hasRainOxygenState(player), "Rain state cleared when in fluid");

        // 5. Logout cleanup
        SurvivalRules.setRainAirSupply(player, 200);
        SurvivalRules.clearPlayer(player.getUUID());
        helper.assertTrue(!SurvivalRules.hasRainOxygenState(player), "Rain state cleaned up on player logout");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rainOxygenSuffocationDamageAtZero(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        SurvivalRules.clearPlayer(player.getUUID());
        PixelScaleHelper.ensurePlayerMini(player);
        SurvivalRules.setRainAirSupply(player, 0);

        float initialHealth = player.getHealth();
        // Tick 20 ticks in rain at 0 air
        for (int i = 0; i < 20; i++) {
            SurvivalRules.tickRain(player, true, false);
        }
        helper.assertTrue(player.getAirSupply() == 0, "Air supply remains 0 in rain");
        helper.assertTrue(player.getHealth() < initialHealth, "Player takes suffocation damage after 20 ticks at zero air");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nibbleTargetChangeResetsDelayWhileHeartbeatMaintainsTimer(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        SurvivalRules.clearPlayer(player.getUUID());
        PixelScaleHelper.ensurePlayerMini(player);
        player.setShiftKeyDown(true);
        player.getFoodData().setFoodLevel(10);

        BlockPos localA = new BlockPos(2, 2, 3);
        BlockPos localB = new BlockPos(2, 2, 1);
        helper.setBlock(localA, Blocks.OAK_LEAVES.defaultBlockState());
        helper.setBlock(localB, Blocks.OAK_LEAVES.defaultBlockState());
        BlockPos absA = helper.absolutePos(localA);
        BlockPos absB = helper.absolutePos(localB);

        Vec3 center = helper.absoluteVec(new Vec3(2.5, 2.0, 2.0));
        player.moveTo(center.x, center.y, center.z, 0.0F, 0.0F);

        helper.assertTrue(SurvivalRules.isValidNibbleTarget(player, helper.getLevel(), absA),
                "Leaf block A is valid target when looking at yaw 0");

        SurvivalRules.updateNibble(player, absA);
        helper.assertTrue(SurvivalRules.hasNibbleSession(player), "Nibble session started on target A");
        Long nextAtA = SurvivalRules.getNibbleNextAt(player);
        long expectedNextAtA = helper.getLevel().getGameTime() + 20L;
        helper.assertTrue(nextAtA != null && nextAtA == expectedNextAtA,
                "Target A nextNibbleAt is 20 ticks in future");

        // Heartbeat on same target A: timer must NOT reset
        SurvivalRules.updateNibble(player, absA);
        helper.assertTrue(SurvivalRules.getNibbleNextAt(player).equals(nextAtA),
                "Heartbeat on same target maintains nextNibbleAt without resetting");

        // Move in front of leaf block B and keep facing +Z.
        Vec3 positionBeforeB = helper.absoluteVec(new Vec3(2.5, 2.0, 0.0));
        player.moveTo(positionBeforeB.x, positionBeforeB.y, positionBeforeB.z, 0.0F, 0.0F);
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        helper.assertTrue(SurvivalRules.isValidNibbleTarget(player, helper.getLevel(), absB),
                "Leaf block B is valid target when looking at yaw 180");

        SurvivalRules.updateNibble(player, absB);
        helper.assertTrue(absB.equals(SurvivalRules.getNibbleTargetPos(player)),
                "Target pos updated to block B");
        Long nextAtB = SurvivalRules.getNibbleNextAt(player);
        long expectedNextAtB = helper.getLevel().getGameTime() + 20L;
        helper.assertTrue(nextAtB != null && nextAtB == expectedNextAtB,
                "Target change resets nextNibbleAt to full 20 ticks");

        // Verify block B is NOT consumed prematurely before nextAtB
        SurvivalRules.setNibbleSessionForTesting(player, absB, helper.getLevel().getGameTime(), helper.getLevel().getGameTime() + 5L);
        SurvivalRules.tickNibble(player);
        helper.assertTrue(helper.getBlockState(localB).is(Blocks.OAK_LEAVES),
                "Block B is not destroyed before its 20-tick delay finishes");

        // Verify block B IS consumed once its timer arrives
        SurvivalRules.setNibbleSessionForTesting(player, absB, helper.getLevel().getGameTime(), helper.getLevel().getGameTime());
        SurvivalRules.tickNibble(player);
        helper.assertTrue(!helper.getBlockState(localB).is(Blocks.OAK_LEAVES),
                "Block B is consumed once full delay elapses");
        helper.assertTrue(player.getFoodData().getFoodLevel() == 12, "Food restored after nibble completion");
        SurvivalRules.clearPlayer(player.getUUID());

        helper.succeed();
    }

    private static Cow cow(GameTestHelper helper) {
        return helper.spawn(EntityType.COW, helper.absoluteVec(new net.minecraft.world.phys.Vec3(8.5, 2, 8.5)));
    }

    private SurvivalFeatureTests() {
    }
}
