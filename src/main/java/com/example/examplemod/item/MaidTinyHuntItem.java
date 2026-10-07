package com.example.examplemod.item;

import com.example.examplemod.scale.PixelScaleHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class MaidTinyHuntItem extends Item {
    public static final int USE_COOLDOWN_TICKS = 10;

    public MaidTinyHuntItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (player.isSpectator()) {
            return InteractionResultHolder.pass(stack);
        }
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }

        PixelScaleHelper.ToggleResult result = PixelScaleHelper.togglePlayer(player);
        if (result == PixelScaleHelper.ToggleResult.APPLIED_MINI || result == PixelScaleHelper.ToggleResult.RESTORED) {
            player.getCooldowns().addCooldown(this, USE_COOLDOWN_TICKS);
            player.displayClientMessage(Component.translatable(result == PixelScaleHelper.ToggleResult.APPLIED_MINI
                    ? "message.examplemod.maid_tiny_hunt.shrunk"
                    : "message.examplemod.maid_tiny_hunt.restored"), true);
            return InteractionResultHolder.success(stack);
        }
        if (result == PixelScaleHelper.ToggleResult.RESTORE_BLOCKED) {
            player.displayClientMessage(Component.translatable("message.examplemod.maid_tiny_hunt.restore_blocked"), true);
            return InteractionResultHolder.fail(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.examplemod.maid_tiny_hunt.desc"));
        tooltipComponents.add(Component.translatable("item.examplemod.maid_tiny_hunt.maid"));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
