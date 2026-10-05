package com.example.examplemod.init;

import com.example.examplemod.ExampleMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

public final class ModTags {
    public static final TagKey<EntityType<?>> PREDATORY_INSECTS = TagKey.create(
            Registries.ENTITY_TYPE, id("predatory_insects"));
    public static final TagKey<EntityType<?>> WEB_ATTRACTED_SPIDERS = TagKey.create(
            Registries.ENTITY_TYPE, id("web_attracted_spiders"));
    public static final TagKey<EntityType<?>> RIDEABLE_LIVESTOCK = TagKey.create(
            Registries.ENTITY_TYPE, id("rideable_livestock"));
    public static final TagKey<EntityType<?>> HUMANOID_DEVOURERS = TagKey.create(
            Registries.ENTITY_TYPE, id("humanoid_devourers"));
    public static final TagKey<Block> SLOW_SURFACES = TagKey.create(
            Registries.BLOCK, id("slow_surfaces"));
    public static final TagKey<Block> TINY_TORCHES = TagKey.create(
            Registries.BLOCK, id("tiny_torches"));

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, path);
    }

    private ModTags() {
    }
}
