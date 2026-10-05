package com.example.examplemod.predator;

import com.example.examplemod.init.ModDamageTypes;
import com.example.examplemod.scale.PixelScaleHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * Hunts tiny players for predators whose vanilla attack goals cannot target players
 * (chicken, cat, ocelot, bat, parrot, frog, axolotl). Damage uses the devoured type,
 * so the kill feed reads "被吃掉了" via the existing death message.
 */
public class PredatorHuntGoal extends Goal {
    private final Mob mob;
    private int attackCooldown;

    public PredatorHuntGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    private boolean isHuntableTarget(LivingEntity target) {
        if (!(target instanceof Player player) || !player.isAlive()
                || player.isCreative() || player.isSpectator()
                || !PixelScaleHelper.isTiny(player)
                || this.mob.isAlliedTo(player)) {
            return false;
        }
        return !(this.mob instanceof TamableAnimal tamable && tamable.isTame());
    }

    @Override
    public boolean canUse() {
        return isHuntableTarget(this.mob.getTarget());
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        LivingEntity target = this.mob.getTarget();
        if (target != null) {
            this.mob.getNavigation().moveTo(target, 1.25D);
        }
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (!isHuntableTarget(target)) {
            return;
        }
        if (this.attackCooldown > 0) {
            this.attackCooldown--;
        }
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        double distSqr = this.mob.distanceToSqr(target);
        double reach = Math.max(1.0, this.mob.getBbWidth() + target.getBbWidth() + 0.5);
        boolean inReach = distSqr <= reach * reach
                || this.mob.getBoundingBox().inflate(0.3).intersects(target.getBoundingBox());

        if (!inReach) {
            if (this.mob.getNavigation().isDone()) {
                this.mob.getNavigation().moveTo(target, 1.25D);
            }
        } else {
            this.mob.getNavigation().stop();
        }

        if (inReach && this.attackCooldown <= 0 && this.mob.getSensing().hasLineOfSight(target)) {
            this.attackCooldown = 20;
            this.mob.swing(InteractionHand.MAIN_HAND);
            float damage = 2.0F;
            if (PixelScaleHelper.hasAttribute(this.mob, Attributes.ATTACK_DAMAGE)) {
                damage = (float) this.mob.getAttributeValue(Attributes.ATTACK_DAMAGE);
            }
            target.hurt(ModDamageTypes.getDevouredSource(this.mob.level(), this.mob), damage);
        }
    }
}
