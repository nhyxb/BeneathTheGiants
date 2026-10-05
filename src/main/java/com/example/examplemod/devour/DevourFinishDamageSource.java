package com.example.examplemod.devour;

import com.example.examplemod.init.ModDamageTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/** Bypass policy belongs to this final attack instance, never the shared F01 damage type. */
final class DevourFinishDamageSource extends DamageSource {
    DevourFinishDamageSource(Level level, Mob captor) {
        super(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ModDamageTypes.DEVOURED),
                captor, captor);
    }

    @Override
    public boolean is(TagKey<DamageType> tag) {
        return tag == DamageTypeTags.BYPASSES_ARMOR || tag == DamageTypeTags.BYPASSES_EFFECTS
                || tag == DamageTypeTags.BYPASSES_RESISTANCE || tag == DamageTypeTags.BYPASSES_ENCHANTMENTS
                || tag == DamageTypeTags.BYPASSES_SHIELD || tag == DamageTypeTags.BYPASSES_COOLDOWN
                || tag == DamageTypeTags.BYPASSES_INVULNERABILITY || super.is(tag);
    }
}
