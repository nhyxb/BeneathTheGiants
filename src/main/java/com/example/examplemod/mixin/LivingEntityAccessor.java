package com.example.examplemod.mixin;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Invoker("getDefaultDimensions")
    EntityDimensions examplemod$callGetDefaultDimensions(Pose pose);

    @Accessor("lastHurt")
    float examplemod$getLastHurt();

    @Accessor("lastHurt")
    void examplemod$setLastHurt(float value);
}
