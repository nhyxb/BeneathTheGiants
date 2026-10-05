package com.example.examplemod.hiding;

import com.example.examplemod.config.SurvivalConfig;
import com.example.examplemod.devour.DevourRules;
import com.example.examplemod.scale.PixelScaleHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/** Concealment changes discovery only; never clears an existing target or retaliation memory. */
public final class HidingRules {
    private static final Map<Player, State> STATES = new WeakHashMap<>();
    private static final double POSITION_TOLERANCE_SQR = 1e-8;

    private static final class State {
        final Vec3 anchor;
        final int levelIdentity;
        long lastTick;
        int ticks;
        State(Player player, long tick) {
            anchor = player.position();
            levelIdentity = System.identityHashCode(player.level());
            lastTick = tick;
            ticks = 1;
        }
    }

    public static void tick(Player player) { tick(player, player.level().getGameTime()); }

    public static void tick(Player player, long now) {
        if (player.level().isClientSide()) return;
        if (!eligible(player)) {
            clear(player);
            return;
        }
        State state = STATES.get(player);
        if (state == null || state.levelIdentity != System.identityHashCode(player.level())
                || state.anchor.distanceToSqr(player.position()) > POSITION_TOLERANCE_SQR) {
            STATES.put(player, new State(player, now));
        } else if (state.lastTick != now) {
            if (state.lastTick == now - 1) {
                state.ticks = Math.min(state.ticks + 1, SurvivalConfig.HIDE_STILL_TICKS.get());
                state.lastTick = now;
            } else {
                STATES.put(player, new State(player, now));
            }
        }
    }

    public static int getHideTicks(Player player) {
        State state = STATES.get(player);
        return validState(player, state) ? state.ticks : 0;
    }

    public static boolean isHidden(Player player) {
        return getHideTicks(player) >= SurvivalConfig.HIDE_STILL_TICKS.get();
    }

    public static double visibilityMultiplier(Player player, Entity observer) {
        if (player.level().isClientSide() || !isHidden(player)) return 1.0;
        if (observer instanceof Mob mob && mob.getTarget() == player) return 1.0;
        return SurvivalConfig.HIDE_VISIBILITY_MULTIPLIER.get();
    }

    private static boolean validState(Player player, State state) {
        return state != null && eligible(player)
                && state.levelIdentity == System.identityHashCode(player.level())
                && state.anchor.distanceToSqr(player.position()) <= POSITION_TOLERANCE_SQR;
    }

    private static boolean eligible(Player player) {
        return player.isAlive() && !player.isRemoved() && !player.isCreative() && !player.isSpectator()
                && PixelScaleHelper.isTiny(player) && player.isShiftKeyDown()
                && !player.isPassenger() && !DevourRules.isCaptured(player);
    }

    public static void clear(Player player) { STATES.remove(player); }
    public static void clearAll() { STATES.clear(); }
    private HidingRules() { }
}
