package com.example.examplemod.network;

import com.example.examplemod.ExampleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.function.Consumer;

public record GiantImpactPayload(float intensity, float amplitude, int duration) implements CustomPacketPayload {
    public static final Type<GiantImpactPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "giant_impact"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GiantImpactPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeFloat(payload.intensity());
                buffer.writeFloat(payload.amplitude());
                buffer.writeVarInt(payload.duration());
            },
            buffer -> new GiantImpactPayload(buffer.readFloat(), buffer.readFloat(), buffer.readVarInt())
    );

    private static volatile Consumer<GiantImpactPayload> clientHandler = null;

    public static void setClientHandler(Consumer<GiantImpactPayload> handler) {
        clientHandler = handler;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (clientHandler != null) {
                        clientHandler.accept(payload);
                    }
                }));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
