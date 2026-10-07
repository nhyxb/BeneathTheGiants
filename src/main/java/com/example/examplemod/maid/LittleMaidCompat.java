package com.example.examplemod.maid;

import com.example.examplemod.init.ModItems;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager;

@LittleMaidExtension
public final class LittleMaidCompat implements ILittleMaid {
    @Override
    public void bindMaidBauble(BaubleManager manager) {
        manager.bind(ModItems.MAID_HEAD_SEAT.get(), new MaidHeadSeatBauble());
    }

    @Override
    public void addMaidTask(TaskManager manager) {
        manager.add(new MaidTinyHuntTask());
    }
}
