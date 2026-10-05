package com.example.examplemod.init;

import com.example.examplemod.ExampleMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, ExampleMod.MODID);

    public static final Holder<Attribute> PIXEL_SCALE = ATTRIBUTES.register(
            "pixel_scale",
            () -> new RangedAttribute("attribute.name.examplemod.pixel_scale", 1.0D, 0.01D, 1.0D).setSyncable(true)
    );

    public static void onEntityAttributeModification(EntityAttributeModificationEvent event) {
        for (EntityType<? extends LivingEntity> type : event.getTypes()) {
            if (!event.has(type, PIXEL_SCALE)) {
                event.add(type, PIXEL_SCALE, 1.0D);
            }
        }
    }
}
