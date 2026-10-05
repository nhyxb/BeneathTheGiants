package com.example.examplemod.network;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.survival.SurvivalRules;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record SurvivalPayload(Action action, int entityId, BlockPos blockPos) implements CustomPacketPayload {
    public static final Type<SurvivalPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "survival_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SurvivalPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.action.ordinal());
                buffer.writeVarInt(payload.entityId);
                buffer.writeBlockPos(payload.blockPos);
            },
            buffer -> new SurvivalPayload(
                    Action.fromId(buffer.readVarInt()),
                    buffer.readVarInt(),
                    buffer.readBlockPos()));

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC,
                (payload, context) -> handle(payload, context));
    }

    private static void handle(SurvivalPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SurvivalRules.handlePayload(context.player(), payload));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action {
        TOGGLE_MOUNT,
        UPDATE_NIBBLE,
        STOP_NIBBLE,
        SET_CLIMBING;

        private static Action fromId(int id) {
            Action[] values = values();
            if (id < 0 || id >= values.length) {
                return STOP_NIBBLE;
            }
            return values[id];
        }
    }
}
