package com.example.examplemod.maid;

import com.example.examplemod.ExampleMod;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = ExampleMod.MODID)
public final class MaidSeatEvents {
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!MaidHeadSeat.isRiddenBy(event.getEntity(), event.getTarget())) {
            return;
        }
        event.setCancellationResult(InteractionResult.FAIL);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (MaidHeadSeat.isRiddenBy(event.getEntity(), event.getTarget())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!MaidHeadSeat.isRiddenBy(event.getEntity(), event.getTarget())) {
            return;
        }
        event.setCancellationResult(InteractionResult.FAIL);
        event.setCanceled(true);
    }

    private MaidSeatEvents() {
    }
}
