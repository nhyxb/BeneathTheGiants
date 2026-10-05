package com.example.examplemod.mixin.client;

import com.example.examplemod.vortex.AxolotlVortexAccess;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.model.AxolotlModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LerpingModel;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AxolotlModel.class)
public abstract class AxolotlVortexModelMixin<T extends Axolotl & LerpingModel> {
    @Shadow
    private ModelPart head;

    @Unique
    private ModelPart examplemod$jaw;

    @WrapOperation(
            method = "createBodyLayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/geom/builders/PartDefinition;addOrReplaceChild(Ljava/lang/String;Lnet/minecraft/client/model/geom/builders/CubeListBuilder;Lnet/minecraft/client/model/geom/PartPose;)Lnet/minecraft/client/model/geom/builders/PartDefinition;"
            )
    )
    private static PartDefinition examplemod$splitHeadAndJaw(
            PartDefinition instance,
            String name,
            CubeListBuilder cubes,
            PartPose partPose,
            Operation<PartDefinition> original
    ) {
        if ("head".equals(name)) {
            CubeListBuilder upperHeadCubes = CubeListBuilder.create()
                    .texOffs(0, 1)
                    .addBox(-4.0F, -3.0F, -5.0F, 8.0F, 4.0F, 5.0F, new CubeDeformation(0.001F));
            PartDefinition headPart = original.call(instance, name, upperHeadCubes, partPose);
            headPart.addOrReplaceChild(
                    "examplemod_jaw",
                    CubeListBuilder.create()
                            .texOffs(0, 5)
                            .addBox(-4.0F, 0.0F, -5.0F, 8.0F, 1.0F, 5.0F, new CubeDeformation(0.001F)),
                    PartPose.offset(0.0F, 1.0F, 0.0F)
            );
            return headPart;
        }
        return original.call(instance, name, cubes, partPose);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void examplemod$initJaw(ModelPart root, CallbackInfo ci) {
        if (this.head != null && this.head.hasChild("examplemod_jaw")) {
            this.examplemod$jaw = this.head.getChild("examplemod_jaw");
        } else {
            this.examplemod$jaw = null;
        }
    }

    @Inject(
            method = "setupAnim(Lnet/minecraft/world/entity/animal/axolotl/Axolotl;FFFFF)V",
            at = @At("TAIL")
    )
    private void examplemod$setupJawAnim(
            T entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo ci
    ) {
        if (this.examplemod$jaw == null) {
            return;
        }
        this.examplemod$jaw.resetPose();
        if (entity instanceof AxolotlVortexAccess access) {
            int phase = access.examplemod$getVortexPhase();
            if (phase == 1) {
                this.examplemod$jaw.xRot = 0.25F;
            } else if (phase == 2) {
                float chewProgress = (Mth.sin(ageInTicks * 0.65F) + 1.0F) * 0.5F;
                this.examplemod$jaw.xRot = 0.15F + 0.25F * chewProgress;
            } else {
                this.examplemod$jaw.xRot = 0.0F;
            }
        }
    }
}
