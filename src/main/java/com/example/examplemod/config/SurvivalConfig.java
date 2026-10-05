package com.example.examplemod.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class SurvivalConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue WEB_VIBRATION_RANGE = BUILDER
            .comment("Maximum distance in blocks at which spiders hear a tiny player's cobweb vibration.")
            .defineInRange("webVibrationRange", 12, 1, 64);

    public static final ModConfigSpec.DoubleValue GIANT_IMPACT_SHAKE_RADIUS = BUILDER
            .comment("Screen shake radius in blocks for giant impacts.")
            .defineInRange("giantImpactShakeRadius", 6.0D, 0.0D, 64.0D);

    public static final ModConfigSpec.DoubleValue GIANT_IMPACT_KNOCKBACK_RADIUS = BUILDER
            .comment("Knockback radius in blocks for giant impacts.")
            .defineInRange("giantImpactKnockbackRadius", 2.0D, 0.0D, 32.0D);

    public static final ModConfigSpec.DoubleValue GIANT_IMPACT_HORIZONTAL_IMPULSE = BUILDER
            .comment("Base horizontal impulse for giant landing impacts.")
            .defineInRange("giantImpactHorizontalImpulse", 0.18D, 0.0D, 5.0D);

    public static final ModConfigSpec.DoubleValue GIANT_IMPACT_VERTICAL_IMPULSE = BUILDER
            .comment("Base vertical impulse for giant landing impacts.")
            .defineInRange("giantImpactVerticalImpulse", 0.12D, 0.0D, 5.0D);

    public static final ModConfigSpec.DoubleValue GIANT_IMPACT_JUMP_FACTOR = BUILDER
            .comment("Jump impact multiplier relative to landing.")
            .defineInRange("giantImpactJumpFactor", 0.6D, 0.0D, 2.0D);

    public static final ModConfigSpec.DoubleValue GIANT_IMPACT_SHAKE_AMPLITUDE = BUILDER
            .comment("Maximum camera shake amplitude in degrees.")
            .defineInRange("giantImpactShakeAmplitude", 2.0D, 0.0D, 45.0D);

    public static final ModConfigSpec.IntValue GIANT_IMPACT_SHAKE_DURATION = BUILDER
            .comment("Camera shake duration in ticks.")
            .defineInRange("giantImpactShakeDuration", 10, 1, 100);

    public static final ModConfigSpec.DoubleValue DEVOUR_CHANCE = BUILDER
            .comment("Chance of humanoid capture after an accepted melee hit leaves a tiny player at <= 2 health.")
            .defineInRange("devourChance", 1.0D, 0.0D, 1.0D);

    public static final ModConfigSpec.IntValue DEVOUR_DURATION = BUILDER
            .comment("Ticks from capture to the unique final devoured damage.")
            .defineInRange("devourDuration", 60, 1, 1200);

    public static final ModConfigSpec.IntValue DEVOUR_COOLDOWN = BUILDER
            .comment("Cooldown in ticks for both attacker and player after an eligible capture attempt, including failed rolls.")
            .defineInRange("devourCooldown", 200, 1, 12000);

    public static final ModConfigSpec.DoubleValue AXOLOTL_VORTEX_RANGE = BUILDER
            .comment("Axolotl mouth vortex range in blocks; zero disables vortices.")
            .defineInRange("axolotlVortexRange", 3.0D, 0.0D, 16.0D);
    public static final ModConfigSpec.DoubleValue AXOLOTL_VORTEX_PULL = BUILDER
            .comment("Added velocity per tick toward an axolotl mouth.")
            .defineInRange("axolotlVortexPull", 0.006D, 0.0D, 0.1D);
    public static final ModConfigSpec.DoubleValue AXOLOTL_VORTEX_MAX_SPEED = BUILDER
            .comment("Maximum radial speed to which the vortex adds motion; existing player motion is preserved.")
            .defineInRange("axolotlVortexMaxSpeed", 0.06D, 0.0D, 0.5D);
    public static final ModConfigSpec.DoubleValue AXOLOTL_BITE_RANGE = BUILDER
            .comment("Distance from the mouth at which continuous biting starts.")
            .defineInRange("axolotlBiteRange", 0.24D, 0.01D, 1.0D);
    public static final ModConfigSpec.DoubleValue AXOLOTL_BITE_DAMAGE = BUILDER
            .comment("Damage per axolotl vortex bite in health points.")
            .defineInRange("axolotlBiteDamage", 0.5D, 0.0D, 20.0D);
    public static final ModConfigSpec.IntValue AXOLOTL_BITE_INTERVAL = BUILDER
            .comment("Ticks of uninterrupted mouth proximity before each bite.")
            .defineInRange("axolotlBiteInterval", 20, 10, 200);

    public static final ModConfigSpec.IntValue HIDE_STILL_TICKS = BUILDER
            .comment("Consecutive stationary sneaking ticks before a tiny player reduces discovery range.")
            .defineInRange("hideStillTicks", 60, 1, 1200);
    public static final ModConfigSpec.DoubleValue HIDE_VISIBILITY_MULTIPLIER = BUILDER
            .comment("Additional vanilla visibility multiplier while concealed; current mob targets retain normal visibility.")
            .defineInRange("hideVisibilityMultiplier", 0.2D, 0.05D, 1.0D);

    public static final ModConfigSpec.IntValue TORCH_BURN_TICKS = BUILDER
            .comment("Maximum native burning ticks renewed by hot torch contact, preserving the twenty-tick damage phase.")
            .defineInRange("torchBurnTicks", 80, 20, 1200);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private SurvivalConfig() {
    }
}
