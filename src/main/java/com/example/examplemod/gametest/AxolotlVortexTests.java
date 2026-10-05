package com.example.examplemod.gametest;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.scale.PixelScaleHelper;
import com.example.examplemod.init.ModDamageTypes;
import com.example.examplemod.vortex.AxolotlVortexRules;
import com.example.examplemod.vortex.AxolotlVortexAccess;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real fluid fixtures; helper probes do not claim real client packet or unload coverage. */
@GameTestHolder(ExampleMod.MODID)
@PrefixGameTestTemplate(false)
public final class AxolotlVortexTests {
    @GameTest(template = "empty")
    public static void waterModesAndPassengerQualification(GameTestHelper h) {
        Axolotl a = source(h);
        for (GameType mode : GameType.values()) {
            Player p = player(h, mode, false);
            center(p, mouth(a).add(0, 0, 1));
            h.assertTrue(AxolotlVortexRules.isEligibleTarget(a, p)
                    == (mode == GameType.SURVIVAL || mode == GameType.ADVENTURE), "Mode qualification: " + mode);
        }
        Player p = player(h, GameType.SURVIVAL, false);
        center(p, mouth(a).add(0, 0, 1));
        h.assertTrue(AxolotlVortexRules.applyPair(a, p, 100), "NoAI source in real water remains eligible");
        p.startRiding(a, true);
        h.assertTrue(!AxolotlVortexRules.applyPair(a, p, 101), "Passenger is excluded");
        p.stopRiding();
        center(p, h.absoluteVec(new Vec3(4, 7, 4)));
        h.assertTrue(!AxolotlVortexRules.applyPair(a, p, 102), "Dry target is excluded");
        AxolotlVortexRules.clearFor(a);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void threeDimensionalRadiusConeAndNearMouth(GameTestHelper h) {
        Axolotl a = source(h);
        Player p = player(h, GameType.SURVIVAL, false);
        Vec3 m = mouth(a);
        center(p, m.add(0, 0, 3));
        h.assertTrue(AxolotlVortexRules.applyPair(a, p, 100), "Exactly three blocks is included");
        center(p, m.add(0, 0, 3.001));
        h.assertTrue(!AxolotlVortexRules.applyPair(a, p, 101), "Beyond three blocks is excluded");
        center(p, m.add(0, 2.9, 1));
        h.assertTrue(!AxolotlVortexRules.applyPair(a, p, 102), "Vertical component counts in radius");
        center(p, m.add(1.7, 0, 1));
        h.assertTrue(AxolotlVortexRules.applyPair(a, p, 103), "Inside front sixty-degree half-angle");
        center(p, m.add(1.8, 0, 1));
        h.assertTrue(!AxolotlVortexRules.applyPair(a, p, 104), "Outside cone is excluded");
        center(p, m.add(0, 0, -1));
        h.assertTrue(!AxolotlVortexRules.applyPair(a, p, 105), "Rear target is excluded");
        center(p, m.add(0, 0, -0.1));
        h.assertTrue(AxolotlVortexRules.applyPair(a, p, 106), "Near mouth has no rear angle dead zone");
        AxolotlVortexRules.clearFor(a);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void solidWallAndAirGapStopAndResetSession(GameTestHelper h) {
        Axolotl a = source(h);
        Player p = player(h, GameType.SURVIVAL, false);
        center(p, mouth(a).add(0, 0, 2));
        h.assertTrue(AxolotlVortexRules.applyPair(a, p, 100), "Continuous water path works");
        BlockPos obstacle = BlockPos.containing(mouth(a).add(0, 0, 1));
        long now = 101;
        for (var block : new net.minecraft.world.level.block.Block[]{Blocks.STONE, Blocks.AIR}) {
            h.getLevel().setBlockAndUpdate(obstacle, block.defaultBlockState());
            p.setDeltaMovement(Vec3.ZERO);
            h.assertTrue(!AxolotlVortexRules.applyPair(a, p, now++), "Solid or air interruption stops pull: " + block);
            h.assertTrue(!AxolotlVortexRules.hasSession(p), "Invalid path clears timer immediately");
            h.assertTrue(p.getDeltaMovement().equals(Vec3.ZERO), "No impulse through interrupted path");
            h.getLevel().setBlockAndUpdate(obstacle, Blocks.WATER.defaultBlockState());
            h.assertTrue(AxolotlVortexRules.applyPair(a, p, now++), "Restored water allows fresh session");
        }
        AxolotlVortexRules.clearFor(a);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void pullIsAdditiveCappedAndSameTickDeduplicated(GameTestHelper h) {
        Axolotl a = source(h);
        Player p = player(h, GameType.SURVIVAL, false);
        center(p, mouth(a).add(0, 0, 1));
        Vec3 original = p.position();
        p.setDeltaMovement(new Vec3(0.2, 0, 0.3));
        h.assertTrue(AxolotlVortexRules.applyPair(a, p, 100), "Escaping player still receives additive pull");
        Vec3 once = p.getDeltaMovement();
        h.assertTrue(Math.abs(once.x - 0.2) < 1e-6 && Math.abs(once.z - 0.294) < 1e-6,
                "Transverse and escape speed preserved; only .006 added toward mouth: " + once);
        AxolotlVortexRules.applyPair(a, p, 100);
        h.assertTrue(p.getDeltaMovement().equals(once), "Repeated pair in same tick adds no impulse");
        h.assertTrue(p.position().equals(original) && !p.isPassenger(), "Pull never teleports or mounts player");
        p.setDeltaMovement(new Vec3(0.2, 0, -0.059));
        AxolotlVortexRules.applyPair(a, p, 101);
        h.assertTrue(Math.abs(p.getDeltaMovement().z + 0.06) < 1e-6, "New radial contribution stops at .06");
        p.setDeltaMovement(new Vec3(0.2, 0, -0.2));
        AxolotlVortexRules.applyPair(a, p, 102);
        h.assertTrue(p.getDeltaMovement().equals(new Vec3(0.2, 0, -0.2)), "Existing inward speed is never clipped");
        AxolotlVortexRules.clearFor(a);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void biteRequiresFullIntervalAndReentryRestarts(GameTestHelper h) {
        Axolotl a = source(h);
        Player p = player(h, GameType.SURVIVAL, false);
        center(p, mouth(a).add(0, 0, 0.1));
        float health = p.getHealth();
        for (long t = 100; t < 120; t++) AxolotlVortexRules.applyPair(a, p, t);
        h.assertTrue(p.getHealth() == health, "No bite before full twenty elapsed ticks");
        AxolotlVortexRules.applyPair(a, p, 120);
        h.assertTrue(p.getHealth() == health - 0.5F, "First bite at full interval");
        AxolotlVortexRules.applyPair(a, p, 120);
        h.assertTrue(p.getHealth() == health - 0.5F, "Same tick cannot repeat bite");
        center(p, mouth(a).add(0, 0, 1));
        AxolotlVortexRules.applyPair(a, p, 121);
        center(p, mouth(a).add(0, 0, 0.1));
        for (long t = 122; t < 142; t++) AxolotlVortexRules.applyPair(a, p, t);
        h.assertTrue(p.getHealth() == health - 0.5F, "Reentry restarts complete bite timer");
        p.invulnerableTime = 0;
        AxolotlVortexRules.applyPair(a, p, 142);
        h.assertTrue(p.getHealth() == health - 1, "Reentry bite after full interval");
        AxolotlVortexRules.clearFor(a);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void lifecycleListenersClearWithoutRelocation(GameTestHelper h) {
        Axolotl a = source(h);
        Player p = player(h, GameType.SURVIVAL, false);
        for (int cause = 0; cause < 5; cause++) {
            center(p, mouth(a).add(0, 0, 1));
            h.assertTrue(AxolotlVortexRules.applyPair(a, p, 100 + cause), "Establish session for event " + cause);
            Vec3 before = p.position();
            if (cause == 0) NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(p));
            if (cause == 1) NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(p, Level.OVERWORLD, Level.NETHER));
            if (cause == 2) NeoForge.EVENT_BUS.post(new EntityTeleportEvent.TeleportCommand(a, a.getX() + 10, a.getY(), a.getZ()));
            if (cause == 3) NeoForge.EVENT_BUS.post(new EntityLeaveLevelEvent(a, h.getLevel()));
            if (cause == 4) NeoForge.EVENT_BUS.post(new EntityTeleportEvent.TeleportCommand(p, p.getX() + 10, p.getY(), p.getZ()));
            h.assertTrue(!AxolotlVortexRules.hasSession(p), "Registered lifecycle listener clears session " + cause);
            h.assertTrue(p.position().equals(before), "Lifecycle cleanup does not relocate player");
            h.assertTrue(state(a).examplemod$getVortexPhase() == 0, "Lifecycle clears synchronized mouth state");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void realWorldTickAutomaticallyDiscoversAndMovesTarget(GameTestHelper h) {
        Axolotl a = source(h);
        Player p = player(h, GameType.SURVIVAL, true);
        center(p, mouth(a).add(0, 0, 1.5));
        Vec3 start = p.position();
        h.runAtTickTime(8, () -> {
            h.assertTrue(AxolotlVortexRules.hasSession(p), "Actual server ticks automatically discover loaded source");
            h.assertTrue(p.getZ() < start.z - 0.01, "Additive attraction changes actual world position");
            h.assertTrue(state(a).examplemod$getVortexTargetId() == p.getId(), "Source metadata identifies selected player");
            h.assertTrue(!p.isPassenger(), "Real tick pull leaves player free");
            AxolotlVortexRules.clearFor(a);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void realWorldTickContinuousBitesKeepVanillaAttribution(GameTestHelper h) {
        Axolotl a = source(h);
        PinnedPlayer p = new PinnedPlayer(h.getLevel(), a);
        PixelScaleHelper.ensurePlayerMini(p);
        // Isolate cumulative bite damage from vanilla saturated natural regeneration.
        p.getFoodData().setFoodLevel(10);
        p.setNoGravity(true);
        center(p, mouth(a).add(0, 0, 0.1));
        h.getLevel().addFreshEntity(p);
        float health = p.getHealth();
        h.runAtTickTime(18, () -> h.assertTrue(p.getHealth() == health, "Real tick must wait full first bite interval"));
        h.runAtTickTime(26, () -> h.assertTrue(p.getHealth() == health - 0.5F, "Real level tick performs first bite"));
        h.runAtTickTime(46, () -> {
            h.assertTrue(p.getHealth() == health - 1, "Real level ticks continue one bite per full interval: health="
                    + p.getHealth() + ", initialHealth=" + health + ", source=" + p.getLastDamageSource()
                    + ", phase=" + state(a).examplemod$getVortexPhase() + ", mouthDistance="
                    + p.getBoundingBox().getCenter().distanceTo(mouth(a)));
            DamageSource last = p.getLastDamageSource();
            h.assertTrue(last != null && last.is(ModDamageTypes.AXOLOTL_BITE) && last.getEntity() == a,
                    "Bite retains dedicated type and source attribution");
            AxolotlVortexRules.clearFor(a);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void globalPairingIsUniqueAndTieBreakUsesUuid(GameTestHelper h) {
        Axolotl first = source(h);
        Axolotl second = h.spawn(EntityType.AXOLOTL, new Vec3(4.5, 2.5, 3.5));
        second.setNoAi(true);
        second.setNoGravity(true);
        second.setYRot(0);
        second.setYHeadRot(0);
        second.yBodyRot = 0;
        first.setUUID(new UUID(0, 1));
        second.setUUID(new UUID(0, 2));
        Player p = player(h, GameType.SURVIVAL, true);
        center(p, mouth(first).add(0, 0, 1));
        p.setDeltaMovement(Vec3.ZERO);
        // Deliberately register in reverse order: insertion order cannot choose the winner.
        AxolotlVortexRules.register(second);
        AxolotlVortexRules.register(first);
        h.runAtTickTime(2, () -> {
        h.assertTrue(state(first).examplemod$getVortexTargetId() == p.getId(), "UUID resolves exact distance tie");
        h.assertTrue(state(second).examplemod$getVortexPhase() == 0, "One player cannot receive two sources");
        h.assertTrue(p.getDeltaMovement().z < 0, "Selected player receives pull from production tick");
        // Callback may run before LevelTick.Post for this gameTime; establish current stamp first.
        AxolotlVortexRules.tickLevel(h.getLevel());
        Vec3 velocity = p.getDeltaMovement();
        AxolotlVortexRules.tickLevel(h.getLevel());
        h.assertTrue(p.getDeltaMovement().equals(velocity), "Whole level same-tick reentry is deduplicated");
        AxolotlVortexRules.clearFor(first);
        AxolotlVortexRules.clearFor(second);
        h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void deadSourceAndDrySourceImmediatelyClear(GameTestHelper h) {
        Axolotl a = source(h);
        Player p = player(h, GameType.SURVIVAL, false);
        center(p, mouth(a).add(0, 0, 1));
        AxolotlVortexRules.applyPair(a, p, 100);
        a.setPlayingDead(true);
        h.assertTrue(!AxolotlVortexRules.applyPair(a, p, 101) && !AxolotlVortexRules.hasSession(p), "Playing dead stops active vortex");
        a.setPlayingDead(false);
        AxolotlVortexRules.applyPair(a, p, 102);
        Vec3 saved = a.position();
        a.setPos(a.getX(), h.absoluteVec(new Vec3(0, 8, 0)).y, a.getZ());
        h.assertTrue(!AxolotlVortexRules.applyPair(a, p, 103) && !AxolotlVortexRules.hasSession(p),
                "Leaving actual water clears active source session");
        a.setPos(saved);
        AxolotlVortexRules.applyPair(a, p, 104);
        a.setHealth(0);
        h.assertTrue(!AxolotlVortexRules.applyPair(a, p, 105) && !AxolotlVortexRules.hasSession(p),
                "Dead source clears active session");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void biteKeepsInvulnerabilityResistanceAndTotem(GameTestHelper h) {
        Axolotl a = source(h);
        Player p = player(h, GameType.SURVIVAL, false);
        center(p, mouth(a).add(0, 0, 0.1));
        p.invulnerableTime = 100;
        ((com.example.examplemod.mixin.LivingEntityAccessor) p).examplemod$setLastHurt(1);
        float health = p.getHealth();
        for (long t = 100; t <= 120; t++) AxolotlVortexRules.applyPair(a, p, t);
        h.assertTrue(p.getHealth() == health, "Normal damage immunity is not bypassed");
        p.invulnerableTime = 0;
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 200, 4));
        AxolotlVortexRules.applyPair(a, p, 140);
        h.assertTrue(p.getHealth() == health, "Resistance V prevents ordinary bite damage");
        p.removeAllEffects();
        p.invulnerableTime = 0;
        p.setHealth(0.25F);
        p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING));
        AxolotlVortexRules.applyPair(a, p, 160);
        h.assertTrue(p.isAlive() && p.getOffhandItem().isEmpty(), "Lethal bite respects vanilla totem protection");
        AxolotlVortexRules.clearFor(a);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void lethalBiteAttributesDeathAndDoesNotAddKnockback(GameTestHelper h) {
        Axolotl a = source(h);
        Player p = player(h, GameType.SURVIVAL, false);
        center(p, mouth(a).add(0, 0, 0.1));
        float health = p.getHealth();
        for (long t = 100; t < 120; t++) AxolotlVortexRules.applyPair(a, p, t);
        p.setDeltaMovement(new Vec3(0.12, 0.03, -0.1));
        Vec3 velocity = p.getDeltaMovement();
        AxolotlVortexRules.applyPair(a, p, 120);
        h.assertTrue(p.getHealth() == health - 0.5F && p.getDeltaMovement().equals(velocity),
                "Successful bite adds no damage knockback when inward speed already exceeds cap");
        p.invulnerableTime = 0;
        p.setHealth(0.25F);
        AxolotlVortexRules.applyPair(a, p, 140);
        h.assertTrue(!p.isAlive(), "Lethal normal bite kills unprotected player");
        DamageSource fatal = p.getLastDamageSource();
        h.assertTrue(fatal != null && fatal.is(ModDamageTypes.AXOLOTL_BITE) && fatal.getEntity() == a,
                "Lethal bite damage source points to axolotl");
        h.assertTrue(fatal.getLocalizedDeathMessage(p).getString().contains(a.getDisplayName().getString()),
                "Localized death message includes axolotl attribution");
        Player other = player(h, GameType.SURVIVAL, false);
        center(other, mouth(a).add(0, 0, 1));
        other.setDeltaMovement(Vec3.ZERO);
        other.hurt(other.damageSources().mobAttack(a), 1);
        h.assertTrue(other.getDeltaMovement().lengthSqr() > 0, "Ordinary mob damage still applies vanilla knockback");
        AxolotlVortexRules.clearFor(a);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void switchingSourceRestartsFullBiteInterval(GameTestHelper h) {
        Axolotl a = source(h);
        Axolotl b = h.spawn(EntityType.AXOLOTL, new Vec3(4.5, 2.5, 3.5));
        b.setNoAi(true);
        b.setNoGravity(true);
        b.setYRot(0);
        b.setYHeadRot(0);
        Player p = player(h, GameType.SURVIVAL, false);
        center(p, mouth(a).add(0, 0, 0.1));
        float health = p.getHealth();
        for (long t = 100; t < 119; t++) AxolotlVortexRules.applyPair(a, p, t);
        for (long t = 119; t < 139; t++) AxolotlVortexRules.applyPair(b, p, t);
        h.assertTrue(p.getHealth() == health, "Switching source cannot inherit nineteen ticks from previous source");
        AxolotlVortexRules.applyPair(b, p, 139);
        h.assertTrue(p.getHealth() == health - 0.5F && p.getLastDamageSource().getEntity() == b,
                "New source bites only after its complete interval");
        AxolotlVortexRules.clearFor(a);
        AxolotlVortexRules.clearFor(b);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void oneSourceSelectsOnlyClosestPlayerAndOtherMobRemainsUnchanged(GameTestHelper h) {
        Axolotl a = source(h);
        Player near = player(h, GameType.SURVIVAL, true);
        Player far = player(h, GameType.SURVIVAL, true);
        center(near, mouth(a).add(0, 0, 1));
        center(far, mouth(a).add(0, 0, 2));
        var fish = h.spawn(EntityType.COD, new Vec3(4.5, 2.5, 4.5));
        fish.setNoAi(true);
        fish.setNoGravity(true);
        PixelScaleHelper.applyTinyModifiers(fish);
        fish.setDeltaMovement(Vec3.ZERO);
        AxolotlVortexRules.register(a);
        h.runAtTickTime(2, () -> {
        h.assertTrue(AxolotlVortexRules.hasSession(near) && !AxolotlVortexRules.hasSession(far),
                "One axolotl selects only closest player");
        h.assertTrue(!AxolotlVortexRules.hasSession(far) && fish.getDeltaMovement().equals(Vec3.ZERO),
                "Unselected player has no session and wand-shrunk nonplayer receives no vortex velocity");
        AxolotlVortexRules.clearFor(a);
        h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void f21CapturedMetadataExcludesVortexWithoutClaimingRealCapture(GameTestHelper h) {
        Axolotl a = source(h);
        Player p = player(h, GameType.SURVIVAL, false);
        center(p, mouth(a).add(0, 0, 1));
        // Contract fixture only: not an actual F21 capture or passenger synchronization test.
        var access = (com.example.examplemod.devour.DevourPlayerAccess) p;
        try {
            access.examplemod$setDevourState(a.getId(), 0.5F);
            h.assertTrue(com.example.examplemod.devour.DevourRules.isCaptured(p), "Fixture enters F21 captured predicate");
            h.assertTrue(!AxolotlVortexRules.applyPair(a, p, 100) && !AxolotlVortexRules.hasSession(p),
                    "F21 captured metadata excludes vortex independently of passenger state");
            h.assertTrue(p.getDeltaMovement().equals(Vec3.ZERO), "Captured target gets no new velocity");
        } finally {
            access.examplemod$setDevourState(-1, 0);
            AxolotlVortexRules.clearFor(a);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void pairingSwapDoesNotEraseNewTargetsMouthState(GameTestHelper h) {
        Axolotl a = source(h);
        Axolotl b = h.spawn(EntityType.AXOLOTL, new Vec3(4.5, 2.5, 3.5));
        b.setNoAi(true);
        b.setNoGravity(true);
        b.setYRot(0);
        b.setYHeadRot(0);
        Player p = player(h, GameType.SURVIVAL, false);
        Player q = player(h, GameType.SURVIVAL, false);
        center(p, mouth(a).add(0, 0, 1));
        center(q, mouth(a).add(0, 0, 1));
        AxolotlVortexRules.applyPair(a, p, 100);
        AxolotlVortexRules.applyPair(b, q, 100);
        AxolotlVortexRules.applyPair(a, q, 101);
        AxolotlVortexRules.applyPair(b, p, 101);
        h.assertTrue(state(a).examplemod$getVortexTargetId() == q.getId() && state(a).examplemod$getVortexPhase() == 1,
                "Stopping p old session cannot clear a new target q metadata");
        h.assertTrue(state(b).examplemod$getVortexTargetId() == p.getId() && state(b).examplemod$getVortexPhase() == 1,
                "Both sources retain their swapped target metadata");
        AxolotlVortexRules.clearFor(a);
        AxolotlVortexRules.clearFor(b);
        h.succeed();
    }

    private static Axolotl source(GameTestHelper h) {
        for (int x = 0; x <= 9; x++) for (int y = 1; y <= 6; y++) for (int z = 0; z <= 10; z++)
            h.setBlock(new BlockPos(x, y, z), Blocks.WATER);
        Axolotl a = h.spawn(EntityType.AXOLOTL, new Vec3(4.5, 2.5, 3.5));
        a.setNoAi(true);
        a.setNoGravity(true);
        a.setYRot(0);
        a.setYHeadRot(0);
        a.yBodyRot = 0;
        return a;
    }

    private static Player player(GameTestHelper h, GameType mode, boolean add) {
        Player p = h.makeMockPlayer(mode);
        PixelScaleHelper.ensurePlayerMini(p);
        p.setNoGravity(true);
        if (add) h.getLevel().addFreshEntity(p);
        return p;
    }
    private static Vec3 mouth(Axolotl a) { return AxolotlVortexRules.mouthPosition(a); }
    private static void center(Player p, Vec3 center) { p.setPos(center.x, center.y - p.getBbHeight() / 2.0, center.z); }
    private static AxolotlVortexAccess state(Axolotl a) { return (AxolotlVortexAccess) a; }

    /** Only holds the geometric bite fixture; production level event drives every rule and damage tick. */
    private static final class PinnedPlayer extends Player {
        private final Axolotl source;
        PinnedPlayer(Level level, Axolotl source) {
            super(level, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "vortex-bite-test"));
            this.source = source;
        }
        @Override public boolean isCreative() { return false; }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isLocalPlayer() { return true; }
        @Override public void tick() {
            super.tick();
            center(this, mouth(source).add(0, 0, 0.1));
            setDeltaMovement(Vec3.ZERO);
        }
    }
    private AxolotlVortexTests() { }
}
