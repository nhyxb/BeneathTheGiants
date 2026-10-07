package com.example.examplemod.maid;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.item.ItemStack;

/** Loaded only when Touhou Little Maid scans this mod's extension. */
public final class MaidHeadSeatBauble implements IMaidBauble {
    @Override
    public void onTakeOff(EntityMaid maid, ItemStack baubleItem) {
        MaidHeadSeat.dismountIfSeatRemoved(maid);
    }

    @Override
    public boolean syncClient(EntityMaid maid, ItemStack baubleItem) {
        return true;
    }
}
