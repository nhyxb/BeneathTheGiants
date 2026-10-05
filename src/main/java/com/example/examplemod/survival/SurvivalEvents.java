package com.example.examplemod.survival;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.scale.PixelScaleHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.BlockTags;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = ExampleMod.MODID)
public final class SurvivalEvents {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!PixelScaleHelper.isTiny(player)) {
            return;
        }

        var state = player.level().getBlockState(event.getPos());
        if (state.is(BlockTags.LEAVES) && player.isShiftKeyDown() && SurvivalRules.canEat(player)) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
            return;
        }

        if (state.is(BlockTags.FLOWERS)) {
            if (!player.level().isClientSide) {
                if (SurvivalRules.giveNectar(player)) {
                    player.level().destroyBlock(event.getPos(), false, player);
                }
            }
            event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide));
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player
                && PixelScaleHelper.isTiny(player)) {
            SurvivalRules.tickPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        SurvivalRules.clearPlayer(event.getEntity().getUUID());
    }

    private SurvivalEvents() {
    }
}
