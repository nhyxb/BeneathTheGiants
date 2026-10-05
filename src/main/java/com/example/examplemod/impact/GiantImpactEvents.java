package com.example.examplemod.impact;

import com.example.examplemod.ExampleMod;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent.LivingJumpEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = ExampleMod.MODID)
public final class GiantImpactEvents {

    @SubscribeEvent
    public static void onTeleport(EntityTeleportEvent event) {
        if (event.getEntity() instanceof LivingEntity living && !living.level().isClientSide()) {
            GiantImpactRules.invalidateMotion(living);
        }
    }

    @SubscribeEvent
    public static void onLivingJump(LivingJumpEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity != null && entity.level() != null && !entity.level().isClientSide()) {
            GiantImpactRules.onJump(entity);
        }
    }

    @SubscribeEvent
    public static void onEntityTickPre(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof LivingEntity living && !living.level().isClientSide()) {
            GiantImpactRules.recordPreTick(living);
        }
    }

    @SubscribeEvent
    public static void onEntityTickPost(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity living && !living.level().isClientSide()) {
            GiantImpactRules.postTick(living);
        }
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity living) {
            GiantImpactRules.clear(living);
        } else if (event.getEntity() != null) {
            GiantImpactRules.clear(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        GiantImpactRules.clearAll();
    }

    private GiantImpactEvents() {
    }
}
