package com.example.examplemod.gametest;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.init.ModAttributes;
import com.example.examplemod.scale.PixelScaleHelper;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ExampleMod.MODID)
@PrefixGameTestTemplate(false)
public class PixelSurvivalAdjustmentTests {
    private static final ResourceLocation EXTERNAL_SPEED = ResourceLocation.fromNamespaceAndPath("qa_external", "speed");
    private static final ResourceLocation EXTERNAL_HEALTH = ResourceLocation.fromNamespaceAndPath("qa_external", "health");
    private static final ResourceLocation EXTERNAL_STEP = ResourceLocation.fromNamespaceAndPath("qa_external", "step");

    @GameTest(template = "empty")
    public static void walkAnimationOnlyNormalizesMarkedEntities(GameTestHelper helper) {
        for (double scale : new double[]{0.5, 2.0}) {
            Cow unmarked = createCow(helper, new Vec3(3, 3, 3));
            unmarked.getAttribute(Attributes.SCALE).setBaseValue(scale);
            unmarked.refreshDimensions();
            helper.assertTrue(!PixelScaleHelper.isTiny(unmarked), "Vanilla scale must not mark an entity");
            animateDisplacement(unmarked, 0.01);
            close(helper, unmarked.walkAnimation.speed(), 0.016, "Unmarked scale " + scale + " must retain vanilla animation");
        }
        Cow marked = createCow(helper, new Vec3(5, 3, 5));
        PixelScaleHelper.applyTinyModifiers(marked);
        animateDisplacement(marked, 0.01);
        close(helper, marked.walkAnimation.speed(), 0.032, "Marked half cow animation");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        animateDisplacement(player, 0.01);
        close(helper, player.walkAnimation.speed(), 0.4, "Micro player animation saturates to .4 after update");
        for (LivingEntity entity : new LivingEntity[]{marked, player}) {
            float previous = entity.walkAnimation.speed();
            for (int i = 0; i < 20; i++) {
                animateDisplacement(entity, 0);
                helper.assertTrue(entity.walkAnimation.speed() <= previous, "Zero motion must decay monotonically");
                previous = entity.walkAnimation.speed();
            }
            helper.assertTrue(previous < 0.00002, "Zero motion must converge to zero");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyNbtReloadWorldJoinMigratesWithoutHealingOrDeletingExternalModifiers(GameTestHelper helper) {
        Cow old = createCow(helper, new Vec3(4, 3, 4));
        modifier(old, ModAttributes.PIXEL_SCALE, PixelScaleHelper.TINY_PIXEL_SCALE_ID,
                1.0 / 28.8 - 1, AttributeModifier.Operation.ADD_VALUE);
        modifier(old, Attributes.MOVEMENT_SPEED, PixelScaleHelper.TINY_MOVEMENT_SPEED_ID,
                -0.65, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        modifier(old, Attributes.MAX_HEALTH, EXTERNAL_HEALTH, 4, AttributeModifier.Operation.ADD_VALUE);
        modifier(old, Attributes.MAX_HEALTH, PixelScaleHelper.TINY_MAX_HEALTH_ID,
                -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        double baseStep = old.getAttribute(Attributes.STEP_HEIGHT).getBaseValue();
        modifier(old, Attributes.STEP_HEIGHT, PixelScaleHelper.TINY_STEP_HEIGHT_ID,
                1.0 / 16.0 - baseStep, AttributeModifier.Operation.ADD_VALUE);
        modifier(old, Attributes.STEP_HEIGHT, EXTERNAL_STEP, 0.1, AttributeModifier.Operation.ADD_VALUE);
        modifier(old, Attributes.MOVEMENT_SPEED, EXTERNAL_SPEED, 0.1, AttributeModifier.Operation.ADD_VALUE);
        old.setHealth(3.5F);
        Cow joined = reloadAndJoin(helper, old);
        assertLegacyMigrated(helper, joined, baseStep);
        // Remove the first live instance, then load its persisted state through another real join.
        CompoundTag migrated = save(joined);
        joined.discard();
        helper.runAfterDelay(2, () -> {
            Cow secondJoin = loadAndJoin(helper, migrated);
            // Also verify values remain stable after actual world ticks.
            helper.runAfterDelay(2, () -> {
                assertLegacyMigrated(helper, secondJoin, baseStep);
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty")
    public static void markedNbtJoinRepairsMissingModifiersKeepingLegalCurrentHealth(GameTestHelper helper) {
        missingModifierJoin(helper, 4, 4);
    }

    @GameTest(template = "empty")
    public static void markedNbtJoinClampsExcessHealthAndLeavesUnmarkedEntityUntouched(GameTestHelper helper) {
        Cow unmarked = createCow(helper, new Vec3(10, 3, 10));
        modifier(unmarked, Attributes.MOVEMENT_SPEED, EXTERNAL_SPEED, 0.1, AttributeModifier.Operation.ADD_VALUE);
        unmarked.setHealth(8);
        Cow joined = reloadAndJoin(helper, unmarked);
        helper.assertTrue(!PixelScaleHelper.isTiny(joined), "Unmarked entity must not acquire marker");
        close(helper, joined.getScale(), 1, "Unmarked dimensions unchanged");
        close(helper, joined.getHealth(), 8, "Unmarked current health unchanged");
        close(helper, joined.getMaxHealth(), 10, "Unmarked max health unchanged");
        helper.assertTrue(joined.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(PixelScaleHelper.TINY_MOVEMENT_SPEED_ID) == null,
                "Unmarked join must not add speed modifier");
        helper.assertTrue(joined.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(EXTERNAL_SPEED), "Unmarked external modifier preserved");
        missingModifierJoin(helper, 8, 5);
    }

    private static void missingModifierJoin(GameTestHelper helper, float initialHealth, float expectedHealth) {
        Cow marked = createCow(helper, new Vec3(4, 3, 4));
        modifier(marked, ModAttributes.PIXEL_SCALE, PixelScaleHelper.TINY_PIXEL_SCALE_ID,
                -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        marked.setHealth(initialHealth);
        Cow joined = reloadAndJoin(helper, marked);
        assertRepaired(helper, joined, expectedHealth);
        CompoundTag repaired = save(joined);
        joined.discard();
        helper.runAfterDelay(2, () -> {
            Cow secondJoin = loadAndJoin(helper, repaired);
            assertRepaired(helper, secondJoin, expectedHealth);
            helper.succeed();
        });
    }

    private static void assertRepaired(GameTestHelper helper, Cow cow, float expectedHealth) {
        close(helper, cow.getScale(), 0.5, "Repaired scale");
        close(helper, cow.getMaxHealth(), 5, "Missing health modifier repaired");
        close(helper, cow.getHealth(), expectedHealth, "Repair must preserve absolute HP or clamp only excess");
        close(helper, cow.getAttributeValue(Attributes.MOVEMENT_SPEED),
                cow.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue() * .25, "Missing speed modifier repaired");
        close(helper, cow.getAttributeValue(Attributes.JUMP_STRENGTH),
                cow.getAttribute(Attributes.JUMP_STRENGTH).getBaseValue() * .5, "Missing jump modifier repaired");
        close(helper, cow.getAttributeValue(Attributes.STEP_HEIGHT),
                Math.max(1.0 / 16, cow.getAttribute(Attributes.STEP_HEIGHT).getBaseValue() * .5), "Missing step modifier repaired");
    }

    private static void assertLegacyMigrated(GameTestHelper helper, Cow cow, double baseStep) {
        close(helper, cow.getScale(), .5, "Legacy join must migrate dimensions to half");
        close(helper, cow.getBbHeight(), .7, "Legacy AABB must refresh on join");
        close(helper, cow.getHealth(), 3.5, "Migration must preserve absolute HP");
        close(helper, cow.getMaxHealth(), 7, "External health modifier must retain its effect");
        close(helper, cow.getAttributeValue(Attributes.MOVEMENT_SPEED),
                (cow.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue() + .1) * .25, "Migrated speed including external addition");
        close(helper, cow.getAttributeValue(Attributes.STEP_HEIGHT), Math.max(1.0 / 16, baseStep * .5) + .1,
                "Migrated step including external addition");
        helper.assertTrue(cow.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(EXTERNAL_SPEED), "External speed ID retained");
        helper.assertTrue(cow.getAttribute(Attributes.MAX_HEALTH).hasModifier(EXTERNAL_HEALTH), "External health ID retained");
        helper.assertTrue(cow.getAttribute(Attributes.STEP_HEIGHT).hasModifier(EXTERNAL_STEP), "External step ID retained");
        AttributeModifier scale = cow.getAttribute(ModAttributes.PIXEL_SCALE).getModifier(PixelScaleHelper.TINY_PIXEL_SCALE_ID);
        helper.assertTrue(scale.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, "Legacy scale operation migrated");
    }

    private static Cow createCow(GameTestHelper helper, Vec3 relativePosition) {
        Cow cow = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(cow != null, "Cow fixture creation");
        Vec3 pos = helper.absoluteVec(relativePosition);
        cow.setPos(pos.x, pos.y, pos.z);
        cow.setNoAi(true);
        cow.setNoGravity(true);
        return cow;
    }

    private static Cow reloadAndJoin(GameTestHelper helper, Cow source) {
        return loadAndJoin(helper, save(source));
    }

    private static CompoundTag save(Cow cow) {
        CompoundTag tag = new CompoundTag();
        cow.saveWithoutId(tag);
        return tag;
    }

    private static Cow loadAndJoin(GameTestHelper helper, CompoundTag tag) {
        Cow loaded = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(loaded != null, "Reload fixture creation");
        loaded.load(tag);
        helper.assertTrue(helper.getLevel().addFreshEntity(loaded), "Persisted entity must actually join world");
        if (PixelScaleHelper.isTiny(loaded)) {
            close(helper, loaded.getScale(), .5, "Marked cow scale must be correct immediately on join");
            close(helper, loaded.getBbHeight(), .7,
                    "Marked cow collision cache must agree with scale immediately on join, including already-migrated saves");
        }
        return loaded;
    }

    private static void modifier(LivingEntity entity, Holder<Attribute> attribute, ResourceLocation id,
                                 double amount, AttributeModifier.Operation operation) {
        entity.getAttribute(attribute).addOrReplacePermanentModifier(new AttributeModifier(id, amount, operation));
    }

    private static void animateDisplacement(LivingEntity entity, double distance) {
        entity.xo = entity.getX() - distance;
        entity.zo = entity.getZ();
        entity.calculateEntityAnimation(false);
    }

    private static void close(GameTestHelper helper, double actual, double expected, String reason) {
        helper.assertTrue(Math.abs(actual - expected) < 0.00001, reason + ": expected=" + expected + ", actual=" + actual);
    }
}
