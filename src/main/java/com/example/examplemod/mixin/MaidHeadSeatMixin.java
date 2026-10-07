package com.example.examplemod.mixin;

import com.example.examplemod.devour.DevourPlayerAccess;
import com.example.examplemod.maid.MaidHeadSeat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Entity.class, priority = 500)
public abstract class MaidHeadSeatMixin {
    @Inject(
            method = "positionRider(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$MoveFunction;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void examplemod$seatOnMaidHead(Entity passenger, Entity.MoveFunction callback, CallbackInfo ci) {
        if (!(passenger instanceof Player player)) {
            return;
        }
        if (player instanceof DevourPlayerAccess access && access.examplemod$getCaptorId() != -1) {
            return;
        }
        Entity vehicle = (Entity) (Object) this;
        if (!MaidHeadSeat.canSitOn(player, vehicle)) {
            return;
        }
        Vec3 feet = MaidHeadSeat.seatFeet(vehicle);
        callback.accept(passenger, feet.x, feet.y, feet.z);
        ci.cancel();
    }
}
