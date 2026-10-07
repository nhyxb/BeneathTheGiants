package com.example.examplemod.mixin.client;

import com.example.examplemod.maid.MaidHeadSeat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Predicate;

@Mixin(GameRenderer.class)
public abstract class MaidRidePickMixin {
    @ModifyArg(
            method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;"
            ),
            index = 4
    )
    private Predicate<Entity> examplemod$passThroughRiddenMaid(Predicate<Entity> vanilla) {
        Player player = Minecraft.getInstance().player;
        if (player == null || !MaidHeadSeat.isMaid(player.getVehicle())) {
            return vanilla;
        }
        return entity -> !MaidHeadSeat.isRiddenBy(player, entity) && vanilla.test(entity);
    }
}
