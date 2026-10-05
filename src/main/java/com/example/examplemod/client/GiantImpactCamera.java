package com.example.examplemod.client;

import com.example.examplemod.network.GiantImpactPayload;
import com.example.examplemod.scale.PixelScaleHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Angle-only shake; magnitude and duration come from the authoritative server. */
public final class GiantImpactCamera {
    private static ClientLevel shakeLevel;
    private static Player shakePlayer;
    private static float amplitude;
    private static int duration;
    private static int remainingTicks;

    public static void handlePayload(GiantImpactPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!validPlayer(minecraft) || !Float.isFinite(payload.intensity())
                || !Float.isFinite(payload.amplitude()) || payload.intensity() <= 0
                || payload.amplitude() <= 0 || payload.duration() <= 0) return;
        if (shakeLevel != minecraft.level || shakePlayer != minecraft.player) reset();
        float ceiling = Math.clamp(payload.amplitude(), 0, 45);
        float decayed = duration > 0 ? amplitude * remainingTicks / duration : 0;
        amplitude = Math.min(ceiling, decayed + ceiling * Math.clamp(payload.intensity(), 0, 1));
        duration = Math.clamp(payload.duration(), 1, 100);
        remainingTicks = duration;
        shakeLevel = minecraft.level;
        shakePlayer = minecraft.player;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!validPlayer(minecraft) || shakeLevel != minecraft.level || shakePlayer != minecraft.player) {
            reset();
        } else if (!minecraft.isPaused() && remainingTicks > 0 && --remainingTicks == 0) {
            reset();
        }
    }

    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!validPlayer(minecraft) || shakeLevel != minecraft.level || shakePlayer != minecraft.player) {
            reset();
            return;
        }
        if (remainingTicks <= 0 || event.getCamera().getEntity() != minecraft.player) return;
        float partial = (float) event.getPartialTick();
        float decay = Math.clamp((remainingTicks - partial) / duration, 0, 1);
        float elapsed = duration - remainingTicks + partial;
        event.setPitch(event.getPitch() + (float) Math.sin(elapsed * Math.PI * 0.9) * amplitude * decay);
        event.setRoll(event.getRoll() + (float) Math.cos(elapsed * Math.PI * 1.3) * amplitude * 0.75F * decay);
    }

    private static boolean validPlayer(Minecraft minecraft) {
        Player player = minecraft.player;
        return minecraft.level != null && player != null && player.isAlive()
                && PixelScaleHelper.isTiny(player) && !player.isCreative() && !player.isSpectator();
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) { reset(); }
    public static void onClone(ClientPlayerNetworkEvent.Clone event) { reset(); }

    public static void reset() {
        remainingTicks = 0;
        duration = 0;
        amplitude = 0;
        shakeLevel = null;
        shakePlayer = null;
    }

    private GiantImpactCamera() { }
}
