package com.example.examplemod.init;

import com.example.examplemod.ExampleMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class ModDamageTypes {
    public static final ResourceKey<DamageType> STOMP = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "stomp")
    );
    public static final ResourceKey<DamageType> DEVOURED = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "devoured")
    );

    public static final ResourceKey<DamageType> AXOLOTL_BITE = ResourceKey.create(
            Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "axolotl_bite"));

    public static DamageSource getAxolotlBiteSource(Level level, LivingEntity attacker) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(AXOLOTL_BITE), attacker, attacker);
    }

    public static DamageSource getStompSource(Level level, LivingEntity stomper) {
        Holder<DamageType> holder = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(STOMP);
        return new DamageSource(holder, stomper, stomper);
    }

    public static DamageSource getDevouredSource(Level level, LivingEntity attacker) {
        Holder<DamageType> holder = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DEVOURED);
        return new DamageSource(holder, attacker, attacker);
    }
}
