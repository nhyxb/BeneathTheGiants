package com.example.examplemod;

import com.example.examplemod.client.ClientInputEvents;
import com.example.examplemod.client.DevourClientEvents;
import com.example.examplemod.client.GiantImpactCamera;
import com.example.examplemod.client.MaidRideCamera;
import com.example.examplemod.network.GiantImpactPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = ExampleMod.MODID, dist = Dist.CLIENT)
public class ExampleModClient {
    public ExampleModClient(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(ClientInputEvents::registerKeyMappings);
        NeoForge.EVENT_BUS.addListener(ClientInputEvents::onClientTick);

        GiantImpactPayload.setClientHandler(GiantImpactCamera::handlePayload);
        NeoForge.EVENT_BUS.addListener(GiantImpactCamera::onComputeCameraAngles);
        NeoForge.EVENT_BUS.addListener(GiantImpactCamera::onClientTick);
        NeoForge.EVENT_BUS.addListener(GiantImpactCamera::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(GiantImpactCamera::onClone);
        NeoForge.EVENT_BUS.addListener(MaidRideCamera::onComputeCameraAngles);
        NeoForge.EVENT_BUS.addListener(MaidRideCamera::onClientTick);
        NeoForge.EVENT_BUS.addListener(MaidRideCamera::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(MaidRideCamera::onClone);
        NeoForge.EVENT_BUS.addListener(DevourClientEvents::onMovementInputUpdate);
    }
}
