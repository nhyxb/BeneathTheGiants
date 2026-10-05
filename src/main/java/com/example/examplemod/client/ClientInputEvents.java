package com.example.examplemod.client;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.devour.DevourRules;
import com.example.examplemod.init.ModTags;
import com.example.examplemod.network.SurvivalPayload;
import com.example.examplemod.scale.PixelScaleHelper;
import com.example.examplemod.survival.SurvivalRules;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class ClientInputEvents {
    private static final KeyMapping MOUNT_KEY = new KeyMapping(
            "key.examplemod.mount_livestock", GLFW.GLFW_KEY_G, "key.categories.examplemod");
    private static final KeyMapping CLIMB_TOGGLE_KEY = new KeyMapping(
            "key.examplemod.toggle_climbing", GLFW.GLFW_KEY_H, "key.categories.examplemod");
    private static boolean wasNibbling;
    private static int tickCounter;

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(MOUNT_KEY);
        event.register(CLIMB_TOGGLE_KEY);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            wasNibbling = false;
            return;
        }

        if (DevourRules.isCaptured(player)) {
            wasNibbling = false;
            while (MOUNT_KEY.consumeClick()) { }
            while (CLIMB_TOGGLE_KEY.consumeClick()) { }
            return;
        }

        if (MOUNT_KEY.consumeClick() && minecraft.screen == null && PixelScaleHelper.isTiny(player)) {
            int targetId = -1;
            if (!player.isPassenger() && minecraft.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit
                    && hit.getEntity().getType().is(ModTags.RIDEABLE_LIVESTOCK)) {
                targetId = hit.getEntity().getId();
            }
            PacketDistributor.sendToServer(new SurvivalPayload(
                    SurvivalPayload.Action.TOGGLE_MOUNT, targetId, BlockPos.ZERO));
        }

        if (CLIMB_TOGGLE_KEY.consumeClick() && minecraft.screen == null && PixelScaleHelper.isTiny(player)) {
            boolean enabled = !SurvivalRules.isClimbingEnabled(player);
            SurvivalRules.setClimbingEnabled(player.getUUID(), enabled);
            PacketDistributor.sendToServer(new SurvivalPayload(
                    SurvivalPayload.Action.SET_CLIMBING, enabled ? 1 : 0, BlockPos.ZERO));
        }

        tickCounter++;
        boolean nibbling = isNibbleInputActive(minecraft, player);
        if (nibbling) {
            BlockPos pos = ((BlockHitResult) minecraft.hitResult).getBlockPos();
            if (!wasNibbling || tickCounter % 5 == 0) {
                PacketDistributor.sendToServer(new SurvivalPayload(
                        SurvivalPayload.Action.UPDATE_NIBBLE, -1, pos));
            }
        } else if (wasNibbling) {
            PacketDistributor.sendToServer(new SurvivalPayload(
                    SurvivalPayload.Action.STOP_NIBBLE, -1, BlockPos.ZERO));
        }
        wasNibbling = nibbling;
    }

    private static boolean isNibbleInputActive(Minecraft minecraft, LocalPlayer player) {
        if (minecraft.screen != null || !PixelScaleHelper.isTiny(player)
                || !player.isShiftKeyDown() || !minecraft.options.keyUse.isDown()
                || player.getFoodData().getFoodLevel() >= 20
                || !(minecraft.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        BlockPos pos = hit.getBlockPos();
        return player.level().getBlockState(pos).is(net.minecraft.tags.BlockTags.LEAVES)
                && player.distanceToSqr(pos.getCenter()) <= 4.5 * 4.5;
    }

    private ClientInputEvents() {
    }
}
