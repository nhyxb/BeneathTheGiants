package com.example.examplemod.mixin;

import com.example.examplemod.vortex.AxolotlVortexAccess;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Axolotl.class)
public abstract class AxolotlVortexMixin implements AxolotlVortexAccess {
    @Unique
    private static final EntityDataAccessor<Integer> EXAMPLEMOD$VORTEX_TARGET_ID =
            SynchedEntityData.defineId(Axolotl.class, EntityDataSerializers.INT);

    @Unique
    private static final EntityDataAccessor<Byte> EXAMPLEMOD$VORTEX_PHASE =
            SynchedEntityData.defineId(Axolotl.class, EntityDataSerializers.BYTE);

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void examplemod$defineSynchedData(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(EXAMPLEMOD$VORTEX_TARGET_ID, -1);
        builder.define(EXAMPLEMOD$VORTEX_PHASE, (byte) 0);
    }

    @Override
    public int examplemod$getVortexTargetId() {
        return ((Axolotl) (Object) this).getEntityData().get(EXAMPLEMOD$VORTEX_TARGET_ID);
    }

    @Override
    public int examplemod$getVortexPhase() {
        return Byte.toUnsignedInt(((Axolotl) (Object) this).getEntityData().get(EXAMPLEMOD$VORTEX_PHASE));
    }

    @Override
    public void examplemod$setVortexState(int targetId, int phase) {
        ((Axolotl) (Object) this).getEntityData().set(EXAMPLEMOD$VORTEX_TARGET_ID, targetId);
        ((Axolotl) (Object) this).getEntityData().set(EXAMPLEMOD$VORTEX_PHASE, (byte) phase);
    }
}
