package com.example.examplemod.init;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.item.MaidHeadSeatItem;
import com.example.examplemod.item.MaidTinyHuntItem;
import com.example.examplemod.item.ScaleWandItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ExampleMod.MODID);

    public static final DeferredItem<ScaleWandItem> SCALE_WAND = ITEMS.register(
            "scale_wand",
            () -> new ScaleWandItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<MaidTinyHuntItem> MAID_TINY_HUNT = ITEMS.register(
            "maid_tiny_hunt",
            () -> new MaidTinyHuntItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<MaidHeadSeatItem> MAID_HEAD_SEAT = ITEMS.register(
            "maid_head_seat",
            () -> new MaidHeadSeatItem(new Item.Properties().stacksTo(1))
    );
}
