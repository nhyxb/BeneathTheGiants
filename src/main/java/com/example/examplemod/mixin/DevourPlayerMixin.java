package com.example.examplemod.mixin;

import com.example.examplemod.devour.DevourPlayerAccess;
import com.example.examplemod.devour.DevourRules;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class DevourPlayerMixin implements DevourPlayerAccess {
    @Unique
    private static final EntityDataAccessor<Integer> EXAMPLEMOD$DEVOUR_CAPTOR_ID =
            SynchedEntityData.defineId(Player.class, EntityDataSerializers.INT);

    @Unique
    private static final EntityDataAccessor<Float> EXAMPLEMOD$DEVOUR_PROGRESS =
            SynchedEntityData.defineId(Player.class, EntityDataSerializers.FLOAT);

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void examplemod$defineSynchedData(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(EXAMPLEMOD$DEVOUR_CAPTOR_ID, -1);
        builder.define(EXAMPLEMOD$DEVOUR_PROGRESS, 0.0F);
    }

    @Override
    public int examplemod$getCaptorId() {
        return ((Player) (Object) this).getEntityData().get(EXAMPLEMOD$DEVOUR_CAPTOR_ID);
    }

    @Override
    public float examplemod$getDevourProgress() {
        return ((Player) (Object) this).getEntityData().get(EXAMPLEMOD$DEVOUR_PROGRESS);
    }

    @Override
    public void examplemod$setDevourState(int captorId, float progress) {
        ((Player) (Object) this).getEntityData().set(EXAMPLEMOD$DEVOUR_CAPTOR_ID, captorId);
        ((Player) (Object) this).getEntityData().set(EXAMPLEMOD$DEVOUR_PROGRESS, progress);
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void examplemod$onHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (DevourRules.shouldBlockDamage((Player) (Object) this, source)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "rideTick", at = @At("HEAD"))
    private void examplemod$onRideTick(CallbackInfo ci) {
        if (this.examplemod$getCaptorId() != -1) {
            ((Player) (Object) this).setShiftKeyDown(false);
        }
    }
}
