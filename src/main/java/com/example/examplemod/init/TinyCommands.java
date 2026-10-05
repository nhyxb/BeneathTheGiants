package com.example.examplemod.init;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.scale.TinyMotionTuning;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = ExampleMod.MODID)
public final class TinyCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tiny")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("speed")
                        .then(Commands.argument("scale", DoubleArgumentType.doubleArg(0.01, 4.0))
                                .executes(ctx -> setDouble(ctx.getSource(), "speedScale", TinyMotionTuning.speedScale = DoubleArgumentType.getDouble(ctx, "scale")))))
                .then(Commands.literal("jump")
                        .then(Commands.argument("scale", DoubleArgumentType.doubleArg(0.01, 4.0))
                                .executes(ctx -> setDouble(ctx.getSource(), "jumpScale", TinyMotionTuning.jumpScale = DoubleArgumentType.getDouble(ctx, "scale")))))
                .then(Commands.literal("gravity")
                        .then(Commands.argument("scale", DoubleArgumentType.doubleArg(0.01, 4.0))
                                .executes(ctx -> setDouble(ctx.getSource(), "gravityScale", TinyMotionTuning.gravityScale = DoubleArgumentType.getDouble(ctx, "scale")))))
                .then(Commands.literal("airdrag")
                        .then(Commands.argument("drag", DoubleArgumentType.doubleArg(0.05, 1.0))
                                .executes(ctx -> setDouble(ctx.getSource(), "airDrag", TinyMotionTuning.airDrag = (float) DoubleArgumentType.getDouble(ctx, "drag")))))
                .then(Commands.literal("rainoxygen")
                        .then(Commands.argument("perTick", IntegerArgumentType.integer(0, 20))
                                .executes(ctx -> {
                                    TinyMotionTuning.rainOxygenPerTick = IntegerArgumentType.getInteger(ctx, "perTick");
                                    return feedback(ctx.getSource(), "rainOxygenPerTick", TinyMotionTuning.rainOxygenPerTick);
                                })))
                .then(Commands.literal("groundaccel")
                        .then(Commands.argument("ratio", DoubleArgumentType.doubleArg(0.01, 1.0))
                                .executes(ctx -> setDouble(ctx.getSource(), "groundAccelRatio", TinyMotionTuning.groundAccelRatio = DoubleArgumentType.getDouble(ctx, "ratio")))))
                .then(Commands.literal("show").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                            "tiny speedScale=%.2f jumpScale=%.2f gravityScale=%.2f airDrag=%.2f groundAccelRatio=%.2f rainOxygenPerTick=%d",
                            TinyMotionTuning.speedScale, TinyMotionTuning.jumpScale, TinyMotionTuning.gravityScale,
                            TinyMotionTuning.airDrag, TinyMotionTuning.groundAccelRatio, TinyMotionTuning.rainOxygenPerTick)), false);
                    return 1;
                }))
                .then(Commands.literal("reset").executes(ctx -> {
                    TinyMotionTuning.speedScale = 0.25D;
                    TinyMotionTuning.jumpScale = 0.25D;
                    TinyMotionTuning.gravityScale = 0.25D;
                    TinyMotionTuning.airDrag = 0.85F;
                    TinyMotionTuning.groundAccelRatio = 0.1D;
                    TinyMotionTuning.rainOxygenPerTick = 1;
                    return feedback(ctx.getSource(), "reset to defaults", 0);
                })));
    }

    private static int setDouble(net.minecraft.commands.CommandSourceStack source, String name, double value) {
        return feedback(source, name, value);
    }

    private static int feedback(net.minecraft.commands.CommandSourceStack source, String name, Object value) {
        source.sendSuccess(() -> Component.literal("tiny " + name + " = " + value), true);
        return 1;
    }

    private TinyCommands() {
    }
}
