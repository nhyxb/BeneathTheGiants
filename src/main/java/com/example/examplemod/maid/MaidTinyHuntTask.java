package com.example.examplemod.maid;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.init.ModItems;
import com.github.tartaricacid.touhoulittlemaid.api.task.IAttackTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.behavior.StartAttacking;
import net.minecraft.world.entity.ai.behavior.StopAttackingIfTargetInvalid;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** Loaded only when Touhou Little Maid scans the extension. */
public final class MaidTinyHuntTask implements IAttackTask {
    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "tiny_hunt");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return ModItems.MAID_TINY_HUNT.get().getDefaultInstance();
    }

    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return null;
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        return Lists.newArrayList(
                Pair.of(5, StartAttacking.create(this::holdsFang, this::findTarget)),
                Pair.of(5, StopAttackingIfTargetInvalid.create(target -> !holdsFang(maid) || !canAttack(maid, target) || farAway(target, maid) || ownerRetargeted(maid, target))),
                Pair.of(5, ramTarget())
        );
    }

    @Override
    public boolean enableLookAndRandomWalk(EntityMaid maid) {
        return false;
    }

    @Override
    public boolean isEnable(EntityMaid maid) {
        return holdsFang(maid);
    }

    @Override
    public boolean isWeapon(EntityMaid maid, ItemStack stack) {
        return stack.is(ModItems.MAID_TINY_HUNT.get());
    }

    @Override
    public boolean canAttack(EntityMaid maid, LivingEntity target) {
        if (!MaidTinyHunt.isHuntTarget(maid, maid.getOwner(), target) || MaidTinyHunt.isDevouring(target)) {
            return false;
        }
        if (target instanceof Player || MaidTinyHunt.isRecentOwnerTarget(maid.getOwner(), target)) {
            return true;
        }
        return IAttackTask.super.canAttack(maid, target);
    }

    @Override
    public List<Pair<String, Predicate<EntityMaid>>> getEnableConditionDesc(EntityMaid maid) {
        return Lists.newArrayList(Pair.of("holding_fang", this::holdsFang));
    }

    @Override
    public String getMaidActionSummary() {
        return "Hold the tiny-hunt fang and hunt hostile mobs plus the owner's current non-player target. Other players are attacked only after they hurt the maid or her owner. The first ram shrinks, later rams stomp, and a half-health target may be eaten. The owner, creative players, and spectators are not targets.";
    }

    private boolean holdsFang(EntityMaid maid) {
        return maid.getMainHandItem().is(ModItems.MAID_TINY_HUNT.get());
    }

    private Optional<? extends LivingEntity> findTarget(EntityMaid maid) {
        LivingEntity owner = maid.getOwner();
        LivingEntity marked = owner == null ? null : owner.getLastHurtMob();
        if (isVisibleTarget(maid, marked)) {
            return Optional.of(marked);
        }
        if (owner != null && isVisibleTarget(maid, owner.getLastHurtByMob())) {
            return Optional.of(owner.getLastHurtByMob());
        }
        if (isVisibleTarget(maid, maid.getLastHurtByMob())) {
            return Optional.of(maid.getLastHurtByMob());
        }
        return IAttackTask.findFirstValidAttackTarget(maid);
    }

    private boolean isVisibleTarget(EntityMaid maid, LivingEntity target) {
        return target != null && canAttack(maid, target) && maid.isWithinRestriction(target.blockPosition())
                && maid.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
                .map(visible -> visible.contains(target)).orElse(false);
    }

    private boolean ownerRetargeted(EntityMaid maid, LivingEntity current) {
        LivingEntity owner = maid.getOwner();
        LivingEntity marked = owner == null ? null : owner.getLastHurtMob();
        return marked != null && marked != current && canAttack(maid, marked);
    }

    private boolean farAway(LivingEntity target, EntityMaid maid) {
        if (!target.isAlive()) {
            return true;
        }
        float radius = maid.getRestrictRadius();
        if (!maid.isHomeModeEnable() && maid.getOwner() != null) {
            return maid.getOwner().distanceTo(target) > radius;
        }
        return maid.distanceTo(target) > radius;
    }

    private static OneShot<EntityMaid> ramTarget() {
        return BehaviorBuilder.create(context -> context.group(
                context.registered(MemoryModuleType.LOOK_TARGET),
                context.registered(MemoryModuleType.WALK_TARGET),
                context.present(MemoryModuleType.ATTACK_TARGET),
                context.absent(MemoryModuleType.ATTACK_COOLING_DOWN),
                context.present(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
        ).apply(context, (lookTarget, walkTarget, attackTarget, attackCoolingDown, visible) -> (level, maid, gameTime) -> {
            LivingEntity target = context.get(attackTarget);
            if (!maid.getMainHandItem().is(ModItems.MAID_TINY_HUNT.get()) || !context.get(visible).contains(target)) {
                return false;
            }
            lookTarget.set(new EntityTracker(target, false));
            if (!MaidTinyHunt.isRamContact(maid, target)) {
                walkTarget.set(new WalkTarget(new EntityTracker(target, false), 1.0F, 0));
                return true;
            }
            MaidTinyHunt.Result result = MaidTinyHunt.strike(maid, target, maid.getRandom().nextDouble());
            if (result == MaidTinyHunt.Result.DEVOURING) {
                maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            }
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            attackCoolingDown.setWithExpiry(true, 20L);
            return result != MaidTinyHunt.Result.IGNORED;
        }));
    }
}
