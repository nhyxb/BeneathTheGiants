package com.example.examplemod.gametest;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.init.ModItems;
import com.example.examplemod.maid.MaidHeadSeat;
import com.example.examplemod.maid.MaidRideShake;
import com.example.examplemod.scale.PixelScaleHelper;
import com.example.examplemod.survival.SurvivalRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import com.example.examplemod.maid.MaidSeatEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ExampleMod.MODID)
@PrefixGameTestTemplate(false)
public final class MaidHeadSeatTests {
    @GameTest(template = "empty")
    public static void ownerSitsOnEquippedMaidHead(GameTestHelper helper) {
        EntityType<?> maidType = BuiltInRegistries.ENTITY_TYPE.getOptional(MaidHeadSeat.MAID_ID).orElse(null);
        helper.assertTrue(maidType != null, "Touhou Little Maid registers touhou_little_maid:maid");

        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        PixelScaleHelper.ensurePlayerMini(owner);
        Vec3 ownerPos = helper.absoluteVec(new Vec3(3.5, 2.0, 1.5));
        owner.moveTo(ownerPos.x, ownerPos.y, ownerPos.z, 0.0F, 0.0F);

        Entity maid = helper.spawn(maidType, new Vec3(3.5, 2.0, 3.5));
        helper.assertTrue(maid instanceof TamableAnimal, "Maid can record an owner");
        TamableAnimal animal = (TamableAnimal) maid;

        helper.assertTrue(!MaidHeadSeat.canSitOn(owner, maid), "An untamed maid is not a seat");
        animal.tame(owner);
        helper.assertTrue(!MaidHeadSeat.canSitOn(owner, maid), "A tamed maid without the bauble is not a seat");
        helper.assertTrue(!SurvivalRules.isValidMountTarget(owner, maid), "The mount key rejects a maid without the bauble");

        MaidHeadSeat.setSeatStack(maid, new ItemStack(ModItems.MAID_HEAD_SEAT.get()));
        helper.assertTrue(owner.getUUID().equals(animal.getOwnerUUID()), "Taming records the owner UUID");
        helper.assertTrue(MaidHeadSeat.canSitOn(owner, maid), "The owner can sit once the bauble is equipped");
        helper.assertTrue(SurvivalRules.isValidMountTarget(owner, maid), "The mount key accepts that maid");

        Player stranger = helper.makeMockPlayer(GameType.SURVIVAL);
        PixelScaleHelper.ensurePlayerMini(stranger);
        stranger.moveTo(ownerPos.x, ownerPos.y, ownerPos.z, 0.0F, 0.0F);
        helper.assertTrue(!MaidHeadSeat.canSitOn(stranger, maid), "Another player cannot use the seat");

        Player spectator = helper.makeMockPlayer(GameType.SPECTATOR);
        PixelScaleHelper.ensurePlayerMini(spectator);
        animal.setOwnerUUID(spectator.getUUID());
        helper.assertTrue(spectator.isSpectator(), "The spectator mock is actually spectating");
        helper.assertTrue(!MaidHeadSeat.canSitOn(spectator, maid), "A spectator owner cannot use the seat");
        animal.setOwnerUUID(owner.getUUID());

        helper.assertTrue(owner.startRiding(maid), "The owner mounts the maid");
        helper.assertTrue(MaidHeadSeat.isRiddenBy(owner, maid), "The crosshair skips the ridden maid");
        helper.assertTrue(!MaidHeadSeat.isRiddenBy(owner, stranger), "Other entities stay pickable");
        PlayerInteractEvent.EntityInteract interact = new PlayerInteractEvent.EntityInteract(owner, InteractionHand.MAIN_HAND, maid);
        MaidSeatEvents.onEntityInteract(interact);
        helper.assertTrue(interact.isCanceled(), "Right-click on the ridden maid does not open her GUI");
        AttackEntityEvent attack = new AttackEntityEvent(owner, maid);
        MaidSeatEvents.onAttack(attack);
        helper.assertTrue(attack.isCanceled(), "Left-click does not hit the ridden maid");
        maid.setYRot(0.0F);
        maid.positionRider(owner);
        Vec3 facingSouth = MaidHeadSeat.seatFeet(maid);
        helper.assertTrue(owner.position().distanceTo(facingSouth) < 0.001D,
                "Seat matches the front of the body, actual=" + owner.position() + " expected=" + facingSouth);
        helper.assertTrue(owner.getZ() > maid.getZ() + maid.getBbWidth() * 0.4D, "Seat is in front, not on the head");
        helper.assertTrue(owner.getY() < maid.getY() + maid.getBbHeight() * 0.8D, "Seat stays on the torso");
        helper.assertTrue(maid instanceof Mob mob && mob.getControllingPassenger() == owner, "The owner controls movement");
        owner.zza = 1.0F;
        owner.xxa = 0.0F;
        helper.assertTrue(MaidHeadSeat.riddenInput(owner).z == 1.0D, "Forward input is passed through");
        double walking = ((LivingEntity) maid).getAttributeValue(Attributes.MOVEMENT_SPEED);
        helper.assertTrue(Math.abs(MaidHeadSeat.riddenSpeed((LivingEntity) maid) - walking * MaidHeadSeat.RIDDEN_SPEED_FACTOR) < 1.0E-5D,
                "Riding slows the maid to one fifth");
        maid.setYRot(90.0F);
        maid.positionRider(owner);
        helper.assertTrue(owner.getX() < maid.getX() - maid.getBbWidth() * 0.4D, "Seat follows the maid's facing");

        MaidHeadSeat.setSeatStack(maid, ItemStack.EMPTY);
        MaidHeadSeat.dismountIfSeatRemoved(maid);
        helper.assertTrue(!owner.isPassenger(), "Removing the last bauble dismounts the owner");
        helper.assertTrue(!MaidHeadSeat.isRiddenBy(owner, maid), "Dismount restores clicks on the maid");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ridingShakeOnlyWhileMoving(GameTestHelper helper) {
        helper.assertTrue(!MaidRideShake.isMoving(0.0D, 0.0D, 0.0D), "Standing still does not shake");
        helper.assertTrue(!MaidRideShake.isMoving(1.0E-5D, 0.0D, 0.0D), "Sub-milliblock drift does not shake");
        helper.assertTrue(MaidRideShake.isMoving(0.02D, 0.0D, 0.0D), "Horizontal travel shakes");
        helper.assertTrue(MaidRideShake.isMoving(0.0D, 0.05D, 0.0D), "Vertical travel shakes");
        helper.assertTrue(MaidRideShake.pitchOffset(0.0F, 0.0F) == 0.0F, "Zero strength has no pitch");
        helper.assertTrue(MaidRideShake.yawOffset(1.0F, 0.0F) == 0.0F, "Zero strength has no yaw");
        helper.assertTrue(MaidRideShake.rollOffset(1.0F, 0.0F) == 0.0F, "Zero strength has no roll");
        float phase = (float) (Math.PI / (2.0D * 1.7D));
        float pitch = MaidRideShake.pitchOffset(phase, 1.0F);
        helper.assertTrue(pitch > MaidRideShake.MAX_PITCH * 0.5F && pitch <= MaidRideShake.MAX_PITCH,
                "Moving pitch stays inside the cap, actual=" + pitch);
        helper.assertTrue(Math.abs(MaidRideShake.yawOffset(phase, 1.0F)) <= MaidRideShake.MAX_YAW, "Yaw stays inside the cap");
        helper.assertTrue(Math.abs(MaidRideShake.rollOffset(phase, 1.0F)) <= MaidRideShake.MAX_ROLL, "Roll stays inside the cap");
        helper.succeed();
    }
}
