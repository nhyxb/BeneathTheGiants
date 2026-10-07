package com.example.examplemod.maid;

import net.minecraft.util.Mth;

/** Angle offsets for the local camera while a ridden maid is translating. */
public final class MaidRideShake {
    /** Ignore sub-milliblock drift so standing still stays still. */
    public static final double MOVEMENT_EPSILON_SQR = 1.0E-8D;
    public static final float MAX_PITCH = 0.45F;
    public static final float MAX_YAW = 0.175F;
    public static final float MAX_ROLL = 0.3F;

    private MaidRideShake() {
    }

    public static boolean isMoving(double dx, double dy, double dz) {
        return dx * dx + dy * dy + dz * dz > MOVEMENT_EPSILON_SQR;
    }

    public static float pitchOffset(float phaseTicks, float strength) {
        return Mth.sin(phaseTicks * 1.7F) * MAX_PITCH * strength;
    }

    public static float yawOffset(float phaseTicks, float strength) {
        return Mth.sin(phaseTicks * 2.3F) * MAX_YAW * strength;
    }

    public static float rollOffset(float phaseTicks, float strength) {
        return Mth.cos(phaseTicks * 1.9F) * MAX_ROLL * strength;
    }
}
