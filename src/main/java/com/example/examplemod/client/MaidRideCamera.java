package com.example.examplemod.client;

import com.example.examplemod.maid.MaidHeadSeat;
import com.example.examplemod.maid.MaidRideShake;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Angle-only shake while the local player rides a moving maid. */
public final class MaidRideCamera {
    private static float phase;
    private static float strength;

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            reset();
            return;
        }
        Entity vehicle = minecraft.player.getVehicle();
        if (!MaidHeadSeat.isMaid(vehicle) || minecraft.isPaused()) {
            if (!MaidHeadSeat.isMaid(vehicle)) {
                reset();
            }
            return;
        }
        if (moving(vehicle)) {
            phase += 1.0F;
            strength = Math.min(1.0F, strength + 0.5F);
        } else {
            strength = Math.max(0.0F, strength - 0.5F);
        }
    }

    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (strength <= 0.0F) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || event.getCamera().getEntity() != minecraft.player) {
            return;
        }
        Entity vehicle = minecraft.player.getVehicle();
        if (!MaidHeadSeat.isMaid(vehicle)) {
            reset();
            return;
        }
        float partial = minecraft.isPaused() || !moving(vehicle) ? 0.0F : (float) event.getPartialTick();
        float sample = phase + partial;
        event.setPitch(event.getPitch() + MaidRideShake.pitchOffset(sample, strength));
        event.setYaw(event.getYaw() + MaidRideShake.yawOffset(sample, strength));
        event.setRoll(event.getRoll() + MaidRideShake.rollOffset(sample, strength));
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    public static void onClone(ClientPlayerNetworkEvent.Clone event) {
        reset();
    }

    private static boolean moving(Entity vehicle) {
        return MaidRideShake.isMoving(
                vehicle.getX() - vehicle.xo,
                vehicle.getY() - vehicle.yo,
                vehicle.getZ() - vehicle.zo);
    }

    public static void reset() {
        phase = 0.0F;
        strength = 0.0F;
    }

    private MaidRideCamera() {
    }
}
