package com.example.examplemod.retaliate;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;

public class FriendlyRetaliateGoal extends Goal {
    private final Mob mob;
    private LivingEntity target;
    private int ticksRemaining;
    private int attackCooldown;
    private static final double MAX_DISTANCE_SQR = 24.0 * 24.0;

    public FriendlyRetaliateGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    public static boolean isValidTarget(Mob mob, LivingEntity target) {
        if (target == null || !target.isAlive() || target.isRemoved() || target == mob) {
            return false;
        }
        if (mob.isAlliedTo(target)) {
            return false;
        }
        if (mob instanceof TamableAnimal tamable && tamable.isTame() && tamable.getOwner() == target) {
            return false;
        }
        if (mob instanceof OwnableEntity ownable && ownable.getOwner() == target) {
            return false;
        }
        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        return true;
    }

    public void setRetaliateTarget(LivingEntity newTarget) {
        if (isValidTarget(this.mob, newTarget)) {
            this.target = newTarget;
            this.ticksRemaining = com.example.examplemod.scale.PixelScaleHelper.isTiny(this.mob) ? 800 : 200;
        }
    }

    public LivingEntity getRetaliateTarget() {
        return this.target;
    }

    public int getTicksRemaining() {
        return this.ticksRemaining;
    }

    @Override
    public boolean canUse() {
        return this.target != null
                && this.ticksRemaining > 0
                && isValidTarget(this.mob, this.target)
                && this.mob.distanceToSqr(this.target) <= MAX_DISTANCE_SQR;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        if (this.target != null) {
            this.mob.getNavigation().moveTo(this.target, 1.25D);
        }
    }

    @Override
    public void stop() {
        if (this.ticksRemaining <= 0 || !isValidTarget(this.mob, this.target)) {
            this.target = null;
        }
        this.mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private boolean canApproachDirectly() {
        double dx = this.target.getX() - this.mob.getX();
        double dz = this.target.getZ() - this.mob.getZ();
        // Height changes and airborne routes remain the navigation system's responsibility.
        if (!this.mob.onGround() || Math.abs(this.target.getY() - this.mob.getY()) > 1.0 / 16.0) {
            return false;
        }
        AABB body = this.mob.getBoundingBox().deflate(1e-5D);
        AABB corridor = body.minmax(body.move(dx, 0.0D, dz));
        if (!this.mob.level().noBlockCollision(this.mob, corridor)) {
            return false;
        }
        int samples = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dz * dz) / 0.125D));
        for (int i = 0; i <= samples; i++) {
            double fraction = (double) i / samples;
            AABB support = body.move(dx * fraction, -0.0625D, dz * fraction);
            if (this.mob.level().noBlockCollision(this.mob, support)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void tick() {
        if (this.target == null) {
            return;
        }
        this.ticksRemaining--;
        if (this.attackCooldown > 0) {
            this.attackCooldown--;
        }

        this.mob.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

        double distSqr = this.mob.distanceToSqr(this.target);
        double reach = Math.max(1.0, this.mob.getBbWidth() + this.target.getBbWidth() + 0.5);
        boolean inReach = distSqr <= (reach * reach) || this.mob.getBoundingBox().inflate(0.3).intersects(this.target.getBoundingBox());
        boolean hasLineOfSight = this.mob.getSensing().hasLineOfSight(this.target);

        if (!inReach) {
            if (this.mob.getNavigation().isDone()) {
                // Clear the completed path so an identical rebuilt path starts at node zero.
                this.mob.getNavigation().stop();
                this.mob.getNavigation().moveTo(this.mob.getNavigation().createPath(this.target, 0), 1.25D);
                // Block-based paths can stop short; only finish directly on clear, supported ground.
                if (distSqr <= 9.0D && hasLineOfSight && canApproachDirectly()) {
                    this.mob.getNavigation().stop();
                    this.mob.getMoveControl().setWantedPosition(this.target.getX(), this.target.getY(), this.target.getZ(), 1.25D);
                }
            } else {
                this.mob.getNavigation().moveTo(this.target, 1.25D);
            }
        } else {
            this.mob.getNavigation().stop();
        }

        if (inReach && this.attackCooldown <= 0 && hasLineOfSight) {
            this.attackCooldown = 20;
            this.mob.swing(InteractionHand.MAIN_HAND);

            float baseDamage = 2.0F;
            if (com.example.examplemod.scale.PixelScaleHelper.hasAttribute(this.mob, Attributes.ATTACK_DAMAGE)) {
                baseDamage = (float) this.mob.getAttributeValue(Attributes.ATTACK_DAMAGE);
            }

            DamageSource damageSource = this.mob.damageSources().mobAttack(this.mob);
            this.target.hurt(damageSource, baseDamage);
        }
    }
}
