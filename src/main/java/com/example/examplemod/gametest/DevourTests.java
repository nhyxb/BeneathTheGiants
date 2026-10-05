package com.example.examplemod.gametest;

import com.example.examplemod.ExampleMod;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import com.example.examplemod.config.SurvivalConfig;
import com.example.examplemod.devour.DevourRules;
import com.example.examplemod.devour.DevourPlayerAccess;
import com.example.examplemod.init.ModAttributes;
import com.example.examplemod.init.ModDamageTypes;
import com.example.examplemod.init.ModTags;
import com.example.examplemod.mixin.LivingEntityAccessor;
import com.example.examplemod.scale.PixelScaleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ExampleMod.MODID)
@PrefixGameTestTemplate(false)
public final class DevourTests {
    @GameTest(template = "empty")
    public static void acceptedMeleeActuallyTriggersCapture(GameTestHelper h) {
        Zombie mob = zombie(h);
        Player p = player(h, GameType.SURVIVAL);
        p.setHealth(2.5F);
        withCertainChance(() -> p.hurt(p.damageSources().mobAttack(mob), 1));
        h.assertTrue(p.isAlive() && p.getHealth() <= 2, "Accepted melee leaves living player at threshold");
        h.assertTrue(DevourRules.isCaptured(p) && p.getVehicle() == mob, "Actual hurt post-event captures and mounts player");
        DevourRules.release(p);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void villagersAndWanderingTradersAreHumanoidDevourers(GameTestHelper h) {
        for (EntityType<? extends Mob> type : java.util.List.of(EntityType.VILLAGER, EntityType.WANDERING_TRADER)) {
            Mob villager = h.spawn(type, new Vec3(4, 2, 4));
            villager.setNoAi(true);
            villager.setNoGravity(true);
            Player p = player(h, GameType.SURVIVAL);
            p.setHealth(2.5F);
            h.assertTrue(type.is(ModTags.HUMANOID_DEVOURERS), "Villager family belongs to humanoid tag: " + type);
            withCertainChance(() -> p.hurt(p.damageSources().mobAttack(villager), 1));
            h.assertTrue(DevourRules.isCaptured(p) && p.getVehicle() == villager,
                    "Accepted villager-family melee captures a low-health player: " + type);
            DevourRules.release(p);
            villager.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void provokedVillagerActuallyRetaliatesAndCapturesInWorldTicks(GameTestHelper h) {
        for (int x = 1; x <= 10; x++) for (int z = 1; z <= 10; z++)
            h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        var villager = h.spawn(EntityType.VILLAGER, new Vec3(4, 2, 4));
        Player p = player(h, GameType.SURVIVAL);
        p.setHealth(3.5F);
        p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        villager.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        h.assertTrue(villager.hurt(villager.damageSources().playerAttack(p), 1), "Actual player damage provokes villager");
        h.succeedWhen(() -> {
            h.assertTrue(DevourRules.isCaptured(p), "Waiting for real villager retaliation capture: health=" + p.getHealth()
                    + ", player=" + p.position() + ", villager=" + villager.position());
            h.assertTrue(p.getVehicle() == villager && p.getHealth() <= 2,
                    "Registered retaliation goal and damage event capture the living low-health player");
            DevourRules.release(p);
        });
    }

    @GameTest(template = "empty")
    public static void arrowsRejectedAndAboveThresholdOrLethalMeleeCannotCapture(GameTestHelper h) {
        Zombie mob = zombie(h);
        Player arrowVictim = player(h, GameType.SURVIVAL);
        Arrow arrow = new Arrow(EntityType.ARROW, h.getLevel());
        arrow.setOwner(mob);
        arrowVictim.setHealth(2.5F);
        withCertainChance(() -> arrowVictim.hurt(arrowVictim.damageSources().arrow(arrow, mob), 1));
        h.assertTrue(!DevourRules.isCaptured(arrowVictim), "Projectile with listed mob owner cannot capture");
        Player healthy = player(h, GameType.SURVIVAL);
        healthy.setHealth(4);
        withCertainChance(() -> healthy.hurt(healthy.damageSources().mobAttack(mob), 1));
        h.assertTrue(healthy.getHealth() > 2 && !DevourRules.isCaptured(healthy), "Above two health stays unbound");
        Player lethal = player(h, GameType.SURVIVAL);
        lethal.setHealth(1);
        withCertainChance(() -> lethal.hurt(lethal.damageSources().mobAttack(mob), 100));
        h.assertTrue(!lethal.isAlive() && !DevourRules.isCaptured(lethal), "Ordinary lethal attack is not rescued");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void rejectedInvulnerableMeleeDoesNotStartCapture(GameTestHelper h) {
        Zombie mob = zombie(h);
        Player p = player(h, GameType.SURVIVAL);
        p.setHealth(2);
        p.invulnerableTime = 20;
        ((LivingEntityAccessor) p).examplemod$setLastHurt(20);
        withCertainChance(() -> p.hurt(p.damageSources().mobAttack(mob), 1));
        h.assertTrue(p.getHealth() == 2 && !DevourRules.isCaptured(p), "Rejected damage does not capture low-health player");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void failureConsumesBothCooldownsAndZeroRollCanCaptureFreshPair(GameTestHelper h) {
        Zombie failed = zombie(h);
        Player p = player(h, GameType.SURVIVAL);
        h.assertTrue(!DevourRules.attemptCapture(failed, p, 1), "Roll one fails");
        h.assertTrue(!DevourRules.attemptCapture(failed, p, 0), "Same pair cannot retry immediately");
        Zombie freshMob = zombie(h);
        h.assertTrue(!DevourRules.attemptCapture(freshMob, p, 0), "Player cooldown blocks fresh mob");
        Player freshPlayer = player(h, GameType.ADVENTURE);
        h.assertTrue(!DevourRules.attemptCapture(failed, freshPlayer, 0), "Mob cooldown blocks fresh player");
        h.assertTrue(DevourRules.attemptCapture(freshMob, freshPlayer, 0), "Fresh adventure pair succeeds with zero roll");
        Player second = player(h, GameType.SURVIVAL);
        h.assertTrue(!DevourRules.attemptCapture(freshMob, second, 0), "Captor cannot bind a second player");
        DevourRules.release(freshPlayer);
        h.assertTrue(!DevourRules.attemptCapture(freshMob, freshPlayer, 0), "Release retains attempt cooldown");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void whitelistModesHealthAndSizeAreRequired(GameTestHelper h) {
        h.assertTrue(EntityType.ZOMBIE.is(ModTags.HUMANOID_DEVOURERS), "Zombie is explicitly listed");
        h.assertTrue(!EntityType.COW.is(ModTags.HUMANOID_DEVOURERS), "Cow is excluded");
        Player p = player(h, GameType.SURVIVAL);
        Mob cow = h.spawn(EntityType.COW, new Vec3(4, 2, 4));
        cow.setNoAi(true);
        h.assertTrue(!DevourRules.attemptCapture(cow, p, 0), "Unlisted mob rejected");
        for (GameType mode : new GameType[]{GameType.CREATIVE, GameType.SPECTATOR})
            h.assertTrue(!DevourRules.attemptCapture(zombie(h), player(h, mode), 0), mode + " excluded");
        // This project always forces Player to tiny scale; removing modifiers cannot create a normal-player fixture.
        p.setHealth(2.001F);
        h.assertTrue(!DevourRules.attemptCapture(zombie(h), p, 0), "Health just above two excluded");
        p.setHealth(2);
        Zombie tiny = zombie(h);
        tiny.getAttribute(ModAttributes.PIXEL_SCALE).setBaseValue(0.01);
        tiny.refreshDimensions();
        h.assertTrue(tiny.getBbHeight() < p.getBbHeight() * 4, "Undersized fixture");
        h.assertTrue(!DevourRules.attemptCapture(tiny, p, 0), "Current size ratio below four excluded");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void distanceWallAndExistingPassengersPreventCapture(GameTestHelper h) {
        Zombie mob = zombie(h);
        Player p = player(h, GameType.SURVIVAL);
        at(h, p, new Vec3(6.501, 2, 4));
        h.assertTrue(!DevourRules.attemptCapture(mob, p, 0), "Beyond 2.5 excluded");
        at(h, p, new Vec3(6.5, 2, 4));
        for (int y = 2; y <= 4; y++) h.setBlock(new BlockPos(5, y, 4), Blocks.STONE);
        h.assertTrue(!DevourRules.attemptCapture(mob, p, 0), "Solid wall blocks sight");
        for (int y = 2; y <= 4; y++) h.setBlock(new BlockPos(5, y, 4), Blocks.AIR);
        Mob vehicle = h.spawn(EntityType.COW, new Vec3(4, 2, 6));
        h.assertTrue(p.startRiding(vehicle, true), "Passenger fixture");
        h.assertTrue(!DevourRules.attemptCapture(mob, p, 0), "Already riding player excluded");
        p.stopRiding();
        mob.startRiding(vehicle, true);
        h.assertTrue(!DevourRules.attemptCapture(mob, p, 0), "Already riding captor excluded");
        mob.stopRiding();
        h.assertTrue(DevourRules.attemptCapture(mob, p, 0), "Exact 2.5 boundary accepted once sight and riding clear");
        DevourRules.release(p);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void allExternalDamageIncludingForgedDevouredIsBlocked(GameTestHelper h) {
        Zombie mob = zombie(h);
        Player p = player(h, GameType.SURVIVAL);
        capture(h, mob, p);
        float health = p.getHealth();
        DamageSource[] sources = {p.damageSources().generic(), p.damageSources().inFire(),
                p.damageSources().fall(), p.damageSources().fellOutOfWorld(), p.damageSources().genericKill(),
                p.damageSources().mobAttack(mob), ModDamageTypes.getDevouredSource(h.getLevel(), mob)};
        for (DamageSource source : sources) {
            h.assertTrue(DevourRules.shouldBlockDamage(p, source), "External source rejected: " + source.getMsgId());
            p.invulnerableTime = 0;
            p.hurt(source, 1000);
            h.assertTrue(p.isAlive() && p.getHealth() == health && DevourRules.isCaptured(p),
                    "Real hurt leaves bound player intact: " + source.getMsgId());
        }
        DevourRules.release(p);
        h.assertTrue(!DevourRules.shouldBlockDamage(p, p.damageSources().generic()), "Release removes damage immunity");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void shiftDismountAndChangingVehicleCannotEscape(GameTestHelper h) {
        Zombie mob = zombie(h);
        Player p = player(h, GameType.SURVIVAL);
        capture(h, mob, p);
        p.setShiftKeyDown(true);
        p.stopRiding();
        h.assertTrue(p.getVehicle() == mob, "Shift and stopRiding cannot escape");
        Mob other = h.spawn(EntityType.COW, new Vec3(4, 2, 6));
        h.assertTrue(!p.startRiding(other, true) && p.getVehicle() == mob, "Forced alternate vehicle cannot escape");
        DevourRules.release(p);
        h.assertTrue(!p.isPassenger() && !DevourRules.isCaptured(p), "Authorized release clears state before dismount");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 130)
    public static void worldTicksFinishWithCaptorCreditDespiteTotemResistanceAndCooldown(GameTestHelper h) {
        Zombie mob = zombie(h);
        Player p = new DeathDropPlayer(h.getLevel());
        PixelScaleHelper.ensurePlayerMini(p);
        p.setNoGravity(true);
        at(h, p, new Vec3(4.8, 2, 4));
        p.setHealth(2);
        h.getLevel().addFreshEntity(p);
        p.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 4));
        p.invulnerableTime = 100;
        capture(h, mob, p);
        int duration = SurvivalConfig.DEVOUR_DURATION.get();
        h.runAtTickTime(Math.max(1, duration / 2), () -> {
            h.assertTrue(p.isAlive() && DevourRules.isCaptured(p), "Real world ticks do not finish prematurely");
            DevourPlayerAccess state = (DevourPlayerAccess) p;
            h.assertTrue(state.examplemod$getCaptorId() == mob.getId()
                    && state.examplemod$getDevourProgress() > 0 && state.examplemod$getDevourProgress() < 1,
                    "World ticks update observable synchronized captor and progress");
            h.assertTrue(p.getOffhandItem().is(Items.TOTEM_OF_UNDYING), "Totem is actually held before terminal tick");
            p.invulnerableTime = 100;
        });
        h.runAtTickTime(duration + 5, () -> {
            h.assertTrue(!p.isAlive(), "Real world ticking completes lethal capture through mitigation");
            int totems = p.getInventory().countItem(Items.TOTEM_OF_UNDYING);
            StringBuilder drops = new StringBuilder();
            for (ItemEntity drop : h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(8))) {
                drops.append(drop.getItem().getHoverName().getString()).append(" x")
                        .append(drop.getItem().getCount()).append(" at ").append(drop.position()).append("; ");
                if (drop.getItem().is(Items.TOTEM_OF_UNDYING)) totems += drop.getItem().getCount();
            }
            h.assertTrue(totems == 1, "Totem preserved without consumption: total=" + totems
                    + ", player=" + p.position() + ", nearby drops=" + drops);
            h.assertTrue(p.getLastDamageSource() != null && p.getLastDamageSource().is(ModDamageTypes.DEVOURED)
                    && p.getLastDamageSource().getEntity() == mob, "Death source credits captor");
            h.assertTrue(p.getLastDamageSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY),
                    "Actual terminal instance bypasses invulnerability and totem protection");
            h.assertTrue(!DevourRules.isCaptured(p) && !p.isPassenger(), "Terminal state cleans binding");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void captorDeathRemovalAndTeleportEventRelease(GameTestHelper h) {
        for (int cause = 0; cause < 3; cause++) {
            Zombie mob = zombie(h);
            Player p = player(h, GameType.SURVIVAL);
            capture(h, mob, p);
            p.setRemainingFireTicks(100);
            p.fallDistance = 10;
            if (cause == 0) mob.setHealth(0);
            if (cause == 1) mob.discard();
            if (cause == 2) NeoForge.EVENT_BUS.post(new EntityTeleportEvent.TeleportCommand(mob,
                    mob.getX() + 10, mob.getY(), mob.getZ()));
            DevourRules.tick(p);
            released(h, p, "Captor interruption " + cause);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void registeredPlayerLifecycleEventsReleaseMockSession(GameTestHelper h) {
        // These validate registered server listeners, not actual login/network travel.
        for (int cause = 0; cause < 3; cause++) {
            Zombie mob = zombie(h);
            Player p = player(h, GameType.SURVIVAL);
            capture(h, mob, p);
            if (cause == 0) NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(p));
            if (cause == 1) NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(p, Level.OVERWORLD, Level.NETHER));
            if (cause == 2) NeoForge.EVENT_BUS.post(new EntityTeleportEvent.TeleportCommand(p, p.getX() + 10, p.getY(), p.getZ()));
            released(h, p, "Player lifecycle event " + cause);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void shrinkingCaptorReleasesAndAnchorRotatesWithBody(GameTestHelper h) {
        Zombie mob = zombie(h);
        Player p = player(h, GameType.SURVIVAL);
        mob.yBodyRot = 0;
        Vec3 first = DevourRules.anchorPosition(mob, p, 0);
        Vec3 mouth = DevourRules.anchorPosition(mob, p, 1);
        h.assertTrue(first.distanceTo(mouth) > 0.01, "Hand and mouth anchors are distinct");
        mob.yBodyRot = 180;
        Vec3 rotated = DevourRules.anchorPosition(mob, p, 0);
        Vec3 a = first.subtract(mob.position());
        Vec3 b = rotated.subtract(mob.position());
        h.assertTrue(Math.abs(a.x + b.x) < 0.0001 && Math.abs(a.z + b.z) < 0.0001
                && Math.abs(a.y - b.y) < 0.0001, "Body half-turn rotates horizontal anchor without changing height");
        mob.getAttribute(Attributes.SCALE).setBaseValue(2);
        mob.refreshDimensions();
        Vec3 enlarged = DevourRules.anchorPosition(mob, p, 0).subtract(mob.position());
        h.assertTrue(Math.abs(enlarged.y - b.y * 2) < 0.0001, "Anchor height follows current effective body scale");
        mob.getAttribute(Attributes.SCALE).setBaseValue(1);
        mob.refreshDimensions();
        capture(h, mob, p);
        mob.getAttribute(ModAttributes.PIXEL_SCALE).setBaseValue(0.01);
        mob.refreshDimensions();
        DevourRules.tick(p);
        released(h, p, "Captor loses size qualification");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 260)
    public static void failedAttemptCooldownExpiresAtConfiguredWorldTick(GameTestHelper h) {
        Zombie mob = zombie(h);
        Player p = player(h, GameType.SURVIVAL);
        int cooldown = SurvivalConfig.DEVOUR_COOLDOWN.get();
        h.assertTrue(!DevourRules.attemptCapture(mob, p, 1), "Eligible probability failure starts cooldown");
        h.runAtTickTime(cooldown - 1, () -> {
            p.setHealth(2);
            at(h, p, new Vec3(4.8, 2, 4));
            h.assertTrue(!DevourRules.attemptCapture(mob, p, 0), "One tick before expiry still blocked");
        });
        h.runAtTickTime(cooldown, () -> {
            p.setHealth(2);
            at(h, p, new Vec3(4.8, 2, 4));
            h.assertTrue(DevourRules.attemptCapture(mob, p, 0), "Exact configured cooldown expiry permits retry");
            DevourRules.release(p);
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void blockedAnchorInterruptsWithoutLeavingImmunity(GameTestHelper h) {
        Zombie mob = zombie(h);
        Player p = player(h, GameType.SURVIVAL);
        capture(h, mob, p);
        Vec3 anchor = DevourRules.anchorPosition(mob, p, 0);
        BlockPos blocked = BlockPos.containing(anchor);
        h.getLevel().setBlockAndUpdate(blocked, Blocks.STONE.defaultBlockState());
        DevourRules.tick(p);
        released(h, p, "Obstructed hand anchor");
        h.assertTrue(!DevourRules.shouldBlockDamage(p, p.damageSources().generic()), "Blocked anchor cannot leave immunity");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void switchingMockModeToCreativeOrSpectatorReleasesOnTick(GameTestHelper h) {
        for (GameType mode : new GameType[]{GameType.CREATIVE, GameType.SPECTATOR}) {
            MutableModePlayer p = new MutableModePlayer(h.getLevel());
            PixelScaleHelper.ensurePlayerMini(p);
            p.setNoGravity(true);
            at(h, p, new Vec3(4.8, 2, 4));
            p.setHealth(2);
            h.getLevel().addFreshEntity(p);
            capture(h, zombie(h), p);
            p.mode = mode;
            DevourRules.tick(p);
            released(h, p, "Mode transition to " + mode);
        }
        h.succeed();
    }

    /** Same headless Player approach as makeMockPlayer, with mutable observable mode. */
    private static final class MutableModePlayer extends Player {
        private GameType mode = GameType.SURVIVAL;
        MutableModePlayer(Level level) {
            super(level, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "devour-mode-test"));
        }
        @Override public boolean isCreative() { return mode == GameType.CREATIVE; }
        @Override public boolean isSpectator() { return mode == GameType.SPECTATOR; }
        @Override public boolean isLocalPlayer() { return true; }
    }

    @GameTest(template = "empty")
    public static void manuallyPostedLeaveLevelEventClearsBindingWithoutRelocation(GameTestHelper h) {
        // Listener regression only: this does not simulate actual chunk unloading or section iteration.
        for (boolean captorLeaves : new boolean[]{true, false}) {
            Zombie mob = zombie(h);
            Player p = player(h, GameType.SURVIVAL);
            capture(h, mob, p);
            Vec3 before = p.position();
            NeoForge.EVENT_BUS.post(new EntityLeaveLevelEvent(captorLeaves ? mob : p, h.getLevel()));
            h.assertTrue(!DevourRules.isCaptured(p) && !p.isPassenger(), "Leave event clears state and passenger binding");
            DevourPlayerAccess state = (DevourPlayerAccess) p;
            h.assertTrue(state.examplemod$getCaptorId() == -1 && state.examplemod$getDevourProgress() == 0,
                    "Leave event clears synchronized state");
            h.assertTrue(p.position().equals(before), "Leave callback must not relocate player during section iteration");
            h.assertTrue(!DevourRules.shouldBlockDamage(p, p.damageSources().generic()), "Leave event removes session immunity");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void capturedPlayerActualAttackAndBowUseAreRejected(GameTestHelper h) {
        Zombie mob = zombie(h);
        Player p = player(h, GameType.SURVIVAL);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
        capture(h, mob, p);
        float health = mob.getHealth();
        p.attack(mob);
        h.assertTrue(mob.getHealth() == health, "Actual Player.attack cannot damage captor while captured");
        p.startUsingItem(InteractionHand.MAIN_HAND);
        h.assertTrue(!p.isUsingItem(), "Actual startUsingItem for bow is rejected while captured");
        DevourRules.release(p);
        p.startUsingItem(InteractionHand.MAIN_HAND);
        h.assertTrue(p.isUsingItem(), "Same bow use succeeds after release");
        p.stopUsingItem();
        h.succeed();
    }

    /** Player's base drop method creates but never spawns items; mirror ServerPlayer's world insertion only. */
    private static final class DeathDropPlayer extends Player {
        DeathDropPlayer(Level level) {
            super(level, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "devour-death-test"));
        }
        @Override public boolean isCreative() { return false; }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isLocalPlayer() { return true; }
        @Override public ItemEntity drop(ItemStack stack, boolean dropAround, boolean traceItem) {
            ItemEntity drop = super.drop(stack, dropAround, traceItem);
            if (drop != null) {
                if (captureDrops() != null) captureDrops().add(drop);
                else level().addFreshEntity(drop);
            }
            return drop;
        }
    }

    private static Zombie zombie(GameTestHelper h) {
        Zombie mob = h.spawn(EntityType.ZOMBIE, new Vec3(4, 2, 4));
        mob.setNoAi(true);
        mob.setNoGravity(true);
        mob.setPersistenceRequired();
        mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        return mob;
    }

    private static Player player(GameTestHelper h, GameType mode) {
        Player p = h.makeMockPlayer(mode);
        PixelScaleHelper.ensurePlayerMini(p);
        p.setNoGravity(true);
        at(h, p, new Vec3(4.8, 2, 4));
        p.setHealth(2);
        h.getLevel().addFreshEntity(p);
        return p;
    }

    private static void capture(GameTestHelper h, Mob mob, Player p) {
        h.assertTrue(DevourRules.attemptCapture(mob, p, 0), "Fresh fixture captures");
    }

    private static void released(GameTestHelper h, Player p, String reason) {
        h.assertTrue(p.isAlive() && !DevourRules.isCaptured(p) && !p.isPassenger(), reason + " releases living player");
        h.assertTrue(h.getLevel().noBlockCollision(p, p.getBoundingBox()), reason + " chooses position without block collisions");
        h.assertTrue(!p.isOnFire() && p.fallDistance == 0, reason + " clears fire and fall distance");
    }

    private static void withCertainChance(Runnable action) {
        double previous = SurvivalConfig.DEVOUR_CHANCE.get();
        try {
            SurvivalConfig.DEVOUR_CHANCE.set(1.0);
            action.run();
        } finally {
            SurvivalConfig.DEVOUR_CHANCE.set(previous);
        }
    }

    private static void at(GameTestHelper h, Entity entity, Vec3 local) {
        entity.setPos(h.absoluteVec(local));
    }

    private DevourTests() { }
}
