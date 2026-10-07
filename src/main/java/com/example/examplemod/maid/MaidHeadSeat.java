package com.example.examplemod.maid;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.devour.DevourRules;
import com.example.examplemod.init.ModItems;
import com.example.examplemod.scale.PixelScaleHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.List;

/** Seat rules with no Touhou Little Maid types. Reflection runs only after that mod is present. */
public final class MaidHeadSeat {
    public static final ResourceLocation MAID_ID = ResourceLocation.fromNamespaceAndPath("touhou_little_maid", "maid");
    /** Riding uses one fifth of the maid's own movement speed. */
    public static final float RIDDEN_SPEED_FACTOR = 0.2F;
    private static final String MAID_MOD_ID = "touhou_little_maid";

    private static volatile Method getMaidBauble;
    private static volatile Method containsItem;
    private static volatile boolean lookupFailed;

    private MaidHeadSeat() {
    }

    public static boolean isMaid(Entity entity) {
        return entity != null && MAID_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
    }

    /** The maid this player is sitting on. Clicks and the crosshair skip her. */
    public static boolean isRiddenBy(Player player, Entity target) {
        return player != null && target != null && target == player.getVehicle() && isMaid(target);
    }

    public static boolean canSitOn(Player player, Entity maid) {
        return player != null
                && PixelScaleHelper.isTiny(player)
                && !player.isSpectator()
                && !DevourRules.isCaptured(player)
                && maid instanceof TamableAnimal animal
                && animal.isAlive()
                && player.getUUID().equals(animal.getOwnerUUID())
                && wearsSeat(maid);
    }

    public static Vec3 seatFeet(Entity maid) {
        float yaw = maid.getYRot() * ((float) Math.PI / 180.0F);
        double forwardX = -Mth.sin(yaw);
        double forwardZ = Mth.cos(yaw);
        double distance = maid.getBbWidth() * 0.5D + 0.05D;
        return new Vec3(
                maid.getX() + forwardX * distance,
                maid.getY() + maid.getBbHeight() * 0.55D,
                maid.getZ() + forwardZ * distance);
    }

    public static float riddenSpeed(LivingEntity maid) {
        return (float) maid.getAttributeValue(Attributes.MOVEMENT_SPEED) * RIDDEN_SPEED_FACTOR;
    }

    public static Vec3 riddenInput(Player player) {
        float forward = player.zza;
        if (forward <= 0.0F) {
            forward *= 0.25F;
        }
        return new Vec3(player.xxa * 0.5F, 0.0D, forward);
    }

    public static void tickRider(Player player) {
        Entity vehicle = player.getVehicle();
        if (isMaid(vehicle)) {
            dismountIfSeatRemoved(vehicle);
        }
    }

    public static void dismountIfSeatRemoved(Entity maid) {
        if (maid == null || maid.level().isClientSide() || !isMaid(maid) || wearsSeat(maid)) {
            return;
        }
        for (Entity passenger : List.copyOf(maid.getPassengers())) {
            if (passenger instanceof Player) {
                passenger.stopRiding();
            }
        }
    }

    public static boolean wearsSeat(Entity maid) {
        if (!ModList.get().isLoaded(MAID_MOD_ID) || !isMaid(maid)) {
            return false;
        }
        try {
            Method baubleGetter = getMaidBauble;
            Method contains = containsItem;
            if (baubleGetter == null || contains == null) {
                baubleGetter = maid.getClass().getMethod("getMaidBauble");
                contains = baubleGetter.getReturnType().getMethod("containsItem", Item.class);
                getMaidBauble = baubleGetter;
                containsItem = contains;
            }
            Object handler = baubleGetter.invoke(maid);
            return handler != null && (boolean) contains.invoke(handler, ModItems.MAID_HEAD_SEAT.get());
        } catch (ReflectiveOperationException exception) {
            if (!lookupFailed) {
                lookupFailed = true;
                ExampleMod.LOGGER.error("Touhou Little Maid bauble lookup failed", exception);
            }
            return false;
        }
    }

    public static void setSeatStack(Entity maid, ItemStack stack) {
        if (!isMaid(maid)) {
            throw new IllegalArgumentException("Not a maid");
        }
        try {
            Object handler = maid.getClass().getMethod("getMaidBauble").invoke(maid);
            handler.getClass().getMethod("setStackInSlot", int.class, ItemStack.class).invoke(handler, 0, stack);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to edit the maid bauble inventory", exception);
        }
    }
}
