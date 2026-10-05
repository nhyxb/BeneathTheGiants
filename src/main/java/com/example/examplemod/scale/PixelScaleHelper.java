package com.example.examplemod.scale;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.init.ModAttributes;
import com.example.examplemod.mixin.LivingEntityAccessor;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.entity.PartEntity;

import java.util.List;

public class PixelScaleHelper {
    public static final double PLAYER_TINY_SCALE = 1.0 / 28.8;
    public static final float PLAYER_TINY_SCALE_FLOAT = (float) PLAYER_TINY_SCALE;

    // Match the chosen Pehkui-style motion multiplier, independently of visual size.
    public static final double TINY_MOTION_SCALE = 0.25D;
    public static final double TINY_WALL_CLIMB_SPEED = (0.2D - 0.08D) * 0.98D * TINY_MOTION_SCALE;

    public static final double WAND_SCALE = 0.5D;
    public static final float WAND_SCALE_FLOAT = (float) WAND_SCALE;

    // Retained aliases
    public static final double TINY_SCALE = PLAYER_TINY_SCALE;
    public static final float TINY_SCALE_FLOAT = PLAYER_TINY_SCALE_FLOAT;

    public static final ResourceLocation TINY_PIXEL_SCALE_ID = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "tiny_pixel_scale");
    public static final ResourceLocation TINY_MAX_HEALTH_ID = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "tiny_max_health");
    public static final ResourceLocation TINY_MOVEMENT_SPEED_ID = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "tiny_movement_speed");
    public static final ResourceLocation TINY_JUMP_STRENGTH_ID = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "tiny_jump_strength");
    public static final ResourceLocation TINY_STEP_HEIGHT_ID = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "tiny_step_height");
    public static final ResourceLocation TINY_GRAVITY_ID = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "tiny_gravity");
    public static final ResourceLocation TINY_BLOCK_RANGE_ID = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "tiny_block_range");
    public static final ResourceLocation TINY_ENTITY_RANGE_ID = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "tiny_entity_range");

    public enum ToggleResult {
        APPLIED_MINI,
        RESTORED,
        RESTORE_BLOCKED,
        REJECTED_PLAYER,
        IGNORED_NON_LIVING,
        COOLDOWN_ACTIVE
    }

    public static boolean hasAttribute(LivingEntity entity, Holder<Attribute> attribute) {
        return entity != null && entity.getAttributes() != null && entity.getAttributes().hasAttribute(attribute);
    }

    public static boolean isTiny(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        if (entity instanceof Player) {
            return true;
        }
        if (entity.getAttributes() == null) {
            return false;
        }
        return entity.getAttributes().hasModifier(ModAttributes.PIXEL_SCALE, TINY_PIXEL_SCALE_ID);
    }

    public static void applyTinyModifiers(LivingEntity entity) {
        if (entity == null || entity.getAttributes() == null) {
            return;
        }
        if (entity instanceof Player player) {
            ensurePlayerMini(player);
            return;
        }

        // 1. Pixel scale modifier (WAND_SCALE 0.5 via ADD_MULTIPLIED_TOTAL -0.5)
        if (hasAttribute(entity, ModAttributes.PIXEL_SCALE)) {
            AttributeInstance scaleInst = entity.getAttribute(ModAttributes.PIXEL_SCALE);
            if (scaleInst != null) {
                scaleInst.addOrReplacePermanentModifier(new AttributeModifier(
                        TINY_PIXEL_SCALE_ID,
                        -0.5D,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                ));
            }
        }

        // 2. Max health (ratio preserved, no free healing)
        if (hasAttribute(entity, Attributes.MAX_HEALTH)) {
            AttributeInstance healthInst = entity.getAttribute(Attributes.MAX_HEALTH);
            if (healthInst != null && !healthInst.hasModifier(TINY_MAX_HEALTH_ID)) {
                float oldMax = entity.getMaxHealth();
                float oldHealth = entity.getHealth();
                float ratio = oldMax > 0 ? (oldHealth / oldMax) : 1.0F;

                healthInst.addOrReplacePermanentModifier(new AttributeModifier(
                        TINY_MAX_HEALTH_ID,
                        -0.5D,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                ));

                float newHealth = ratio * entity.getMaxHealth();
                entity.setHealth(Math.min(newHealth, entity.getMaxHealth()));
            }
        }

        // 3. Movement speed (x0.25 -> amount = -0.75)
        if (hasAttribute(entity, Attributes.MOVEMENT_SPEED)) {
            AttributeInstance speedInst = entity.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedInst != null) {
                speedInst.addOrReplacePermanentModifier(new AttributeModifier(
                        TINY_MOVEMENT_SPEED_ID,
                        -0.75D,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                ));
            }
        }

        // 4. Jump strength (x0.5 -> amount = -0.5)
        if (hasAttribute(entity, Attributes.JUMP_STRENGTH)) {
            AttributeInstance jumpInst = entity.getAttribute(Attributes.JUMP_STRENGTH);
            if (jumpInst != null) {
                jumpInst.addOrReplacePermanentModifier(new AttributeModifier(
                        TINY_JUMP_STRENGTH_ID,
                        -0.5D,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                ));
            }
        }

        // 5. Step height (scaled to target half-scale, minimum 1/16 block)
        if (hasAttribute(entity, Attributes.STEP_HEIGHT)) {
            AttributeInstance stepInst = entity.getAttribute(Attributes.STEP_HEIGHT);
            if (stepInst != null) {
                double base = stepInst.getBaseValue();
                double target = Math.max(1.0 / 16.0, base * WAND_SCALE);
                stepInst.addOrReplacePermanentModifier(new AttributeModifier(
                        TINY_STEP_HEIGHT_ID,
                        target - base,
                        AttributeModifier.Operation.ADD_VALUE
                ));
            }
        }

        entity.refreshDimensions();
        if (entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon) {
            dragon.refreshDimensions();
            if (dragon.getSubEntities() != null) {
                for (net.minecraft.world.entity.boss.EnderDragonPart part : dragon.getSubEntities()) {
                    part.refreshDimensions();
                }
            }
        }
    }

    public static void removeTinyModifiers(LivingEntity entity) {
        if (entity == null || entity.getAttributes() == null) {
            return;
        }

        // 1. Pixel scale
        if (hasAttribute(entity, ModAttributes.PIXEL_SCALE)) {
            AttributeInstance scaleInst = entity.getAttribute(ModAttributes.PIXEL_SCALE);
            if (scaleInst != null) {
                scaleInst.removeModifier(TINY_PIXEL_SCALE_ID);
            }
        }

        // 2. Health ratio mapped back
        if (hasAttribute(entity, Attributes.MAX_HEALTH)) {
            AttributeInstance healthInst = entity.getAttribute(Attributes.MAX_HEALTH);
            if (healthInst != null && healthInst.hasModifier(TINY_MAX_HEALTH_ID)) {
                float oldMax = entity.getMaxHealth();
                float oldHealth = entity.getHealth();
                float ratio = oldMax > 0 ? (oldHealth / oldMax) : 1.0F;

                healthInst.removeModifier(TINY_MAX_HEALTH_ID);

                float newHealth = ratio * entity.getMaxHealth();
                entity.setHealth(Math.min(newHealth, entity.getMaxHealth()));
            }
        }

        // 3. Movement speed
        if (hasAttribute(entity, Attributes.MOVEMENT_SPEED)) {
            AttributeInstance speedInst = entity.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedInst != null) {
                speedInst.removeModifier(TINY_MOVEMENT_SPEED_ID);
            }
        }

        // 4. Jump strength
        if (hasAttribute(entity, Attributes.JUMP_STRENGTH)) {
            AttributeInstance jumpInst = entity.getAttribute(Attributes.JUMP_STRENGTH);
            if (jumpInst != null) {
                jumpInst.removeModifier(TINY_JUMP_STRENGTH_ID);
            }
        }

        // 5. Step height
        if (hasAttribute(entity, Attributes.STEP_HEIGHT)) {
            AttributeInstance stepInst = entity.getAttribute(Attributes.STEP_HEIGHT);
            if (stepInst != null) {
                stepInst.removeModifier(TINY_STEP_HEIGHT_ID);
            }
        }

        // 5.5. Gravity
        if (hasAttribute(entity, Attributes.GRAVITY)) {
            AttributeInstance gravityInst = entity.getAttribute(Attributes.GRAVITY);
            if (gravityInst != null) {
                gravityInst.removeModifier(TINY_GRAVITY_ID);
            }
        }

        // 6. Player reach ranges
        if (entity instanceof Player) {
            if (hasAttribute(entity, Attributes.BLOCK_INTERACTION_RANGE)) {
                AttributeInstance reachInst = entity.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
                if (reachInst != null) {
                    reachInst.removeModifier(TINY_BLOCK_RANGE_ID);
                }
            }
            if (hasAttribute(entity, Attributes.ENTITY_INTERACTION_RANGE)) {
                AttributeInstance reachInst = entity.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
                if (reachInst != null) {
                    reachInst.removeModifier(TINY_ENTITY_RANGE_ID);
                }
            }
        }

        entity.refreshDimensions();
        if (entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon) {
            dragon.refreshDimensions();
            if (dragon.getSubEntities() != null) {
                for (net.minecraft.world.entity.boss.EnderDragonPart part : dragon.getSubEntities()) {
                    part.refreshDimensions();
                }
            }
        }
    }

    public static void ensurePlayerMini(Player player) {
        if (player == null) {
            return;
        }
        if (player.level().isClientSide) {
            if (Math.abs(player.getScale() - PLAYER_TINY_SCALE_FLOAT) > 1e-4 || player.getBbHeight() > 0.1F) {
                player.refreshDimensions();
            }
            return;
        }

        if (player.getAttributes() == null) {
            return;
        }

        boolean changed = false;

        // 1. Pixel scale modifier
        if (hasAttribute(player, ModAttributes.PIXEL_SCALE)) {
            AttributeInstance scaleInst = player.getAttribute(ModAttributes.PIXEL_SCALE);
            if (scaleInst != null && !scaleInst.hasModifier(TINY_PIXEL_SCALE_ID)) {
                scaleInst.addOrReplacePermanentModifier(new AttributeModifier(
                        TINY_PIXEL_SCALE_ID,
                        PLAYER_TINY_SCALE - 1.0D,
                        AttributeModifier.Operation.ADD_VALUE
                ));
                changed = true;
            }
        }

        // 2. Max health (halved, ratio preserved)
        if (hasAttribute(player, Attributes.MAX_HEALTH)) {
            AttributeInstance healthInst = player.getAttribute(Attributes.MAX_HEALTH);
            if (healthInst != null && !healthInst.hasModifier(TINY_MAX_HEALTH_ID)) {
                float oldMax = player.getMaxHealth();
                float oldHealth = player.getHealth();
                float ratio = oldMax > 0 ? (oldHealth / oldMax) : 1.0F;

                healthInst.addOrReplacePermanentModifier(new AttributeModifier(
                        TINY_MAX_HEALTH_ID,
                        -0.5D,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                ));

                float newHealth = ratio * player.getMaxHealth();
                player.setHealth(Math.min(newHealth, player.getMaxHealth()));
                changed = true;
            }
        }

        // 3. Movement speed (tunable via /tiny speed; default x0.25, migrates legacy -0.65)
        if (hasAttribute(player, Attributes.MOVEMENT_SPEED)) {
            AttributeInstance speedInst = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedInst != null) {
                AttributeModifier existing = speedInst.getModifier(TINY_MOVEMENT_SPEED_ID);
                if (existing == null || existing.operation() != AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL || Math.abs(existing.amount() - (TinyMotionTuning.speedScale - 1.0D)) > 1e-6) {
                    speedInst.addOrReplacePermanentModifier(new AttributeModifier(
                            TINY_MOVEMENT_SPEED_ID,
                            TinyMotionTuning.speedScale - 1.0D,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                    ));
                    changed = true;
                }
            }
        }

        // 4. Jump strength (tunable via /tiny jump; default x0.25, aligned with movement per Pehkui scaledMotion)
        if (hasAttribute(player, Attributes.JUMP_STRENGTH)) {
            AttributeInstance jumpInst = player.getAttribute(Attributes.JUMP_STRENGTH);
            if (jumpInst != null) {
                AttributeModifier existing = jumpInst.getModifier(TINY_JUMP_STRENGTH_ID);
                if (existing == null || existing.operation() != AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                        || Math.abs(existing.amount() - (TinyMotionTuning.jumpScale - 1.0D)) > 1e-6) {
                    jumpInst.addOrReplacePermanentModifier(new AttributeModifier(
                            TINY_JUMP_STRENGTH_ID,
                            TinyMotionTuning.jumpScale - 1.0D,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                    ));
                    changed = true;
                }
            }
        }

        // 5. Step height
        if (hasAttribute(player, Attributes.STEP_HEIGHT)) {
            AttributeInstance stepInst = player.getAttribute(Attributes.STEP_HEIGHT);
            if (stepInst != null && !stepInst.hasModifier(TINY_STEP_HEIGHT_ID)) {
                double base = stepInst.getBaseValue();
                double target = Math.max(1.0 / 16.0, base * PLAYER_TINY_SCALE);
                stepInst.addOrReplacePermanentModifier(new AttributeModifier(
                        TINY_STEP_HEIGHT_ID,
                        target - base,
                        AttributeModifier.Operation.ADD_VALUE
                ));
                changed = true;
            }
        }

        // 5.5. Gravity (tunable via /tiny gravity; default x0.25, aligned with movement per Pehkui scaledMotion)
        if (hasAttribute(player, Attributes.GRAVITY)) {
            AttributeInstance gravityInst = player.getAttribute(Attributes.GRAVITY);
            if (gravityInst != null) {
                AttributeModifier existing = gravityInst.getModifier(TINY_GRAVITY_ID);
                if (existing == null || existing.operation() != AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                        || Math.abs(existing.amount() - (TinyMotionTuning.gravityScale - 1.0D)) > 1e-6) {
                    gravityInst.addOrReplacePermanentModifier(new AttributeModifier(
                            TINY_GRAVITY_ID,
                            TinyMotionTuning.gravityScale - 1.0D,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                    ));
                    changed = true;
                }
            }
        }

        // 6. Block interaction range
        if (hasAttribute(player, Attributes.BLOCK_INTERACTION_RANGE)) {
            AttributeInstance reachInst = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
            if (reachInst != null && !reachInst.hasModifier(TINY_BLOCK_RANGE_ID)) {
                double base = reachInst.getBaseValue();
                double target = Math.max(1.5, base * PLAYER_TINY_SCALE);
                reachInst.addOrReplacePermanentModifier(new AttributeModifier(
                        TINY_BLOCK_RANGE_ID,
                        target - base,
                        AttributeModifier.Operation.ADD_VALUE
                ));
                changed = true;
            }
        }

        // 7. Entity interaction range
        if (hasAttribute(player, Attributes.ENTITY_INTERACTION_RANGE)) {
            AttributeInstance reachInst = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
            if (reachInst != null && !reachInst.hasModifier(TINY_ENTITY_RANGE_ID)) {
                double base = reachInst.getBaseValue();
                double target = Math.max(1.5, base * PLAYER_TINY_SCALE);
                reachInst.addOrReplacePermanentModifier(new AttributeModifier(
                        TINY_ENTITY_RANGE_ID,
                        target - base,
                        AttributeModifier.Operation.ADD_VALUE
                ));
                changed = true;
            }
        }

        if (changed) {
            player.refreshDimensions();
        }
    }

    public static void migrateLivingEntity(LivingEntity entity) {
        if (entity == null || entity instanceof Player || entity.level().isClientSide || entity.getAttributes() == null) {
            return;
        }

        if (!hasAttribute(entity, ModAttributes.PIXEL_SCALE)) {
            return;
        }
        AttributeInstance scaleInst = entity.getAttribute(ModAttributes.PIXEL_SCALE);
        if (scaleInst == null || !scaleInst.hasModifier(TINY_PIXEL_SCALE_ID)) {
            return;
        }

        boolean changed = false;

        // 1. Update PIXEL_SCALE modifier to ADD_MULTIPLIED_TOTAL -0.5D
        AttributeModifier scaleMod = scaleInst.getModifier(TINY_PIXEL_SCALE_ID);
        if (scaleMod != null && (scaleMod.operation() != AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL || Math.abs(scaleMod.amount() - (-0.5D)) > 1e-6)) {
            scaleInst.addOrReplacePermanentModifier(new AttributeModifier(
                    TINY_PIXEL_SCALE_ID,
                    -0.5D,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            ));
            changed = true;
        }

        changed |= ensureModifier(entity, Attributes.MOVEMENT_SPEED, TINY_MOVEMENT_SPEED_ID,
                -0.75D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        changed |= ensureModifier(entity, Attributes.JUMP_STRENGTH, TINY_JUMP_STRENGTH_ID,
                -0.5D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        // Repair saved partial states without applying the initial health ratio again.
        float oldHealth = entity.getHealth();
        boolean healthChanged = ensureModifier(entity, Attributes.MAX_HEALTH, TINY_MAX_HEALTH_ID,
                -0.5D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        if (healthChanged && oldHealth > entity.getMaxHealth()) {
            entity.setHealth(entity.getMaxHealth());
        }
        changed |= healthChanged;

        if (hasAttribute(entity, Attributes.STEP_HEIGHT)) {
            AttributeInstance stepInst = entity.getAttribute(Attributes.STEP_HEIGHT);
            if (stepInst != null) {
                double base = stepInst.getBaseValue();
                double target = Math.max(1.0 / 16.0, base * WAND_SCALE);
                changed |= ensureModifier(entity, Attributes.STEP_HEIGHT, TINY_STEP_HEIGHT_ID,
                        target - base, AttributeModifier.Operation.ADD_VALUE);
            }
        }

        // Loaded attributes can already be correct while the entity still caches its original dimensions.
        EntityDimensions expectedDimensions = entity.getDimensions(entity.getPose());
        boolean dimensionsMismatch = Math.abs(entity.getBbWidth() - expectedDimensions.width()) > 1e-6F
                || Math.abs(entity.getBbHeight() - expectedDimensions.height()) > 1e-6F;
        if (changed || dimensionsMismatch) {
            entity.refreshDimensions();
            if (entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon) {
                dragon.refreshDimensions();
                if (dragon.getSubEntities() != null) {
                    for (net.minecraft.world.entity.boss.EnderDragonPart part : dragon.getSubEntities()) {
                        part.refreshDimensions();
                    }
                }
            }
        }
    }

    private static boolean ensureModifier(LivingEntity entity, Holder<Attribute> attribute,
                                          ResourceLocation id, double amount, AttributeModifier.Operation operation) {
        if (!hasAttribute(entity, attribute)) {
            return false;
        }
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return false;
        }
        AttributeModifier existing = instance.getModifier(id);
        if (existing != null && existing.operation() == operation && Math.abs(existing.amount() - amount) <= 1e-6) {
            return false;
        }
        instance.addOrReplacePermanentModifier(new AttributeModifier(id, amount, operation));
        return true;
    }

    public static double calculateRestoredPixelScale(LivingEntity entity) {
        if (!hasAttribute(entity, ModAttributes.PIXEL_SCALE)) {
            return 1.0D;
        }
        AttributeInstance inst = entity.getAttribute(ModAttributes.PIXEL_SCALE);
        if (inst == null) {
            return 1.0D;
        }
        double base = inst.getBaseValue();
        double d0 = base;
        for (AttributeModifier mod : inst.getModifiers()) {
            if (mod.operation() == AttributeModifier.Operation.ADD_VALUE && !mod.id().equals(TINY_PIXEL_SCALE_ID)) {
                d0 += mod.amount();
            }
        }
        double d1 = d0;
        for (AttributeModifier mod : inst.getModifiers()) {
            if (mod.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE && !mod.id().equals(TINY_PIXEL_SCALE_ID)) {
                d1 += d0 * mod.amount();
            }
        }
        for (AttributeModifier mod : inst.getModifiers()) {
            if (mod.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL && !mod.id().equals(TINY_PIXEL_SCALE_ID)) {
                d1 *= (1.0D + mod.amount());
            }
        }
        return ModAttributes.PIXEL_SCALE.value().sanitizeValue(d1);
    }

    public static boolean canRestoreSafely(LivingEntity entity) {
        Level level = entity.level();
        Pose pose = entity.getPose();
        double vanillaScale = hasAttribute(entity, Attributes.SCALE) ? entity.getAttributeValue(Attributes.SCALE) : 1.0D;
        double restoredPixelScale = calculateRestoredPixelScale(entity);
        float totalScale = (float) (vanillaScale * restoredPixelScale);

        EntityDimensions defaultDims = ((LivingEntityAccessor) entity).examplemod$callGetDefaultDimensions(pose);
        EntityDimensions restoredDims = defaultDims.scale(totalScale);
        AABB restoredAabb = restoredDims.makeBoundingBox(entity.position());

        // Check world block collisions
        if (!level.noBlockCollision(entity, restoredAabb)) {
            return false;
        }

        // Check collisions with other non-spectator entities (exclude entity itself and its own multipart parts)
        List<Entity> colliding = level.getEntities(entity, restoredAabb, e -> {
            if (e == entity || e.isSpectator() || !e.isPickable()) {
                return false;
            }
            if (e instanceof PartEntity<?> part && part.getParent() == entity) {
                return false;
            }
            if (e instanceof EnderDragonPart dragonPart && dragonPart.parentMob == entity) {
                return false;
            }
            return true;
        });
        return colliding.isEmpty();
    }

    public static ToggleResult toggleLivingTarget(Player actor, LivingEntity target) {
        if (target instanceof Player) {
            return ToggleResult.REJECTED_PLAYER;
        }
        if (!target.isAlive() || target.isRemoved()) {
            return ToggleResult.IGNORED_NON_LIVING;
        }
        if (actor != null && actor.getCooldowns().isOnCooldown(com.example.examplemod.init.ModItems.SCALE_WAND.get())) {
            return ToggleResult.COOLDOWN_ACTIVE;
        }
        if (isTiny(target)) {
            if (!canRestoreSafely(target)) {
                return ToggleResult.RESTORE_BLOCKED;
            }
            removeTinyModifiers(target);
            return ToggleResult.RESTORED;
        } else {
            applyTinyModifiers(target);
            return ToggleResult.APPLIED_MINI;
        }
    }
}
