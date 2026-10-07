package com.example.examplemod.scale;

/**
 * Live-tunable standalone tiny player motion parameters, adjusted at runtime via /tiny commands
 * (permission level 2). Pehkui mode uses its own size-derived motion; these values apply without Pehkui.
 * Values are not persisted; defaults return on restart.
 */
public final class TinyMotionTuning {
    public static double speedScale = 0.25D;
    public static double jumpScale = 0.25D;
    public static double gravityScale = 0.25D;
    public static float airDrag = 0.85F;
    public static double groundAccelRatio = 0.1D;
    public static int rainOxygenPerTick = 1;

    private TinyMotionTuning() {
    }
}
