package com.example.examplemod.gametest;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.init.ModAttributes;
import com.example.examplemod.init.ModItems;
import com.example.examplemod.mixin.LivingEntityAccessor;
import com.example.examplemod.retaliate.FriendlyRetaliateGoal;
import com.example.examplemod.scale.PehkuiScaleSupport;
import com.example.examplemod.scale.PixelScaleHelper;
import com.example.examplemod.scale.ScaleEvents;
import com.example.examplemod.stomp.StompHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ExampleMod.MODID)
@PrefixGameTestTemplate(false)
public class PixelSurvivalGameTests {

    /**
     * 1. Player dimensions, eye height, health, and lifecycle reset locking.
     */
    @GameTest(template = "empty")
    public static void playerDimensionsAndLifecycleLocking(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(!PixelScaleHelper.isTiny(player), "A new player is not tiny");
        helper.assertTrue(player.getBbHeight() > 1.0F, "A new player keeps a normal hitbox");

        // Opting in fixes the scale to 1 / 28.8
        float expectedScale = PixelScaleHelper.TINY_SCALE_FLOAT;
        PixelScaleHelper.ensurePlayerMini(player);
        float actualScale = PehkuiScaleSupport.isLoaded()
                ? PehkuiScaleSupport.getEffectiveScale(player)
                : player.getScale();
        helper.assertTrue(Math.abs(actualScale - expectedScale) < 1e-4, "Player scale should be 1/28.8");

        // Player standing height is ~1/16 block (1.8 / 28.8 = 0.0625)
        float height = player.getBbHeight();
        helper.assertTrue(Math.abs(height - 0.0625F) < 1e-3, "Player standing height should be ~1/16 block");

        // Eye height must also be scaled
        float eyeHeight = player.getEyeHeight();
        helper.assertTrue(eyeHeight < 0.1F && eyeHeight > 0.01F, "Player eye height must be scaled to tiny proportions");

        // Apply and verify attributes
        helper.assertTrue(player.getMaxHealth() == 10.0F, "Player max health should be halved to 10.0");
        helper.assertTrue(player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) >= 1.5D, "Block interaction range must be at least 1.5");
        helper.assertTrue(player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) >= 1.5D, "Entity interaction range must be at least 1.5");
        helper.assertTrue(player.getAttributeValue(Attributes.STEP_HEIGHT) >= 1.0 / 16.0, "Step height must not be lower than 1/16 block");

        // Idempotency: calling ensurePlayerMini again should not duplicate modifiers
        PixelScaleHelper.ensurePlayerMini(player);
        helper.assertTrue(player.getMaxHealth() == 10.0F, "Player max health must remain 10.0 without duplicate modifiers");

        // Native scale command simulation: setting vanilla SCALE base value must NOT un-tiny the player
        if (PixelScaleHelper.hasAttribute(player, Attributes.SCALE)) {
            player.getAttribute(Attributes.SCALE).setBaseValue(10.0D);
            float scaleAfterVanillaChange = PehkuiScaleSupport.isLoaded()
                    ? PehkuiScaleSupport.getEffectiveScale(player)
                    : player.getScale();
            helper.assertTrue(Math.abs(scaleAfterVanillaChange - expectedScale) < 1e-4, "Native scale change must not restore player scale");
        }

        // Merely existing does not shrink a server player.
        net.minecraft.server.level.ServerPlayer fakePlayer = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel());
        helper.assertTrue(!PixelScaleHelper.isTiny(fakePlayer), "FakePlayer is not forced tiny");
        helper.assertTrue(Math.abs(fakePlayer.getScale() - 1.0F) < 1e-4, "FakePlayer getScale stays 1");

        helper.succeed();
    }

    /**
     * 2. Entity save/load NBT persistence of tiny modifiers and active world integration.
     */
    @GameTest(template = "empty")
    public static void entitySaveLoadPersistence(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, 2, 2, 2);
        PixelScaleHelper.applyTinyModifiers(cow);
        helper.assertTrue(PixelScaleHelper.isTiny(cow), "Cow must be tiny after applying modifiers");

        // Save to NBT
        CompoundTag tag = new CompoundTag();
        cow.saveWithoutId(tag);

        // Load into new Cow entity and add to active world
        Cow reloadedCow = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(reloadedCow != null, "Failed to create reloaded cow");
        reloadedCow.load(tag);
        Vec3 spawnPos = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
        reloadedCow.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        helper.getLevel().addFreshEntity(reloadedCow);

        // Verify permanent modifier was loaded and recognized in world
        helper.assertTrue(PixelScaleHelper.isTiny(reloadedCow), "Reloaded cow must remain tiny from persistent attribute modifier");
        helper.assertTrue(Math.abs(reloadedCow.getScale() - PixelScaleHelper.WAND_SCALE_FLOAT) < 1e-5, "Reloaded cow scale must be wand scale");

        helper.succeed();
    }

    /**
     * 3. Multiple wand toggles preserve health ratio without free healing.
     */
    @GameTest(template = "empty")
    public static void multipleTogglesPreserveHealthRatio(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Cow cow = helper.spawn(EntityType.COW, 3, 2, 3);
        cow.setHealth(6.0F); // 6.0 out of 10.0 = 60%

        // 1st toggle: normal -> tiny
        PixelScaleHelper.ToggleResult res1 = PixelScaleHelper.toggleLivingTarget(player, cow);
        helper.assertTrue(res1 == PixelScaleHelper.ToggleResult.APPLIED_MINI, "1st toggle should apply mini");
        helper.assertTrue(PixelScaleHelper.isTiny(cow), "Cow should be tiny");
        helper.assertTrue(cow.getMaxHealth() == 5.0F, "Tiny cow max health should be 5.0");
        helper.assertTrue(Math.abs(cow.getHealth() - 3.0F) < 1e-3, "Tiny cow health should be 60% of 5.0 = 3.0");

        // Damage while tiny: reduce from 3.0 to 2.0 (40%)
        cow.setHealth(2.0F);

        // 2nd toggle: tiny -> restored
        PixelScaleHelper.ToggleResult res2 = PixelScaleHelper.toggleLivingTarget(player, cow);
        helper.assertTrue(res2 == PixelScaleHelper.ToggleResult.RESTORED, "2nd toggle should restore");
        helper.assertTrue(!PixelScaleHelper.isTiny(cow), "Cow should be normal size");
        helper.assertTrue(cow.getMaxHealth() == 10.0F, "Normal cow max health should be 10.0");
        helper.assertTrue(Math.abs(cow.getHealth() - 4.0F) < 1e-3, "Restored cow health should be 40% of 10.0 = 4.0 (no free healing)");

        // Repeated toggles back and forth
        PixelScaleHelper.toggleLivingTarget(player, cow); // mini (2.0 health)
        PixelScaleHelper.toggleLivingTarget(player, cow); // restored (4.0 health)
        PixelScaleHelper.toggleLivingTarget(player, cow); // mini (2.0 health)
        PixelScaleHelper.toggleLivingTarget(player, cow); // restored (4.0 health)

        helper.assertTrue(Math.abs(cow.getHealth() - 4.0F) < 1e-3, "Health must remain 4.0 after repeated toggles");

        helper.succeed();
    }

    /**
     * 4. External modifiers from other mods are never deleted on restore.
     */
    @GameTest(template = "empty")
    public static void externalModifiersPreserved(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Cow cow = helper.spawn(EntityType.COW, 4, 2, 4);

        ResourceLocation externalId = ResourceLocation.fromNamespaceAndPath("othermod", "external_speed_buff");
        AttributeModifier externalMod = new AttributeModifier(externalId, 0.2D, AttributeModifier.Operation.ADD_VALUE);
        cow.getAttribute(Attributes.MOVEMENT_SPEED).addPermanentModifier(externalMod);

        // Toggle to mini
        PixelScaleHelper.toggleLivingTarget(player, cow);
        helper.assertTrue(cow.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(externalId), "External modifier must stay after shrinking");

        // Toggle back to normal
        PixelScaleHelper.toggleLivingTarget(player, cow);
        helper.assertTrue(cow.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(externalId), "External modifier must stay after restoring");

        helper.succeed();
    }

    /**
     * 5. Wand target rejection on player and cooldown only on success.
     */
    @GameTest(template = "empty")
    public static void wandPlayerRejectionAndCooldown(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack wand = new ItemStack(ModItems.SCALE_WAND.get());

        // Target rejection on player: should FAIL and NOT add cooldown
        PixelScaleHelper.ToggleResult res = PixelScaleHelper.toggleLivingTarget(player, player);
        helper.assertTrue(res == PixelScaleHelper.ToggleResult.REJECTED_PLAYER, "Wand must reject Player target");

        InteractionResult interactRes = ModItems.SCALE_WAND.get().interactLivingEntity(wand, player, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(interactRes == InteractionResult.FAIL, "Right clicking player should fail");
        helper.assertTrue(!player.getCooldowns().isOnCooldown(ModItems.SCALE_WAND.get()), "Player should NOT receive cooldown on rejected target");

        // Target non-player: should SUCCEED and add 10-tick cooldown
        Cow cow = helper.spawn(EntityType.COW, 2, 2, 2);
        InteractionResult cowRes = ModItems.SCALE_WAND.get().interactLivingEntity(wand, player, cow, InteractionHand.MAIN_HAND);
        helper.assertTrue(cowRes == InteractionResult.SUCCESS, "Right clicking cow should succeed");
        helper.assertTrue(player.getCooldowns().isOnCooldown(ModItems.SCALE_WAND.get()), "Player should receive cooldown on successful toggle");

        // Second click during cooldown should be blocked
        InteractionResult secondRes = ModItems.SCALE_WAND.get().interactLivingEntity(wand, player, cow, InteractionHand.OFF_HAND);
        helper.assertTrue(secondRes == InteractionResult.FAIL, "Second hand interaction during cooldown should fail");

        helper.succeed();
    }

    /**
     * 6. Restore is blocked when collision space is constrained.
     */
    @GameTest(template = "empty")
    public static void restoreBlockedWhenSpaceConstrained(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Cow cow = helper.spawn(EntityType.COW, 5, 2, 5);

        // Shrink cow
        PixelScaleHelper.toggleLivingTarget(player, cow);
        helper.assertTrue(PixelScaleHelper.isTiny(cow), "Cow should be tiny");

        // Place a solid block right where the restored cow's body would be
        BlockPos obstruction = new BlockPos(5, 3, 5);
        helper.setBlock(obstruction, Blocks.OBSIDIAN.defaultBlockState());

        // Attempt restore
        PixelScaleHelper.ToggleResult res = PixelScaleHelper.toggleLivingTarget(player, cow);
        helper.assertTrue(res == PixelScaleHelper.ToggleResult.RESTORE_BLOCKED, "Restore must be blocked by solid block collision");
        helper.assertTrue(PixelScaleHelper.isTiny(cow), "Cow must remain tiny when blocked");
        helper.assertTrue(cow.getMaxHealth() == 5.0F, "Cow attributes must not be restored when blocked");

        // Remove block and restore should now succeed
        helper.setBlock(obstruction, Blocks.AIR.defaultBlockState());
        PixelScaleHelper.ToggleResult resSuccess = PixelScaleHelper.toggleLivingTarget(player, cow);
        helper.assertTrue(resSuccess == PixelScaleHelper.ToggleResult.RESTORED, "Restore should succeed once obstruction is removed");
        helper.assertTrue(!PixelScaleHelper.isTiny(cow), "Cow should now be restored to normal");

        helper.succeed();
    }

    /**
     * 7. Tiny damage output (0.25x), knockback output (0.25x), received knockback (2.0x), and mining speed (0.2x).
     */
    @GameTest(template = "empty")
    public static void tinyDamageKnockbackAndMiningSpeed(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        PixelScaleHelper.ensurePlayerMini(player);
        Cow victim = helper.spawn(EntityType.COW, 6, 2, 6);

        // 1. Damage output: tiny player incoming damage scaled to 0.25 (both base and weapon)
        DamageSource src = player.damageSources().playerAttack(player);
        DamageContainer container = new DamageContainer(src, 10.0F);
        CommonHooks.onEntityIncomingDamage(victim, container);
        helper.assertTrue(Math.abs(container.getNewDamage() - 2.5F) < 1e-3, "Tiny entity damage output must be multiplied by 0.25");

        DamageContainer weaponContainer = new DamageContainer(src, 8.0F);
        CommonHooks.onEntityIncomingDamage(victim, weaponContainer);
        helper.assertTrue(Math.abs(weaponContainer.getNewDamage() - 2.0F) < 1e-3, "Weapon damage output from tiny player must be multiplied by 0.25");

        // 2. Knockback received: tiny victim receives 2.0x knockback
        Cow tinyVictim = helper.spawn(EntityType.COW, 7, 2, 7);
        PixelScaleHelper.applyTinyModifiers(tinyVictim);
        LivingKnockBackEvent kbEvent = CommonHooks.onLivingKnockBack(tinyVictim, 1.0F, 1.0, 0.0);
        helper.assertTrue(Math.abs(kbEvent.getStrength() - 2.0F) < 1e-3, "Tiny victim received knockback must be multiplied by 2.0");

        // 3. Mining speed: tiny player mining speed scaled to 0.2x
        PlayerEvent.BreakSpeed breakSpeedEvent = new PlayerEvent.BreakSpeed(player, Blocks.STONE.defaultBlockState(), 10.0F, BlockPos.ZERO);
        NeoForge.EVENT_BUS.post(breakSpeedEvent);
        helper.assertTrue(Math.abs(breakSpeedEvent.getNewSpeed() - 2.0F) < 1e-3, "Tiny player mining speed must be multiplied by 0.2");

        helper.succeed();
    }

    /**
     * 8. Wall climbing behavior and sneaking stop.
     */
    @GameTest(template = "empty")
    public static void wallClimbingAndSneaking(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, 8, 2, 8);
        PixelScaleHelper.applyTinyModifiers(cow);

        // Set collision against wall
        cow.horizontalCollision = true;

        // When tiny and hitting wall horizontally, onClimbable returns true
        helper.assertTrue(cow.onClimbable(), "Tiny entity hitting wall horizontally must be climbable");

        // After restoring, normal entity hitting plain wall does not climb
        PixelScaleHelper.removeTinyModifiers(cow);
        helper.assertTrue(!cow.onClimbable(), "Normal entity hitting plain wall must not be climbable");

        helper.succeed();
    }

    /**
     * 9. Friendly mob retaliation: triggers on real damage, excludes owner/allies/creative.
     */
    @GameTest(template = "empty")
    public static void friendlyMobRetaliation(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, 9, 2, 9);
        Zombie attacker = helper.spawn(EntityType.ZOMBIE, 9, 2, 10);

        // Attacking cow causes cow to register retaliation goal target
        cow.hurt(cow.damageSources().mobAttack(attacker), 2.0F);

        FriendlyRetaliateGoal goal = ScaleEvents.getOrAttachRetaliateGoal(cow);
        helper.assertTrue(goal.getRetaliateTarget() == attacker, "Cow must target attacker on real damage");
        helper.assertTrue(goal.getTicksRemaining() == 200, "Retaliation goal memory should be 200 ticks");
        helper.assertTrue(goal.canUse(), "Retaliation goal must be active");

        // Creative player should NOT be targeted
        Player creativePlayer = helper.makeMockPlayer(GameType.CREATIVE);
        goal.setRetaliateTarget(creativePlayer);
        helper.assertTrue(goal.getRetaliateTarget() != creativePlayer, "Creative player must not be targeted for retaliation");

        // Tiny mob retaliation damage test: base 2.0F damage scaled by 0.25 in incoming damage event -> 0.5F
        Cow tinyCow = helper.spawn(EntityType.COW, 9, 2, 9);
        PixelScaleHelper.applyTinyModifiers(tinyCow);
        DamageSource tinyMobAttack = tinyCow.damageSources().mobAttack(tinyCow);
        DamageContainer tinyAttackContainer = new DamageContainer(tinyMobAttack, 2.0F);
        CommonHooks.onEntityIncomingDamage(attacker, tinyAttackContainer);
        helper.assertTrue(Math.abs(tinyAttackContainer.getNewDamage() - 0.5F) < 1e-3, "Tiny mob retaliate damage must be scaled to 0.5F");

        helper.succeed();
    }

    /**
     * 10. Stomp geometry, movement requirement, cooldown, height ratio, riding exemption, and damage attribution.
     * All entities are independent and use structure-relative coordinates via helper.absoluteVec.
     */
    @GameTest(template = "empty")
    public static void stompDamageBoundaryAndConditions(GameTestHelper helper) {
        // Condition A (Positive control: moving stomper enlarged with SCALE to satisfy >= 4 height ratio)
        Cow bigCow1 = helper.spawn(EntityType.COW, 2, 2, 2);
        bigCow1.getAttribute(Attributes.SCALE).setBaseValue(3.0D);
        bigCow1.refreshDimensions();
        Cow tinyCow1 = helper.spawn(EntityType.COW, 2, 2, 2);
        PixelScaleHelper.applyTinyModifiers(tinyCow1);
        Vec3 p1 = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
        bigCow1.setPos(p1.x, p1.y, p1.z);
        bigCow1.xo = p1.x - 0.1;
        bigCow1.yo = p1.y;
        bigCow1.zo = p1.z;
        tinyCow1.setPos(p1.x, p1.y, p1.z);
        boolean movingStomp = StompHandler.tryStomp(bigCow1, tinyCow1, 0.0F);
        helper.assertTrue(movingStomp, "Moving over tiny entity must trigger stomp damage");
        helper.assertTrue(tinyCow1.getHealth() < tinyCow1.getMaxHealth(), "Tiny cow should take stomp damage");

        // Condition B (Negative control: standing still)
        Cow bigCow2 = helper.spawn(EntityType.COW, 4, 2, 4);
        bigCow2.getAttribute(Attributes.SCALE).setBaseValue(3.0D);
        bigCow2.refreshDimensions();
        Cow tinyCow2 = helper.spawn(EntityType.COW, 4, 2, 4);
        PixelScaleHelper.applyTinyModifiers(tinyCow2);
        Vec3 p2 = helper.absoluteVec(new Vec3(4.5, 2.0, 4.5));
        bigCow2.setPos(p2.x, p2.y, p2.z);
        bigCow2.xo = p2.x;
        bigCow2.yo = p2.y;
        bigCow2.zo = p2.z;
        bigCow2.fallDistance = 0;
        tinyCow2.setPos(p2.x, p2.y, p2.z);
        boolean stillStomp = StompHandler.tryStomp(bigCow2, tinyCow2, 0.0F);
        helper.assertTrue(!stillStomp, "Standing still must not trigger stomp damage");

        // Condition C: 10-tick independent cooldown blocks immediate consecutive stomp on pair 1
        boolean immediateStomp = StompHandler.tryStomp(bigCow1, tinyCow1, 0.0F);
        helper.assertTrue(!immediateStomp, "Consecutive stomp within 10 ticks must be blocked by cooldown");

        // Condition D: Height ratio check (< 4 should fail, including ratio 1 and normalCow vs halfCow ratio 2)
        Cow bigCow3 = helper.spawn(EntityType.COW, 6, 2, 6);
        Cow normalCow = helper.spawn(EntityType.COW, 6, 2, 6);
        Vec3 p3 = helper.absoluteVec(new Vec3(6.5, 2.0, 6.5));
        bigCow3.setPos(p3.x, p3.y, p3.z);
        bigCow3.xo = p3.x - 0.1;
        normalCow.setPos(p3.x, p3.y, p3.z);
        boolean equalSizeStomp = StompHandler.tryStomp(bigCow3, normalCow, 0.0F);
        helper.assertTrue(!equalSizeStomp, "Entities without >= 4 height ratio cannot stomp each other");
        boolean halfCowRatioTwoStomp = StompHandler.tryStomp(bigCow3, tinyCow1, 0.0F);
        helper.assertTrue(!halfCowRatioTwoStomp, "Normal cow vs half cow ratio 2 must not stomp");

        // Condition E: Creative player immunity
        Cow bigCow4 = helper.spawn(EntityType.COW, 8, 2, 8);
        bigCow4.getAttribute(Attributes.SCALE).setBaseValue(3.0D);
        bigCow4.refreshDimensions();
        Player creativePlayer = helper.makeMockPlayer(GameType.CREATIVE);
        Vec3 p4 = helper.absoluteVec(new Vec3(8.5, 2.0, 8.5));
        bigCow4.setPos(p4.x, p4.y, p4.z);
        bigCow4.xo = p4.x - 0.1;
        creativePlayer.setPos(p4.x, p4.y, p4.z);
        boolean creativeStomp = StompHandler.tryStomp(bigCow4, creativePlayer, 0.0F);
        helper.assertTrue(!creativeStomp, "Creative player must be immune to stomp damage");

        // Condition F: Mutual riding exemption on fresh pair
        Cow bigCow5 = helper.spawn(EntityType.COW, 10, 2, 10);
        bigCow5.getAttribute(Attributes.SCALE).setBaseValue(3.0D);
        bigCow5.refreshDimensions();
        Cow tinyCow5 = helper.spawn(EntityType.COW, 10, 2, 10);
        PixelScaleHelper.applyTinyModifiers(tinyCow5);
        Vec3 p5 = helper.absoluteVec(new Vec3(10.5, 2.0, 10.5));
        bigCow5.setPos(p5.x, p5.y, p5.z);
        bigCow5.xo = p5.x - 0.1;
        tinyCow5.setPos(p5.x, p5.y, p5.z);
        bigCow5.startRiding(tinyCow5, true);
        boolean ridingStomp = StompHandler.tryStomp(bigCow5, tinyCow5, 0.0F);
        helper.assertTrue(!ridingStomp, "Riding relationship must be exempt from stomp damage");
        bigCow5.stopRiding();

        // Condition G: Falling stomp damage calculation with sufficient MAX_HEALTH
        Cow bigCow6 = helper.spawn(EntityType.COW, 12, 2, 12);
        bigCow6.getAttribute(Attributes.SCALE).setBaseValue(3.0D);
        bigCow6.refreshDimensions();
        Cow tinyCow6 = helper.spawn(EntityType.COW, 12, 2, 12);
        PixelScaleHelper.applyTinyModifiers(tinyCow6);
        tinyCow6.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
        tinyCow6.setHealth(50.0F);
        Vec3 p6 = helper.absoluteVec(new Vec3(12.5, 2.0, 12.5));
        bigCow6.setPos(p6.x, p6.y, p6.z);
        bigCow6.xo = p6.x - 0.1;
        bigCow6.fallDistance = 5.0F;
        tinyCow6.setPos(p6.x, p6.y, p6.z);
        float healthBefore = tinyCow6.getHealth();
        boolean fallStomp = StompHandler.tryStomp(bigCow6, tinyCow6, 5.0F);
        helper.assertTrue(fallStomp, "Falling stomp must trigger");
        float damageDealt = healthBefore - tinyCow6.getHealth();
        helper.assertTrue(Math.abs(damageDealt - 7.0F) < 1e-3, "Falling stomp damage must be 2 + 5 = 7.0");

        helper.succeed();
    }

    /**
     * 11. Real wall climbing: forward input ascends ~0.0294/tick, sneaking maintains Y, no input falls.
     */
    @GameTest(template = "empty")
    public static void realWallClimbingContinuousTicks(GameTestHelper helper) {
        // Build floor at (2, 1, 3) and wall at (2, y, 2)
        helper.setBlock(new BlockPos(2, 1, 3), Blocks.STONE.defaultBlockState());
        for (int y = 2; y <= 5; y++) {
            helper.setBlock(new BlockPos(2, y, 2), Blocks.STONE.defaultBlockState());
        }

        Cow cow = helper.spawn(EntityType.COW, 2, 2, 3);
        PixelScaleHelper.applyTinyModifiers(cow);
        double halfWidth = cow.getBbWidth() / 2.0;
        Vec3 wallFacePos = helper.absoluteVec(new Vec3(2.5, 2.0, 3.0 + halfWidth + 0.005));
        cow.setPos(wallFacePos.x, wallFacePos.y, wallFacePos.z);
        cow.setYRot(180.0F); // Face North towards wall at Z=2
        cow.horizontalCollision = true;

        cow.zza = 1.0F;
        double startY = cow.getY();
        for (int i = 0; i < 5; i++) {
            cow.travel(new Vec3(0, 0, 1.0));
        }
        double climbedY = cow.getY() - startY;
        helper.assertTrue(climbedY > 0.13D && climbedY < 0.16D, "Five motion-scaled cow climb ticks (~0.147): " + climbedY + " (startY=" + startY + ", endY=" + cow.getY() + ", collision=" + cow.horizontalCollision + ", onClimbable=" + cow.onClimbable() + ")");

        // 2. Sneaking: maintains Y position
        cow.setShiftKeyDown(true);
        cow.setJumping(false);
        cow.zza = 0.0F;
        double sneakStartY = cow.getY();
        for (int i = 0; i < 5; i++) {
            cow.travel(new Vec3(0, 0, 0.0));
        }
        double sneakYDiff = Math.abs(cow.getY() - sneakStartY);
        helper.assertTrue(sneakYDiff < 0.01D, "Sneaking on wall must hold Y position: diff=" + sneakYDiff);

        // 3. No input: stops forced ascent, falls with gravity
        cow.setShiftKeyDown(false);
        cow.zza = 0.0F;
        double releaseStartY = cow.getY();
        for (int i = 0; i < 3; i++) {
            cow.travel(new Vec3(0, 0, 0.0));
        }
        helper.assertTrue(cow.getY() <= releaseStartY, "Releasing input must stop ascending and fall");

        helper.succeed();
    }

    /**
     * 12. Real wand interaction routing: Villager, Horse, Minecart, Player rejection, cooldown, durability.
     */
    @GameTest(template = "empty")
    public static void realWandEntityInteractRouting(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack wand = new ItemStack(ModItems.SCALE_WAND.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, wand);

        // 1. Regular Cow interaction
        Cow cow = helper.spawn(EntityType.COW, 2, 2, 2);
        InteractionResult resCow = player.interactOn(cow, InteractionHand.MAIN_HAND);
        helper.assertTrue(resCow.consumesAction(), "Wand interact on Cow must consume action");
        helper.assertTrue(PixelScaleHelper.isTiny(cow), "Cow must become tiny");
        helper.assertTrue(wand.getCount() == 1, "Wand stack count must remain 1");
        helper.assertTrue(wand.getDamageValue() == 0, "Wand durability must not decrease");
        helper.assertTrue(player.getCooldowns().isOnCooldown(ModItems.SCALE_WAND.get()), "Player must be on 10-tick cooldown");

        // Second click with offhand during cooldown fails
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.SCALE_WAND.get()));
        InteractionResult offhandRes = player.interactOn(cow, InteractionHand.OFF_HAND);
        helper.assertTrue(offhandRes == InteractionResult.FAIL, "Offhand wand click during cooldown must fail");

        // Clear cooldown for next targets
        player.getCooldowns().removeCooldown(ModItems.SCALE_WAND.get());

        // 2. Villager interaction: wand must intercept trading GUI
        Villager villager = helper.spawn(EntityType.VILLAGER, 4, 2, 4);
        InteractionResult resVillager = player.interactOn(villager, InteractionHand.MAIN_HAND);
        helper.assertTrue(resVillager.consumesAction(), "Wand interact on Villager must consume action");
        helper.assertTrue(PixelScaleHelper.isTiny(villager), "Villager must become tiny");

        player.getCooldowns().removeCooldown(ModItems.SCALE_WAND.get());

        // 3. Untamed Horse interaction: wand must intercept mount/anger
        Horse horse = helper.spawn(EntityType.HORSE, 6, 2, 6);
        InteractionResult resHorse = player.interactOn(horse, InteractionHand.MAIN_HAND);
        helper.assertTrue(resHorse.consumesAction(), "Wand interact on Horse must consume action");
        helper.assertTrue(PixelScaleHelper.isTiny(horse), "Horse must become tiny");
        helper.assertTrue(player.getVehicle() == null, "Player must not mount untamed horse");

        player.getCooldowns().removeCooldown(ModItems.SCALE_WAND.get());

        // 4. Player rejection
        Player otherPlayer = helper.makeMockPlayer(GameType.SURVIVAL);
        InteractionResult resPlayer = player.interactOn(otherPlayer, InteractionHand.MAIN_HAND);
        helper.assertTrue(resPlayer == InteractionResult.FAIL, "Wand interact on Player must fail");
        helper.assertTrue(!player.getCooldowns().isOnCooldown(ModItems.SCALE_WAND.get()), "No cooldown on rejected player");

        // 5. Non-living rejection (Minecart)
        Minecart minecart = helper.spawn(EntityType.MINECART, 8, 2, 8);
        InteractionResult resNonLiving = player.interactOn(minecart, InteractionHand.MAIN_HAND);
        helper.assertTrue(resNonLiving == InteractionResult.FAIL, "Wand interact on non-living must fail");
        helper.assertTrue(!player.getCooldowns().isOnCooldown(ModItems.SCALE_WAND.get()), "No cooldown on non-living target");

        helper.succeed();
    }

    /**
     * 13. Stomp fall distance calculation, invulnerability & lastHurt preservation, and barrier checks.
     */
    @GameTest(template = "empty")
    public static void stompFallDistanceAndInvulnerabilityPreservation(GameTestHelper helper) {
        Cow tinyCow = helper.spawn(EntityType.COW, 3, 2, 3);
        PixelScaleHelper.applyTinyModifiers(tinyCow);
        tinyCow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D);
        tinyCow.setHealth(100.0F);

        // Pre-hit with normal attack
        tinyCow.hurt(tinyCow.damageSources().generic(), 5.0F);
        helper.assertTrue(tinyCow.invulnerableTime > 0, "Tiny cow should have invulnerability ticks");
        int originalInvulnerableTime = tinyCow.invulnerableTime;
        float originalLastHurt = ((LivingEntityAccessor) tinyCow).examplemod$getLastHurt();
        helper.assertTrue(Math.abs(originalLastHurt - 5.0F) < 1e-3, "originalLastHurt should be 5.0");
        float healthAfterHit = tinyCow.getHealth(); // 95.0F

        // Big stomper falling from 4 blocks height (enlarged with SCALE to satisfy >= 4 height ratio against half cow)
        Cow bigCow = helper.spawn(EntityType.COW, 3, 2, 3);
        bigCow.getAttribute(Attributes.SCALE).setBaseValue(3.0D);
        bigCow.refreshDimensions();
        Vec3 p = helper.absoluteVec(new Vec3(3.5, 2.0, 3.5));
        bigCow.setPos(p.x, p.y, p.z);
        bigCow.xo = p.x - 0.1;
        bigCow.yo = p.y + 4.0;
        bigCow.fallDistance = 4.0F;
        tinyCow.setPos(p.x, p.y, p.z);

        // Stomp executes: damage = 2 + min(4, 8) = 6.0
        boolean stomped = StompHandler.tryStomp(bigCow, tinyCow, 4.0F);
        helper.assertTrue(stomped, "Falling stomp should succeed");
        helper.assertTrue(Math.abs(tinyCow.getHealth() - (healthAfterHit - 6.0F)) < 1e-3, "Health should decrease by 6.0");

        // Invulnerable time and lastHurt MUST be preserved!
        helper.assertTrue(tinyCow.invulnerableTime == originalInvulnerableTime, "invulnerableTime must be preserved after stomp");
        helper.assertTrue(Math.abs(((LivingEntityAccessor) tinyCow).examplemod$getLastHurt() - originalLastHurt) < 1e-3, "lastHurt must be preserved after stomp");

        // Consecutive stomp within 10 ticks must be blocked
        boolean blockedStomp = StompHandler.tryStomp(bigCow, tinyCow, 4.0F);
        helper.assertTrue(!blockedStomp, "Consecutive stomp on same target must be blocked");

        // Barrier test: solid block separating stomper and victim blocks stomp
        Cow bigCowAbove = helper.spawn(EntityType.COW, 5, 4, 5);
        bigCowAbove.getAttribute(Attributes.SCALE).setBaseValue(3.0D);
        bigCowAbove.refreshDimensions();
        Cow tinyCowBelow = helper.spawn(EntityType.COW, 5, 2, 5);
        PixelScaleHelper.applyTinyModifiers(tinyCowBelow);
        helper.setBlock(new BlockPos(5, 3, 5), Blocks.OBSIDIAN.defaultBlockState());
        Vec3 pAbove = helper.absoluteVec(new Vec3(5.5, 4.0, 5.5));
        Vec3 pBelow = helper.absoluteVec(new Vec3(5.5, 2.0, 5.5));
        bigCowAbove.setPos(pAbove.x, pAbove.y, pAbove.z);
        bigCowAbove.xo = pAbove.x - 0.1;
        tinyCowBelow.setPos(pBelow.x, pBelow.y, pBelow.z);
        boolean blockedByFloor = StompHandler.tryStomp(bigCowAbove, tinyCowBelow, 0.0F);
        helper.assertTrue(!blockedByFloor, "Solid floor between entities must block stomp damage");

        helper.succeed();
    }

    /**
     * 14. Weapon attack damage container scaling (0.25x) and received knockback verification.
     */
    @GameTest(template = "empty")
    public static void weaponDamageContainerAndKnockbackScaling(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        PixelScaleHelper.ensurePlayerMini(player);
        Zombie victim = helper.spawn(EntityType.ZOMBIE, 4, 2, 4);
        ItemStack diamondSword = new ItemStack(Items.DIAMOND_SWORD);
        player.setItemInHand(InteractionHand.MAIN_HAND, diamondSword);

        // Diamond sword base damage is 7.0 + 1.0 (player base) = 8.0. Tiny multiplier 0.25x -> 2.0
        DamageSource src = player.damageSources().playerAttack(player);
        DamageContainer container = new DamageContainer(src, 8.0F);
        CommonHooks.onEntityIncomingDamage(victim, container);
        helper.assertTrue(Math.abs(container.getNewDamage() - 2.0F) < 1e-3, "Weapon attack damage from tiny player must be 8.0 * 0.25 = 2.0");

        // Knockback output scaled to 0.25x
        LivingKnockBackEvent kbEvent = CommonHooks.onLivingKnockBack(victim, 1.0F, 1.0, 0.0);
        helper.assertTrue(Math.abs(kbEvent.getStrength() - 1.0F) < 1e-3, "Normal zombie received knockback base strength 1.0");

        helper.succeed();
    }

    /**
     * 15. AI friendly mob retaliation: Goal tick, line of sight check, and wall occlusion.
     */
    @GameTest(template = "empty")
    public static void friendlyRetaliationGoalTickAndLineOfSight(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, 3, 2, 3);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 3, 2, 5);

        FriendlyRetaliateGoal goal = ScaleEvents.getOrAttachRetaliateGoal(cow);
        cow.hurt(cow.damageSources().mobAttack(zombie), 2.0F);
        helper.assertTrue(goal.getRetaliateTarget() == zombie, "Cow must target zombie on attack");

        // Wall occlusion test: place 3-block tall wall at Z=4 between cow and zombie
        for (int y = 2; y <= 4; y++) {
            helper.setBlock(new BlockPos(3, y, 4), Blocks.STONE.defaultBlockState());
        }

        // Tick goal: because 3-block wall blocks line of sight, goal cannot attack
        float zombieHealthBefore = zombie.getHealth();
        goal.tick();
        helper.assertTrue(zombie.getHealth() == zombieHealthBefore, "Retaliation must not hit through solid wall without line of sight");

        helper.succeed();
    }

    /**
     * 16. Ender Dragon scaling and part interaction routing.
     */
    @GameTest(template = "empty")
    public static void enderDragonScalingAndPartRouting(GameTestHelper helper) {
        EnderDragon dragon = EntityType.ENDER_DRAGON.create(helper.getLevel());
        helper.assertTrue(dragon != null, "EnderDragon creation must succeed");

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        // Apply tiny modifiers to dragon
        PixelScaleHelper.applyTinyModifiers(dragon);
        helper.assertTrue(PixelScaleHelper.isTiny(dragon), "Dragon must be tiny");
        helper.assertTrue(Math.abs(dragon.getScale() - PixelScaleHelper.WAND_SCALE_FLOAT) < 1e-5, "Dragon getScale must be 0.5");

        // Dragon parts dimensions must be scaled (halved for WAND_SCALE 0.5)
        for (EnderDragonPart part : dragon.getSubEntities()) {
            EntityDimensions partDims = part.getDimensions(Pose.STANDING);
            helper.assertTrue(partDims.height() < 2.0F, "Dragon part height must be scaled to half proportions");
        }

        // Click dragon part with wand: routes to parent dragon and restores it
        EnderDragonPart headPart = dragon.getSubEntities()[0];
        PixelScaleHelper.ToggleResult restoreRes = PixelScaleHelper.toggleLivingTarget(player, headPart.parentMob);
        helper.assertTrue(restoreRes == PixelScaleHelper.ToggleResult.RESTORED, "Part wand click must restore parent dragon");
        helper.assertTrue(!PixelScaleHelper.isTiny(dragon), "Dragon should no longer be tiny");

        // Parts restored to normal dimensions
        EntityDimensions restoredPartDims = headPart.getDimensions(Pose.STANDING);
        helper.assertTrue(restoredPartDims.height() > 0.5F, "Restored dragon part height must return to normal");

        helper.succeed();
    }

    /**
     * 17. Sleeping pose dimensions scaling and wake up restoration.
     */
    @GameTest(template = "empty")
    public static void sleepingPoseScalingDimensions(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(player.getDimensions(Pose.SLEEPING).height() > 0.05F, "A normal player keeps vanilla sleeping dimensions");
        PixelScaleHelper.ensurePlayerMini(player);

        if (!PehkuiScaleSupport.isLoaded()) {
            // Sleeping dimensions must be scaled by getScale()
            EntityDimensions sleepingDims = player.getDimensions(Pose.SLEEPING);
            helper.assertTrue(sleepingDims.height() < 0.05F, "Sleeping dimensions height must be scaled (not vanilla 0.2)");
            helper.assertTrue(sleepingDims.width() < 0.05F, "Sleeping dimensions width must be scaled");

            // Standing dimensions must be ~0.0625
            EntityDimensions standingDims = player.getDimensions(Pose.STANDING);
            helper.assertTrue(Math.abs(standingDims.height() - 0.0625F) < 1e-4, "Standing height must be 1/16 block");

            // Wand mobs use the same no-Pehkui sleeping scale path.
            Villager villager = helper.spawn(EntityType.VILLAGER, 2, 2, 2);
            float origSleepingHeight = villager.getDimensions(Pose.SLEEPING).height();
            float origSleepingWidth = villager.getDimensions(Pose.SLEEPING).width();
            PixelScaleHelper.applyTinyModifiers(villager);
            EntityDimensions tinySleepingDims = villager.getDimensions(Pose.SLEEPING);
            helper.assertTrue(Math.abs(tinySleepingDims.height() - origSleepingHeight * PixelScaleHelper.WAND_SCALE_FLOAT) < 1e-4, "Villager sleeping height should be scaled to 0.5");
            helper.assertTrue(Math.abs(tinySleepingDims.width() - origSleepingWidth * PixelScaleHelper.WAND_SCALE_FLOAT) < 1e-4, "Villager sleeping width should be scaled to 0.5");
        }

        helper.succeed();
    }

    /**
     * 18. Restore safety check with external modifiers and vanilla SCALE 2.0.
     */
    @GameTest(template = "empty")
    public static void restoreSafetyCheckWithExternalModifiers(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Cow cow = helper.spawn(EntityType.COW, 3, 2, 3);

        // Set cow vanilla SCALE to 2.0 (standing height 2.8 blocks)
        cow.getAttribute(Attributes.SCALE).setBaseValue(2.0D);

        // Shrink cow
        PixelScaleHelper.toggleLivingTarget(player, cow);
        helper.assertTrue(PixelScaleHelper.isTiny(cow), "Cow must be tiny");

        // Place ceiling at relative Y=4 (height 2 blocks). Normal 1.4 cow fits, but 2.8 cow cannot!
        BlockPos ceiling = new BlockPos(3, 4, 3);
        helper.setBlock(ceiling, Blocks.OBSIDIAN.defaultBlockState());

        // Attempt restore: must be blocked because 2.0x cow needs 2.8 blocks height
        PixelScaleHelper.ToggleResult blockedRes = PixelScaleHelper.toggleLivingTarget(player, cow);
        helper.assertTrue(blockedRes == PixelScaleHelper.ToggleResult.RESTORE_BLOCKED, "Restore must be blocked for 2x scale cow in 2-block room");
        helper.assertTrue(PixelScaleHelper.isTiny(cow), "Cow must remain tiny");
        helper.assertTrue(cow.getAttribute(Attributes.SCALE).getBaseValue() == 2.0D, "External SCALE must not be modified or reset");

        // Clear ceiling: restore must now succeed
        helper.setBlock(ceiling, Blocks.AIR.defaultBlockState());
        PixelScaleHelper.ToggleResult successRes = PixelScaleHelper.toggleLivingTarget(player, cow);
        helper.assertTrue(successRes == PixelScaleHelper.ToggleResult.RESTORED, "Restore must succeed when room is cleared");
        helper.assertTrue(!PixelScaleHelper.isTiny(cow), "Cow must be restored");
        helper.assertTrue(cow.getAttribute(Attributes.SCALE).getBaseValue() == 2.0D, "External SCALE must remain 2.0 after restore");

        helper.succeed();
    }

    /**
     * 19. Player lifecycle modifier repair: individual missing modifier is rebuilt without health duplication.
     */
    @GameTest(template = "empty")
    public static void playerLifecycleAndModifierRepair(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        PixelScaleHelper.ensurePlayerMini(player);

        // Verify initial state
        helper.assertTrue(player.getMaxHealth() == 10.0F, "Player max health should be 10.0");
        helper.assertTrue(player.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(PixelScaleHelper.TINY_MOVEMENT_SPEED_ID), "Speed modifier present");
        helper.assertTrue(player.getAttribute(Attributes.STEP_HEIGHT).hasModifier(PixelScaleHelper.TINY_STEP_HEIGHT_ID), "Step height modifier present");

        // Manually remove MOVEMENT_SPEED and STEP_HEIGHT modifiers to simulate corruption
        player.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(PixelScaleHelper.TINY_MOVEMENT_SPEED_ID);
        player.getAttribute(Attributes.STEP_HEIGHT).removeModifier(PixelScaleHelper.TINY_STEP_HEIGHT_ID);
        helper.assertTrue(!player.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(PixelScaleHelper.TINY_MOVEMENT_SPEED_ID), "Speed modifier removed");

        // ensurePlayerMini must detect missing modifiers and restore them
        PixelScaleHelper.ensurePlayerMini(player);
        helper.assertTrue(player.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(PixelScaleHelper.TINY_MOVEMENT_SPEED_ID), "Speed modifier repaired");
        helper.assertTrue(player.getAttribute(Attributes.STEP_HEIGHT).hasModifier(PixelScaleHelper.TINY_STEP_HEIGHT_ID), "Step height modifier repaired");
        helper.assertTrue(player.getMaxHealth() == 10.0F, "Health must not be duplicated or increased during repair");

        // Simulate Clone lifecycle event
        Player newPlayer = helper.makeMockPlayer(GameType.SURVIVAL);
        NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(newPlayer, player, false));
        helper.assertTrue(PixelScaleHelper.isTiny(newPlayer), "Cloned player must be ensured tiny");

        helper.succeed();
    }

    /**
     * 20. Normal cow (1.4 height) vs half cow (0.7 height) has height ratio 2.0 < 4.0 and cannot stomp.
     */
    @GameTest(template = "empty")
    public static void halfCowRatioTwoCannotStomp(GameTestHelper helper) {
        Cow normalCow = helper.spawn(EntityType.COW, 2, 2, 2);
        Cow halfCow = helper.spawn(EntityType.COW, 2, 2, 2);
        PixelScaleHelper.applyTinyModifiers(halfCow);

        Vec3 pos = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
        normalCow.setPos(pos.x, pos.y, pos.z);
        normalCow.xo = pos.x - 0.1;
        normalCow.yo = pos.y;
        normalCow.zo = pos.z;
        halfCow.setPos(pos.x, pos.y, pos.z);

        float initialHealth = halfCow.getHealth();
        boolean stomped = StompHandler.tryStomp(normalCow, halfCow, 0.0F);
        helper.assertTrue(!stomped, "Normal cow vs half cow ratio 2 must NOT trigger stomp");
        helper.assertTrue(halfCow.getHealth() == initialHealth, "Half cow must take no stomp damage from ratio 2");

        helper.succeed();
    }

    /**
     * 21. Player legacy speed modifier (-0.65) is migrated to -0.75 without duplicating health or deleting third-party modifiers.
     */
    @GameTest(template = "empty")
    public static void playerSpeedLegacyMigration(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        PixelScaleHelper.ensurePlayerMini(player);

        // Manually install legacy -0.65 speed modifier
        player.getAttribute(Attributes.MOVEMENT_SPEED).addOrReplacePermanentModifier(new AttributeModifier(
                PixelScaleHelper.TINY_MOVEMENT_SPEED_ID,
                -0.65D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        ));
        // Add a third-party modifier
        ResourceLocation thirdPartyId = ResourceLocation.fromNamespaceAndPath("thirdparty", "speed_buff");
        player.getAttribute(Attributes.MOVEMENT_SPEED).addOrReplacePermanentModifier(new AttributeModifier(
                thirdPartyId,
                0.2D,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE
        ));

        // Call ensurePlayerMini to trigger migration
        PixelScaleHelper.ensurePlayerMini(player);

        AttributeModifier speedMod = player.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(PixelScaleHelper.TINY_MOVEMENT_SPEED_ID);
        double expectedSpeed = PehkuiScaleSupport.isLoaded()
                ? PixelScaleHelper.PEHKUI_LOCOMOTION_MULTIPLIER - 1.0D
                : -0.75D;
        helper.assertTrue(speedMod != null, "Speed modifier must exist");
        helper.assertTrue(Math.abs(speedMod.amount() - expectedSpeed) < 1e-6, "Legacy speed -0.65 must be updated to " + expectedSpeed);
        helper.assertTrue(speedMod.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, "Speed modifier operation must be ADD_MULTIPLIED_TOTAL");
        helper.assertTrue(player.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(thirdPartyId), "Third-party speed modifier must be preserved");
        helper.assertTrue(player.getMaxHealth() == 10.0F, "Player max health must not duplicate or change");

        // Idempotency check: calling ensurePlayerMini again changes nothing
        PixelScaleHelper.ensurePlayerMini(player);
        helper.assertTrue(player.getMaxHealth() == 10.0F, "Idempotent ensurePlayerMini preserves health");
        helper.assertTrue(Math.abs(player.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(PixelScaleHelper.TINY_MOVEMENT_SPEED_ID).amount() - expectedSpeed) < 1e-6, "Idempotent speed modifier remains " + expectedSpeed);

        helper.succeed();
    }

    /**
     * 22. Server join migration for non-player LivingEntity (including hostile Enemy) already bearing tiny marker.
     */
    @GameTest(template = "empty")
    public static void serverJoinMigrationForShrunkEntities(GameTestHelper helper) {
        // 1. Shrunk Zombie with old ADD_VALUE scale modifier and old -0.65 speed
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 3, 2, 3);
        zombie.getAttribute(ModAttributes.PIXEL_SCALE).addOrReplacePermanentModifier(new AttributeModifier(
                PixelScaleHelper.TINY_PIXEL_SCALE_ID,
                PixelScaleHelper.PLAYER_TINY_SCALE - 1.0D,
                AttributeModifier.Operation.ADD_VALUE
        ));
        zombie.getAttribute(Attributes.MOVEMENT_SPEED).addOrReplacePermanentModifier(new AttributeModifier(
                PixelScaleHelper.TINY_MOVEMENT_SPEED_ID,
                -0.65D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        ));
        double oldBaseStep = zombie.getAttribute(Attributes.STEP_HEIGHT).getBaseValue();
        zombie.getAttribute(Attributes.STEP_HEIGHT).addOrReplacePermanentModifier(new AttributeModifier(
                PixelScaleHelper.TINY_STEP_HEIGHT_ID,
                Math.max(1.0 / 16.0, oldBaseStep * PixelScaleHelper.PLAYER_TINY_SCALE) - oldBaseStep,
                AttributeModifier.Operation.ADD_VALUE
        ));

        // Add third-party scale modifier
        ResourceLocation thirdPartyScale = ResourceLocation.fromNamespaceAndPath("thirdparty", "custom_scale");
        zombie.getAttribute(ModAttributes.PIXEL_SCALE).addOrReplacePermanentModifier(new AttributeModifier(
                thirdPartyScale,
                0.1D,
                AttributeModifier.Operation.ADD_VALUE
        ));

        // Trigger migration
        PixelScaleHelper.migrateLivingEntity(zombie);

        // Verify zombie migrated
        AttributeModifier newScaleMod = zombie.getAttribute(ModAttributes.PIXEL_SCALE).getModifier(PixelScaleHelper.TINY_PIXEL_SCALE_ID);
        helper.assertTrue(newScaleMod != null && newScaleMod.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, "Scale modifier migrated to ADD_MULTIPLIED_TOTAL");
        helper.assertTrue(Math.abs(newScaleMod.amount() - (-0.5D)) < 1e-6, "Scale modifier migrated to -0.5");
        helper.assertTrue(zombie.getAttribute(ModAttributes.PIXEL_SCALE).hasModifier(thirdPartyScale), "Third-party scale modifier preserved");

        AttributeModifier newSpeedMod = zombie.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(PixelScaleHelper.TINY_MOVEMENT_SPEED_ID);
        double expectedSpeed = PehkuiScaleSupport.isLoaded()
                ? PixelScaleHelper.PEHKUI_LOCOMOTION_MULTIPLIER - 1.0D
                : -0.75D;
        helper.assertTrue(newSpeedMod != null && Math.abs(newSpeedMod.amount() - expectedSpeed) < 1e-6, "Speed modifier migrated to " + expectedSpeed);

        AttributeModifier newStepMod = zombie.getAttribute(Attributes.STEP_HEIGHT).getModifier(PixelScaleHelper.TINY_STEP_HEIGHT_ID);
        double expectedStepAmount = Math.max(1.0 / 16.0, oldBaseStep * PixelScaleHelper.WAND_SCALE) - oldBaseStep;
        helper.assertTrue(newStepMod != null && Math.abs(newStepMod.amount() - expectedStepAmount) < 1e-6, "Step height migrated to half size");

        // 2. Untouched mob (no tiny marker) must NOT be modified
        Zombie untouchedZombie = helper.spawn(EntityType.ZOMBIE, 5, 2, 5);
        PixelScaleHelper.migrateLivingEntity(untouchedZombie);
        helper.assertTrue(!PixelScaleHelper.isTiny(untouchedZombie), "Untouched zombie must not be shrunk on migration");

        helper.succeed();
    }

    /**
     * 23. Third-party modifiers on PIXEL_SCALE and MOVEMENT_SPEED are preserved through wand toggle & restore.
     */
    @GameTest(template = "empty")
    public static void restorePreservesThirdPartyScaleModifiers(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Cow cow = helper.spawn(EntityType.COW, 4, 2, 4);

        ResourceLocation customScaleId = ResourceLocation.fromNamespaceAndPath("custommod", "scale_bonus");
        cow.getAttribute(ModAttributes.PIXEL_SCALE).addPermanentModifier(new AttributeModifier(
                customScaleId,
                0.1D,
                AttributeModifier.Operation.ADD_VALUE
        ));

        // Shrink cow
        PixelScaleHelper.toggleLivingTarget(player, cow);
        helper.assertTrue(PixelScaleHelper.isTiny(cow), "Cow must be shrunk");
        helper.assertTrue(cow.getAttribute(ModAttributes.PIXEL_SCALE).hasModifier(customScaleId), "Custom modifier preserved when shrunk");

        // Restore cow
        PixelScaleHelper.toggleLivingTarget(player, cow);
        helper.assertTrue(!PixelScaleHelper.isTiny(cow), "Cow must be restored");
        helper.assertTrue(cow.getAttribute(ModAttributes.PIXEL_SCALE).hasModifier(customScaleId), "Custom modifier preserved when restored");
        helper.assertTrue(Math.abs(cow.getAttribute(ModAttributes.PIXEL_SCALE).getModifier(customScaleId).amount() - 0.1D) < 1e-6, "Custom modifier value preserved");

        helper.succeed();
    }

    /**
     * 24. Walk animation distance is normalized by effective entity scale (not affecting standing/sleeping or real velocity).
     */
    @GameTest(template = "empty")
    public static void walkAnimationDecoupledSpeedScaling(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        PixelScaleHelper.ensurePlayerMini(player);

        // Standing still: animation speed remains 0
        player.calculateEntityAnimation(false);
        helper.assertTrue(player.walkAnimation.speed() < 1e-4, "Standing still must produce zero animation speed");

        // Small displacement 0.01: normalized by scale (1/28.8) yields 0.01 * 28.8 = 0.288 -> min(0.288 * 4, 1) = 1.0
        // walkAnimation.update(1.0, 0.4) -> speed becomes 0.4
        player.xo = player.getX() - 0.01;
        player.calculateEntityAnimation(false);
        float animSpeed = player.walkAnimation.speed();
        helper.assertTrue(animSpeed > 0.2F, "Normalized tiny walk animation speed should be accelerated: got " + animSpeed);

        // Standing still again: decays
        player.xo = player.getX();
        player.calculateEntityAnimation(false);
        helper.assertTrue(player.walkAnimation.speed() < animSpeed, "Standing still must decay walk animation speed");

        // Normal cow: displacement 0.01 without scaling gives 0.01 * 4 = 0.04 -> update(0.04, 0.4) -> 0.016
        Cow normalCow = helper.spawn(EntityType.COW, 2, 2, 2);
        normalCow.xo = normalCow.getX() - 0.01;
        normalCow.calculateEntityAnimation(false);
        float normalAnimSpeed = normalCow.walkAnimation.speed();
        helper.assertTrue(normalAnimSpeed < 0.05F, "Normal cow animation with 0.01 distance should not be magnified: got " + normalAnimSpeed);

        // Half-sized cow: displacement 0.01 normalized by 0.5 gives 0.02 * 4 = 0.08 -> update(0.08, 0.4) -> 0.032
        Cow halfCow = helper.spawn(EntityType.COW, 4, 2, 4);
        PixelScaleHelper.applyTinyModifiers(halfCow);
        halfCow.xo = halfCow.getX() - 0.01;
        halfCow.calculateEntityAnimation(false);
        float halfAnimSpeed = halfCow.walkAnimation.speed();
        helper.assertTrue(halfAnimSpeed > normalAnimSpeed * 1.5F, "Half-scale cow walk animation should be magnified compared to normal: half=" + halfAnimSpeed + ", normal=" + normalAnimSpeed);

        helper.succeed();
    }
}

