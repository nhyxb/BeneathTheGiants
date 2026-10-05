package com.example.examplemod.torch;

import com.example.examplemod.ExampleMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = ExampleMod.MODID)
public final class TorchContactEvents {
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) { TorchContactRules.tick(event.getEntity()); }
    private TorchContactEvents() { }
}
