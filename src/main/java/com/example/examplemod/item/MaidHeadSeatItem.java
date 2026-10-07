package com.example.examplemod.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class MaidHeadSeatItem extends Item {
    public MaidHeadSeatItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.examplemod.maid_head_seat.desc"));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
