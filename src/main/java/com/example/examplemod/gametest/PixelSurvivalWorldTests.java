package com.example.examplemod.gametest;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.mixin.LivingEntityAccessor;
import com.example.examplemod.retaliate.FriendlyRetaliateGoal;
import com.example.examplemod.scale.PixelScaleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** World ticks exercise the registered entity events and GoalSelector, not their helpers. */
@GameTestHolder(ExampleMod.MODID)
@PrefixGameTestTemplate(false)
public class PixelSurvivalWorldTests {
    @GameTest(template = "empty", timeoutTicks = 180)
    public static void normalCowPursuesAndActuallyRetaliates(GameTestHelper helper) {
        retaliation(helper, false, 2.0F);
    }

    @GameTest(template = "empty", timeoutTicks = 650)
    public static void tinyCowPursuesAndActuallyRetaliates(GameTestHelper helper) {
        retaliation(helper, true, 0.5F);
    }

    private static void retaliation(GameTestHelper helper, boolean tiny, float expectedDamage) {
        floor(helper);
        Cow defender = helper.spawn(EntityType.COW, new Vec3(4.5, 2, 4.5));
        Cow attacker = helper.spawn(EntityType.COW, new Vec3(4.5, 2, 8.5));
        // Preserve panic to verify retaliation priority; remove unrelated random wandering.
        defender.goalSelector.getAvailableGoals().stream()
                .filter(goal -> !(goal.getGoal() instanceof FriendlyRetaliateGoal)
                        && !(goal.getGoal() instanceof PanicGoal))
                .map(goal -> goal.getGoal()).toList().forEach(defender.goalSelector::removeGoal);
        attacker.setNoAi(true);
        attacker.getAttribute(Attributes.ARMOR).setBaseValue(0);
        attacker.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        if (tiny) {
            PixelScaleHelper.applyTinyModifiers(defender);
        }
        // Isolate pursuit from tiny received knockback ejecting the defender off the arena.
        defender.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        helper.assertTrue(defender.getAttribute(Attributes.ATTACK_DAMAGE) == null,
                "Cow must exercise the missing-attack-attribute fallback");
        double[] startDistance = {0};
        boolean[] attacked = {false};
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(attacker.getHealth() == attacker.getMaxHealth(),
                    "Unprovoked cow must not damage the attacker");
            startDistance[0] = defender.distanceToSqr(attacker);
            helper.assertTrue(startDistance[0] > 9, "Start outside retaliation reach");
            helper.assertTrue(defender.hurt(defender.damageSources().mobAttack(attacker), 1),
                    "Provocation must be actual accepted damage");
            attacked[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(attacked[0], "Waiting for provocation");
            float damage = attacker.getMaxHealth() - attacker.getHealth();
            helper.assertTrue(damage > 0, "Waiting for real attack: distance=" + defender.distanceTo(attacker)
                    + ", navigationDone=" + defender.getNavigation().isDone()
                    + ", LOS=" + defender.getSensing().hasLineOfSight(attacker)
                    + ", defenderPos=" + defender.position() + ", attackerPos=" + attacker.position()
                    + ", defenderOnGround=" + defender.onGround()
                    + ", defenderHP=" + defender.getHealth()
                    + ", running=" + defender.goalSelector.getAvailableGoals().stream().anyMatch(
                    goal -> goal.isRunning() && goal.getGoal() instanceof FriendlyRetaliateGoal));
            helper.assertTrue(Math.abs(damage - expectedDamage) < 0.001,
                    "First real retaliation damage: expected " + expectedDamage + ", got " + damage);
            helper.assertTrue(defender.distanceToSqr(attacker) < startDistance[0] - 1,
                    "Defender must actually pursue the stationary attacker");
            helper.assertTrue(defender.goalSelector.getAvailableGoals().stream().anyMatch(
                    goal -> goal.isRunning() && goal.getGoal() instanceof FriendlyRetaliateGoal),
                    "Retaliation must run through GoalSelector");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 65)
    public static void gravityLandingAutomaticallyStompsAndPreservesOrdinaryHurtState(GameTestHelper helper) {
        floor(helper);
        Cow victim = durableTinyCow(helper, new Vec3(4.5, 2, 4.5));
        Cow control = durableTinyCow(helper, new Vec3(10.5, 2, 10.5));
        // Vanilla NoAI disables travel authority as well as AI. Override only that fixture
        // gate so this NoAI cow can run vanilla gravity without navigation/random movement.
        Cow stomper = new Cow(EntityType.COW, helper.getLevel()) {
            @Override
            public boolean isControlledByLocalInstance() {
                return true;
            }
        };
        Vec3 fallStart = helper.absoluteVec(new Vec3(4.5, 6, 4.5));
        stomper.setPos(fallStart.x, fallStart.y, fallStart.z);
        stomper.getAttribute(Attributes.SCALE).setBaseValue(3.0D);
        stomper.setNoAi(true);
        helper.getLevel().addFreshEntity(stomper);
        // Gravity remains enabled. No synthetic fallDistance, previous position, or stomp calls.
        boolean[] preHit = {false};
        float[] healthAfterHit = {0};
        boolean[] sawFall = {false};
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(victim.hurt(victim.damageSources().generic(), 5), "Victim ordinary hit accepted");
            helper.assertTrue(control.hurt(control.damageSources().generic(), 5), "Control ordinary hit accepted");
            victim.setDeltaMovement(Vec3.ZERO);
            control.setDeltaMovement(Vec3.ZERO);
            healthAfterHit[0] = victim.getHealth();
            preHit[0] = true;
        });
        helper.onEachTick(() -> {
            if (stomper.fallDistance > 1) {
                sawFall[0] = true;
            }
            if (preHit[0] && victim.getHealth() < healthAfterHit[0]) {
                helper.assertTrue(Math.abs(victim.invulnerableTime - control.invulnerableTime) <= 1,
                        "Stomp must preserve ordinary invulnerability countdown relative to control");
                helper.assertTrue(Math.abs(((LivingEntityAccessor) victim).examplemod$getLastHurt() - 5) < 0.001,
                        "Stomp must preserve ordinary lastHurt=5");
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(preHit[0] && sawFall[0] && stomper.onGround(),
                    "Waiting for landing: Y=" + stomper.getY() + ", onGround=" + stomper.onGround()
                            + ", fallDistance=" + stomper.fallDistance + ", sawFall=" + sawFall[0]
                            + ", victimHP=" + victim.getHealth());
            float damage = healthAfterHit[0] - victim.getHealth();
            helper.assertTrue(damage > 5.7F && damage < 6.3F,
                    "Four-block fall must inflict about 6, not walking damage 2; got " + damage);
            helper.assertTrue(stomper.fallDistance == 0, "Landing must have reset vanilla fallDistance");
            helper.assertTrue(victim.getLastHurtByMob() == stomper, "Automatic stomp attribution must point to stomper");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 55)
    public static void realWallTravelClimbsThenSneaksWithoutForwardInput(GameTestHelper helper) {
        floor(helper);
        for (int y = 2; y <= 7; y++) {
            helper.setBlock(new BlockPos(4, y, 4), Blocks.STONE);
        }
        // An unattached mock player is driven once per world tick to supply deterministic input.
        // travel still performs actual block collision and gravity; no collision flag is injected.
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        PixelScaleHelper.ensurePlayerMini(player);
        Vec3 pos = helper.absoluteVec(new Vec3(4.5, 2, 5 + player.getBbWidth() / 2 + 0.001));
        player.setPos(pos.x, pos.y, pos.z);
        player.setYRot(180);
        double[] climbStart = {0};
        double[] sneakStart = {0};
        for (int tick = 1; tick <= 12; tick++) {
            final int current = tick;
            helper.runAtTickTime(tick, () -> {
                player.zza = 1;
                player.travel(new Vec3(0, 0, 1));
                if (current == 3) {
                    helper.assertTrue(player.horizontalCollision, "Actual movement must collide with wall");
                    climbStart[0] = player.getY();
                }
                if (current == 12) {
                    double climbed = player.getY() - climbStart[0];
                    helper.assertTrue(climbed > 0.25 && climbed < 0.28,
                            "Nine wall travel ticks should climb about 0.2646; got " + climbed);
                    sneakStart[0] = player.getY();
                }
            });
        }
        for (int tick = 13; tick <= 22; tick++) {
            helper.runAtTickTime(tick, () -> {
                player.setShiftKeyDown(true);
                player.zza = 0;
                player.travel(Vec3.ZERO);
                helper.assertTrue(Math.abs(player.getY() - sneakStart[0]) < 0.01,
                        "Sneak must hold height: diff=" + (player.getY() - sneakStart[0])
                                + ", horizontalCollision=" + player.horizontalCollision
                                + ", velocity=" + player.getDeltaMovement());
            });
        }
        helper.runAtTickTime(23, () -> {
            player.setShiftKeyDown(false);
            player.zza = 0;
        });
        for (int tick = 24; tick <= 28; tick++) {
            helper.runAtTickTime(tick, () -> player.travel(Vec3.ZERO));
        }
        helper.runAtTickTime(29, () -> {
            helper.assertTrue(player.getY() < sneakStart[0] - 0.03,
                    "Releasing sneak and forward must resume gravity");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 50)
    public static void actualChargedDiamondSwordAttackScalesDamageAndSprintKnockback(GameTestHelper helper) {
        floor(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        PixelScaleHelper.ensurePlayerMini(player);
        Vec3 pos = helper.absoluteVec(new Vec3(4.5, 2, 4));
        player.setPos(pos.x, pos.y, pos.z);
        player.setYRot(0);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        Cow victim = helper.spawn(EntityType.COW, new Vec3(4.5, 2, 4.5));
        victim.setNoAi(true);
        victim.getAttribute(Attributes.ARMOR).setBaseValue(0);
        victim.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
        // Player.tick supplies equipment modifier updates and a genuinely charged cooldown.
        for (int tick = 1; tick <= 30; tick++) {
            helper.runAtTickTime(tick, player::tick);
        }
        helper.runAtTickTime(31, () -> {
            helper.assertTrue(player.getAttackStrengthScale(0.5F) > 0.99,
                    "Sword attack must be fully charged");
            helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.ATTACK_DAMAGE) - 7) < 0.001,
                    "Diamond sword equipment must contribute real attack damage (7 total)");
            victim.setDeltaMovement(Vec3.ZERO);
            player.setOnGround(true);
            player.fallDistance = 0;
            player.setSprinting(true);
            float before = victim.getHealth();
            player.attack(victim);
            helper.assertTrue(Math.abs(before - victim.getHealth() - 1.75F) < 0.001,
                    "Actual sword attack must deal 7 * .25 = 1.75");
            double knockback = Math.sqrt(victim.getDeltaMovement().horizontalDistanceSqr());
            helper.assertTrue(Math.abs(knockback - 0.175) < 0.005,
                    "Actual sprint attack knockback must be .175 after both output paths are scaled; got " + knockback);
            helper.succeed();
        });
    }

    private static Cow durableTinyCow(GameTestHelper helper, Vec3 position) {
        Cow cow = helper.spawn(EntityType.COW, position);
        cow.setNoAi(true);
        cow.setNoGravity(true);
        PixelScaleHelper.applyTinyModifiers(cow);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        cow.setHealth(cow.getMaxHealth());
        return cow;
    }

    private static void floor(GameTestHelper helper) {
        for (int x = 1; x <= 13; x++) {
            for (int z = 1; z <= 13; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
    }
}
