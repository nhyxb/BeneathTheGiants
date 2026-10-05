package com.example.examplemod.item;

import com.example.examplemod.scale.PixelScaleHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class ScaleWandItem extends Item {
    public ScaleWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }

        // Shared cooldown tracker on player to prevent double triggering across hands/stacks
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResult.FAIL;
        }

        PixelScaleHelper.ToggleResult result = PixelScaleHelper.toggleLivingTarget(player, target);
        switch (result) {
            case APPLIED_MINI:
            case RESTORED:
                player.getCooldowns().addCooldown(this, 10);
                return InteractionResult.SUCCESS;
            case RESTORE_BLOCKED:
                player.displayClientMessage(Component.translatable("message.examplemod.scale_wand.restore_blocked"), true);
                return InteractionResult.FAIL;
            case REJECTED_PLAYER:
                player.displayClientMessage(Component.translatable("message.examplemod.scale_wand.reject_player"), true);
                return InteractionResult.FAIL;
            case COOLDOWN_ACTIVE:
                return InteractionResult.FAIL;
            default:
                return InteractionResult.PASS;
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.examplemod.scale_wand.desc"));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
