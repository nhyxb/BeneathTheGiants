package com.example.examplemod.devour;

import com.example.examplemod.ExampleMod;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = ExampleMod.MODID)
public final class DevourEvents {
    @SubscribeEvent
    public static void onDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()
                || event.getNewDamage() <= 0) return;
        var source = event.getSource();
        if (!(source.getEntity() instanceof Mob captor) || source.getDirectEntity() != captor
                || !(source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO))) return;
        DevourRules.attemptCapture(captor, player, player.getRandom().nextDouble());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Player player && DevourRules.shouldBlockDamage(player, event.getSource())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKnockback(LivingKnockBackEvent event) {
        if (event.getEntity() instanceof Player player && DevourRules.isCaptured(player)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) { DevourRules.tick(event.getEntity()); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMount(EntityMountEvent event) {
        if (event.getEntityMounting() instanceof Player player
                && !DevourRules.allowMount(player, event.getEntityBeingMounted(), event.isMounting())) {
            event.setCanceled(true);
        }
    }

    private static void blockInteraction(PlayerInteractEvent event) {
        if (DevourRules.isCaptured(event.getEntity()) && event instanceof ICancellableEvent cancellable) {
            cancellable.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockUse(PlayerInteractEvent.RightClickBlock event) { blockInteraction(event); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemUse(PlayerInteractEvent.RightClickItem event) { blockInteraction(event); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityUse(PlayerInteractEvent.EntityInteract event) { blockInteraction(event); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSpecificEntityUse(PlayerInteractEvent.EntityInteractSpecific event) { blockInteraction(event); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockAttack(PlayerInteractEvent.LeftClickBlock event) { blockInteraction(event); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(AttackEntityEvent event) {
        if (DevourRules.isCaptured(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (DevourRules.isCaptured(event.getPlayer())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUse(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof Player player && DevourRules.isCaptured(player)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) { DevourRules.releaseFor(event.getEntity()); }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) { DevourRules.releaseForLeave(event.getEntity()); }

    @SubscribeEvent
    public static void onTeleport(EntityTeleportEvent event) { DevourRules.releaseFor(event.getEntity()); }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) { DevourRules.release(event.getEntity()); }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) { DevourRules.release(event.getEntity()); }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) { DevourRules.release(event.getOriginal()); }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) { DevourRules.clearAll(); }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) { DevourRules.clearAll(); }

    private DevourEvents() { }
}
