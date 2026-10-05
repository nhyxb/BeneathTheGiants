package com.example.examplemod.hiding;

import com.example.examplemod.ExampleMod;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent.LivingVisibilityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = ExampleMod.MODID)
public final class HidingEvents {
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) { HidingRules.tick(event.getEntity()); }

    @SubscribeEvent
    public static void onVisibility(LivingVisibilityEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide()) {
            event.modifyVisibility(HidingRules.visibilityMultiplier(player, event.getLookingEntity()));
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player) HidingRules.clear(player);
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide()) HidingRules.clear(player);
    }

    @SubscribeEvent
    public static void onTeleport(EntityTeleportEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide()) HidingRules.clear(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) { HidingRules.clear(event.getEntity()); }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) { HidingRules.clear(event.getEntity()); }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) { HidingRules.clear(event.getOriginal()); }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) { HidingRules.clearAll(); }
    private HidingEvents() { }
}
