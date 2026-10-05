package com.example.examplemod.vortex;

import com.example.examplemod.ExampleMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = ExampleMod.MODID)
public final class AxolotlVortexEvents {
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Axolotl source && !event.getLevel().isClientSide()) {
            AxolotlVortexRules.register(source);
        }
    }

    @SubscribeEvent
    public static void onTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) AxolotlVortexRules.tickLevel(level);
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof Axolotl source) AxolotlVortexRules.unregister(source);
        else AxolotlVortexRules.clearFor(event.getEntity());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) { AxolotlVortexRules.clearFor(event.getEntity()); }

    @SubscribeEvent
    public static void onTeleport(EntityTeleportEvent event) { AxolotlVortexRules.clearFor(event.getEntity()); }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) { AxolotlVortexRules.clearFor(event.getEntity()); }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) { AxolotlVortexRules.clearFor(event.getEntity()); }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) { AxolotlVortexRules.clearAll(); }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) { AxolotlVortexRules.clearAll(); }

    private AxolotlVortexEvents() { }
}
