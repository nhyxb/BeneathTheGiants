package com.example.examplemod.scale;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.init.ModAttributes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import virtuoel.pehkui.api.ScaleData;
import virtuoel.pehkui.api.ScaleModifier;
import virtuoel.pehkui.api.ScaleModifiers;
import virtuoel.pehkui.api.ScaleRegistries;
import virtuoel.pehkui.api.ScaleTypes;

/** Loaded only when the optional Pehkui mod is installed. */
public final class PehkuiScaleIntegration {
    private static final ResourceLocation WAND_SCALE_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "wand_scale");

    private static final ScaleModifier WAND_SCALE_MODIFIER = new ScaleModifier() {
        @Override
        public float modifyScale(ScaleData data, float modifiedScale, float delta) {
            return isWandScaled(data.getEntity()) ? modifiedScale * PixelScaleHelper.WAND_SCALE_FLOAT : modifiedScale;
        }

        @Override
        public float modifyPrevScale(ScaleData data, float modifiedScale) {
            return isWandScaled(data.getEntity()) ? modifiedScale * PixelScaleHelper.WAND_SCALE_FLOAT : modifiedScale;
        }
    };

    private static boolean initialized;

    private PehkuiScaleIntegration() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        ScaleRegistries.register(ScaleRegistries.SCALE_MODIFIERS, WAND_SCALE_MODIFIER_ID, WAND_SCALE_MODIFIER);
        initialized = true;
    }

    public static boolean ensurePlayerScale(Player player) {
        initialize();
        ScaleData data = ScaleTypes.BASE.getScaleData(player);
        float target = PixelScaleHelper.PLAYER_TINY_SCALE_FLOAT;
        boolean changed = false;
        if (Math.abs(data.getBaseScale() - target) > 1.0e-5F
                || Math.abs(data.getTargetScale() - target) > 1.0e-5F) {
            data.setScale(target);
            changed = true;
        }
        changed |= ensureModifier(ScaleTypes.MOTION.getScaleData(player), ScaleModifiers.BASE_MULTIPLIER);
        changed |= ensureModifier(ScaleTypes.REACH.getScaleData(player), ScaleModifiers.BASE_MULTIPLIER);
        // Keep the mod's vanilla 1.5-block hand range, not Pehkui's reach-derived multipliers.
        changed |= removeModifier(ScaleTypes.BLOCK_REACH.getScaleData(player), ScaleModifiers.REACH_MULTIPLIER);
        changed |= removeModifier(ScaleTypes.ENTITY_REACH.getScaleData(player), ScaleModifiers.REACH_MULTIPLIER);
        // Fall damage follows real world distance as it does without Pehkui.
        changed |= removeModifier(ScaleTypes.FALLING.getScaleData(player), ScaleModifiers.MOTION_DIVISOR);
        return changed;
    }

    public static boolean ensureHuntScale(LivingEntity entity) {
        if (entity instanceof Player) {
            return false;
        }
        initialize();
        removeWandScale(entity);
        ScaleData data = ScaleTypes.BASE.getScaleData(entity);
        float target = PixelScaleHelper.PLAYER_TINY_SCALE_FLOAT;
        boolean changed = false;
        if (Math.abs(data.getBaseScale() - target) > 1.0e-5F
                || Math.abs(data.getTargetScale() - target) > 1.0e-5F) {
            data.setScale(target);
            changed = true;
        }
        changed |= ensureModifier(ScaleTypes.MOTION.getScaleData(entity), ScaleModifiers.BASE_MULTIPLIER);
        changed |= removeModifier(ScaleTypes.FALLING.getScaleData(entity), ScaleModifiers.MOTION_DIVISOR);
        return changed;
    }

    public static boolean ensureWandScale(LivingEntity entity) {
        if (entity instanceof Player || !isWandScaled(entity)) {
            return false;
        }
        initialize();
        ScaleData baseData = ScaleTypes.BASE.getScaleData(entity);
        boolean changed = baseData.getBaseValueModifiers().add(WAND_SCALE_MODIFIER);
        if (changed) {
            baseData.onUpdate();
        }
        changed |= ensureModifier(ScaleTypes.MOTION.getScaleData(entity), ScaleModifiers.BASE_MULTIPLIER);
        changed |= ensureModifier(ScaleTypes.REACH.getScaleData(entity), ScaleModifiers.BASE_MULTIPLIER);
        // Unlike the normal-size Pehkui path, tiny entities retain world-distance fall damage.
        changed |= removeModifier(ScaleTypes.FALLING.getScaleData(entity), ScaleModifiers.MOTION_DIVISOR);
        return changed;
    }

    public static void clearPlayerScale(Player player) {
        initialize();
        ScaleData data = ScaleTypes.BASE.getScaleData(player);
        if (Math.abs(data.getBaseScale() - 1.0F) > 1.0e-5F
                || Math.abs(data.getTargetScale() - 1.0F) > 1.0e-5F) {
            data.setScale(1.0F);
        }
        ensureSizeDerivedScaling(player);
    }

    public static void removeWandScale(LivingEntity entity) {
        initialize();
        ScaleData baseData = ScaleTypes.BASE.getScaleData(entity);
        if (baseData.getBaseValueModifiers().remove(WAND_SCALE_MODIFIER)) {
            baseData.onUpdate();
        }
    }

    public static void restoreSizeDerivedScaling(LivingEntity entity) {
        initialize();
        ensureSizeDerivedScaling(entity);
    }

    public static float getEffectiveScale(Entity entity) {
        initialize();
        return ScaleTypes.BASE.getScaleData(entity).getScale();
    }

    private static boolean ensureSizeDerivedScaling(Entity entity) {
        boolean changed = ensureModifier(ScaleTypes.MOTION.getScaleData(entity), ScaleModifiers.BASE_MULTIPLIER);
        changed |= ensureModifier(ScaleTypes.REACH.getScaleData(entity), ScaleModifiers.BASE_MULTIPLIER);
        changed |= ensureModifier(ScaleTypes.BLOCK_REACH.getScaleData(entity), ScaleModifiers.REACH_MULTIPLIER);
        changed |= ensureModifier(ScaleTypes.ENTITY_REACH.getScaleData(entity), ScaleModifiers.REACH_MULTIPLIER);
        changed |= ensureModifier(ScaleTypes.FALLING.getScaleData(entity), ScaleModifiers.MOTION_DIVISOR);
        return changed;
    }

    private static boolean ensureModifier(ScaleData data, ScaleModifier modifier) {
        if (data.getBaseValueModifiers().add(modifier)) {
            data.onUpdate();
            return true;
        }
        return false;
    }

    private static boolean removeModifier(ScaleData data, ScaleModifier modifier) {
        if (data.getBaseValueModifiers().remove(modifier)) {
            data.onUpdate();
            return true;
        }
        return false;
    }

    private static boolean isWandScaled(Entity entity) {
        if (!(entity instanceof LivingEntity living) || living instanceof Player || living.getAttributes() == null
                || !living.getAttributes().hasAttribute(ModAttributes.PIXEL_SCALE)) {
            return false;
        }
        AttributeInstance marker = living.getAttribute(ModAttributes.PIXEL_SCALE);
        return marker != null && marker.hasModifier(PixelScaleHelper.TINY_PIXEL_SCALE_ID);
    }
}
