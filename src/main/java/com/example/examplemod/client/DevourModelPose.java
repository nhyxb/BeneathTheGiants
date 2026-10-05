package com.example.examplemod.client;

import com.example.examplemod.devour.DevourPlayerAccess;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

public final class DevourModelPose {
    private DevourModelPose() {}

    public static void resetTransientPose(EntityModel<?> model) {
        if (model instanceof VillagerModel<?> villagerModel) {
            ModelPart root = villagerModel.root();
            if (root != null && root.hasChild("arms")) {
                root.getChild("arms").resetPose();
            }
        }
    }

    public static <T extends LivingEntity> void apply(T entity, EntityModel<T> model, float partialTicks) {
        if (!(entity instanceof Mob mob)) {
            return;
        }

        Player victim = null;
        float progress = 0.0F;
        for (Entity passenger : mob.getPassengers()) {
            if (passenger instanceof Player player && player instanceof DevourPlayerAccess access) {
                if (access.examplemod$getCaptorId() == mob.getId()) {
                    victim = player;
                    progress = access.examplemod$getDevourProgress();
                    break;
                }
            }
        }

        if (victim == null) {
            return;
        }

        float p = Mth.clamp(progress, 0.0F, 1.0F);
        boolean isRightMain = (mob.getMainArm() == HumanoidArm.RIGHT);

        if (model instanceof HumanoidModel<?> humanoidModel) {
            ModelPart mainArm = isRightMain ? humanoidModel.rightArm : humanoidModel.leftArm;
            ModelPart offArm = isRightMain ? humanoidModel.leftArm : humanoidModel.rightArm;

            float mainSign = isRightMain ? -1.0F : 1.0F;
            float offSign = -mainSign;

            // 主手抬起至嘴前：从手前伸出(-1.0F)平滑抬起至嘴前(-1.45F)，向身体中线内收
            mainArm.xRot = Mth.lerp(p, -1.0F, -1.45F);
            mainArm.yRot = mainSign * Mth.lerp(p, 0.15F, 0.45F);
            mainArm.zRot = mainSign * Mth.lerp(p, 0.0F, 0.1F);

            // 副手微抬辅助托举
            offArm.xRot = Mth.lerp(p, -0.6F, -0.9F);
            offArm.yRot = offSign * Mth.lerp(p, 0.1F, 0.3F);
            offArm.zRot = offSign * Mth.lerp(p, 0.0F, 0.1F);

            if (humanoidModel instanceof PlayerModel<?> playerModel) {
                playerModel.rightSleeve.copyFrom(playerModel.rightArm);
                playerModel.leftSleeve.copyFrom(playerModel.leftArm);
            }
        } else if (model instanceof IllagerModel<?> illagerModel) {
            ModelPart root = illagerModel.root();
            if (root.hasChild("arms") && root.hasChild("right_arm") && root.hasChild("left_arm")) {
                ModelPart arms = root.getChild("arms");
                ModelPart rightArm = root.getChild("right_arm");
                ModelPart leftArm = root.getChild("left_arm");

                arms.visible = false;
                rightArm.visible = true;
                leftArm.visible = true;

                ModelPart mainArm = isRightMain ? rightArm : leftArm;
                ModelPart offArm = isRightMain ? leftArm : rightArm;

                float mainSign = isRightMain ? -1.0F : 1.0F;
                float offSign = -mainSign;

                mainArm.xRot = Mth.lerp(p, -1.0F, -1.45F);
                mainArm.yRot = mainSign * Mth.lerp(p, 0.15F, 0.45F);
                mainArm.zRot = mainSign * Mth.lerp(p, 0.0F, 0.1F);

                offArm.xRot = Mth.lerp(p, -0.6F, -0.9F);
                offArm.yRot = offSign * Mth.lerp(p, 0.1F, 0.3F);
                offArm.zRot = offSign * Mth.lerp(p, 0.0F, 0.1F);
            }
        } else if (model instanceof VillagerModel<?> villagerModel) {
            ModelPart root = villagerModel.root();
            if (root != null && root.hasChild("arms")) {
                ModelPart arms = root.getChild("arms");
                float mainSign = isRightMain ? -1.0F : 1.0F;

                arms.xRot = Mth.lerp(p, -1.0F, -1.45F);
                arms.yRot = mainSign * Mth.lerp(p, 0.1F, 0.0F);
                arms.zRot = 0.0F;
            }
        }
    }
}
