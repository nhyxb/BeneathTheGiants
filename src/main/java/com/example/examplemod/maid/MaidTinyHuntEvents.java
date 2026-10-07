package com.example.examplemod.maid;

import com.example.examplemod.ExampleMod;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = ExampleMod.MODID)
public final class MaidTinyHuntEvents {
    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity living) {
            MaidTinyHunt.tickDevour(living, living.level().getGameTime());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof LivingEntity living && MaidTinyHunt.blocksCaptorDamage(living, event.getSource())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        if (!event.isMounting() && event.getEntityMounting() instanceof LivingEntity living
                && MaidTinyHunt.shouldKeepMounted(living)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        MaidTinyHunt.release(event.getEntity());
    }

    private MaidTinyHuntEvents() {
    }
}
