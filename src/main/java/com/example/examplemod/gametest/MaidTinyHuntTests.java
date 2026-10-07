package com.example.examplemod.gametest;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.config.SurvivalConfig;
import com.example.examplemod.init.ModDamageTypes;
import com.example.examplemod.init.ModItems;
import com.example.examplemod.maid.MaidTinyHunt;
import com.example.examplemod.maid.MaidTinyHuntTask;
import com.example.examplemod.scale.PehkuiScaleSupport;
import com.example.examplemod.scale.PixelScaleHelper;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(ExampleMod.MODID)
@PrefixGameTestTemplate(false)
public final class MaidTinyHuntTests {
    @GameTest(template = "empty")
    public static void tinyHuntShrinksThenStompsAndMayEat(GameTestHelper helper) {
        helper.assertTrue(TaskManager.findTask(MaidTinyHuntTask.UID).isPresent(), "Tiny hunt is a maid work mode");

        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        LivingEntity hunter = helper.spawn(EntityType.ZOMBIE, new Vec3(1.5, 2.0, 1.5));
        LivingEntity monster = helper.spawn(EntityType.ZOMBIE, new Vec3(3.5, 2.0, 1.5));
        LivingEntity cow = helper.spawn(EntityType.COW, new Vec3(4.5, 2.0, 1.5));
        LivingEntity pig = helper.spawn(EntityType.PIG, new Vec3(5.5, 2.0, 1.5));

        helper.assertTrue(MaidTinyHunt.isHuntTarget(hunter, owner, monster), "Monsters are hunted");
        helper.assertTrue(!MaidTinyHunt.isHuntTarget(hunter, owner, cow), "Unattacked animals are left alone");
        helper.assertTrue(!MaidTinyHunt.isHuntTarget(hunter, owner, owner), "The owner is not prey");
        Player stranger = helper.makeMockPlayer(GameType.SURVIVAL);
        Player creative = helper.makeMockPlayer(GameType.CREATIVE);
        helper.assertTrue(!MaidTinyHunt.isHuntTarget(hunter, owner, stranger), "An idle player is not hunted");
        owner.setLastHurtMob(stranger);
        helper.assertTrue(!MaidTinyHunt.isHuntTarget(hunter, owner, stranger),
                "The owner swinging first does not mark a player");
        hunter.setLastHurtByMob(stranger);
        helper.assertTrue(MaidTinyHunt.isHuntTarget(hunter, owner, stranger), "A player who hit the maid is hunted");
        Player ownerAggressor = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(!MaidTinyHunt.isHuntTarget(hunter, owner, ownerAggressor), "Another idle player is not hunted");
        owner.setLastHurtByMob(ownerAggressor);
        helper.assertTrue(MaidTinyHunt.isHuntTarget(hunter, owner, ownerAggressor), "A player who hit only the owner is hunted");
        hunter.setLastHurtByMob(null);
        owner.setLastHurtByMob(null);
        helper.assertTrue(!MaidTinyHunt.isHuntTarget(hunter, owner, creative), "Creative players are not hunted");
        creative.setLastHurtByMob(null);
        hunter.setLastHurtByMob(creative);
        helper.assertTrue(!MaidTinyHunt.isHuntTarget(hunter, owner, creative), "A creative attacker is still ignored");
        hunter.setLastHurtByMob(stranger);
        float strangerHealth = stranger.getHealth();
        helper.assertTrue(MaidTinyHunt.strike(hunter, stranger, 0.0D) == MaidTinyHunt.Result.SHRUNK,
                "The first ram shrinks a normal player");
        helper.assertTrue(PixelScaleHelper.isTiny(stranger), "The player receives the tiny-player mark");
        helper.assertTrue(stranger.getMaxHealth() == 10.0F, "Shrinking a player halves max health");
        helper.assertTrue(Math.abs(stranger.getHealth() - strangerHealth * 0.5F) < 1.0e-3F,
                "The shrink keeps the health ratio and does not stomp");
        float shrunkHealth = stranger.getHealth();
        MaidTinyHunt.Result secondRam = MaidTinyHunt.strike(hunter, stranger, 0.9D);
        helper.assertTrue(secondRam == MaidTinyHunt.Result.STOMPED,
                "A tiny player is stomped, got " + secondRam);
        helper.assertTrue(Math.abs(stranger.getHealth() - (shrunkHealth - MaidTinyHunt.STOMP_DAMAGE)) < 1.0e-3F,
                "The player stomp deals 2 damage");
        owner.setLastHurtMob(pig);
        helper.assertTrue(MaidTinyHunt.isHuntTarget(hunter, owner, pig), "The owner's current target is hunted");

        float health = monster.getHealth();
        helper.assertTrue(MaidTinyHunt.strike(hunter, monster, 0.0D) == MaidTinyHunt.Result.SHRUNK, "The first hit shrinks");
        helper.assertTrue(PixelScaleHelper.isPlayerTinyScale(monster), "Shrink uses the player's scale");
        helper.assertTrue(!monster.getAttributes().hasModifier(com.example.examplemod.init.ModAttributes.PIXEL_SCALE, PixelScaleHelper.TINY_PIXEL_SCALE_ID),
                "Hunt scale replaces the wand mark");
        if (PehkuiScaleSupport.isLoaded()) {
            helper.assertTrue(Math.abs(PehkuiScaleSupport.getEffectiveScale(monster) - PixelScaleHelper.PLAYER_TINY_SCALE_FLOAT) < 1.0e-3F,
                    "Pehkui scale matches the player");
        }
        helper.assertTrue(monster.getHealth() == health, "Shrinking does not deal damage");
        helper.assertTrue(!monster.isPassenger(), "A full-size hit is not an eat");

        helper.assertTrue(MaidTinyHunt.strike(hunter, monster, 0.9D) == MaidTinyHunt.Result.STOMPED, "A small target is stomped");
        helper.assertTrue(monster.getHealth() < health && monster.getHealth() > monster.getMaxHealth() * 0.5F,
                "A healthy small target stays above half and is not eaten");
        helper.assertTrue(monster.getLastDamageSource() != null && monster.getLastDamageSource().is(ModDamageTypes.STOMP),
                "The small-target hit uses stomp damage");
        helper.assertTrue(!monster.isPassenger(), "Above half health is not eaten");

        monster.setHealth(monster.getMaxHealth() * 0.5F + 1.0F);
        helper.assertTrue(MaidTinyHunt.strike(hunter, monster, 0.9D) == MaidTinyHunt.Result.STOMPED, "A failed eat roll still stomps");
        helper.assertTrue(monster.getHealth() <= monster.getMaxHealth() * 0.5F, "The stomp crosses half health");
        helper.assertTrue(!monster.isPassenger(), "The 50% miss leaves the target down");

        LivingEntity meal = helper.spawn(EntityType.ZOMBIE, new Vec3(3.5, 2.0, 3.5));
        PixelScaleHelper.shrinkToPlayerScale(meal);
        meal.setHealth(meal.getMaxHealth() * 0.5F + 1.0F);
        helper.assertTrue(MaidTinyHunt.strike(hunter, meal, 0.0D) == MaidTinyHunt.Result.DEVOURING, "The 50% hit picks the target up");
        helper.assertTrue(meal.getVehicle() == hunter, "The maid is holding the meal");
        helper.assertTrue(!MaidTinyHunt.isRamContact(hunter, cow), "A distant body is not a ram");
        cow.setPos(hunter.position());
        helper.assertTrue(MaidTinyHunt.isRamContact(hunter, cow), "Overlapping bodies count as a ram");

        float hunterHealth = hunter.getHealth();
        hunter.hurt(hunter.damageSources().mobAttack((net.minecraft.world.entity.Mob) meal), 6.0F);
        helper.assertTrue(hunter.getHealth() == hunterHealth, "Prey being eaten cannot hurt the maid");
        hunter.hurt(hunter.damageSources().mobAttack((net.minecraft.world.entity.Mob) monster), 6.0F);
        helper.assertTrue(hunter.getHealth() < hunterHealth, "Another mob can still hurt the maid");

        long done = meal.level().getGameTime() + SurvivalConfig.DEVOUR_DURATION.get();
        MaidTinyHunt.tickDevour(meal, done);
        helper.assertTrue(!meal.isAlive(), "The held target is eaten");
        helper.assertTrue(meal.getLastDamageSource() != null && meal.getLastDamageSource().is(ModDamageTypes.DEVOURED),
                "The finish uses the devoured damage");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fangTogglesPlayerAndJoinDoesNotShrink(GameTestHelper helper) {
        Player fresh = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(!PixelScaleHelper.isTiny(fresh), "A new player starts at normal size");
        helper.assertTrue(fresh.getBbHeight() > 1.0F, "A new player keeps a normal hitbox");

        NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(fresh, fresh.level()));
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(fresh));
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(fresh, false));
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(fresh, Level.OVERWORLD, Level.NETHER));
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(fresh));
        helper.assertTrue(!PixelScaleHelper.isTiny(fresh), "Lifecycle events must not shrink a normal player");

        Player copied = helper.makeMockPlayer(GameType.SURVIVAL);
        NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(copied, fresh, true));
        helper.assertTrue(!PixelScaleHelper.isTiny(copied), "Dying at normal size stays normal");

        ServerPlayer serverPlayer = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "fangtoggle"));
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(serverPlayer));
        helper.assertTrue(!PixelScaleHelper.isTiny(serverPlayer), "Server player ticks must not force the tiny scale");

        ItemStack fang = new ItemStack(ModItems.MAID_TINY_HUNT.get());
        serverPlayer.setItemInHand(InteractionHand.MAIN_HAND, fang);
        Vec3 pos = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
        serverPlayer.setPos(pos.x, pos.y, pos.z);

        var first = fang.getItem().use(serverPlayer.level(), serverPlayer, InteractionHand.MAIN_HAND);
        helper.assertTrue(first.getResult().consumesAction(), "Using the fang succeeds");
        helper.assertTrue(fang.getCount() == 1, "The fang is not consumed");
        helper.assertTrue(PixelScaleHelper.isTiny(serverPlayer), "The first use shrinks the player");
        helper.assertTrue(serverPlayer.getBbHeight() < 0.1F, "Shrunk standing height is a fraction of a block");
        helper.assertTrue(serverPlayer.getMaxHealth() == 10.0F, "Shrinking halves max health");

        var blockedByCooldown = fang.getItem().use(serverPlayer.level(), serverPlayer, InteractionHand.MAIN_HAND);
        helper.assertTrue(!blockedByCooldown.getResult().consumesAction(), "Cooldown rejects a second use");
        helper.assertTrue(PixelScaleHelper.isTiny(serverPlayer), "Cooldown does not restore the player");

        serverPlayer.getCooldowns().removeCooldown(fang.getItem());
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.OBSIDIAN.defaultBlockState());
        var blockedBySpace = fang.getItem().use(serverPlayer.level(), serverPlayer, InteractionHand.MAIN_HAND);
        helper.assertTrue(!blockedBySpace.getResult().consumesAction(), "A low ceiling blocks restoration");
        helper.assertTrue(PixelScaleHelper.isTiny(serverPlayer), "A blocked restore leaves the player tiny");
        helper.assertTrue(serverPlayer.getMaxHealth() == 10.0F, "A blocked restore keeps the tiny health");

        serverPlayer.getCooldowns().removeCooldown(fang.getItem());
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.AIR.defaultBlockState());
        float healthBefore = serverPlayer.getHealth();
        var restored = fang.getItem().use(serverPlayer.level(), serverPlayer, InteractionHand.MAIN_HAND);
        helper.assertTrue(restored.getResult().consumesAction(), "The second real use restores");
        helper.assertTrue(!PixelScaleHelper.isTiny(serverPlayer), "The player is full size again");
        helper.assertTrue(serverPlayer.getBbHeight() > 1.0F, "Restored hitbox is a normal player");
        helper.assertTrue(serverPlayer.getMaxHealth() == 20.0F, "Max health returns");
        helper.assertTrue(Math.abs(serverPlayer.getHealth() - healthBefore * 2.0F) < 1.0e-3F, "Health ratio is kept");
        helper.assertTrue(fang.getCount() == 1, "Restoration does not consume the fang");

        Player reborn = helper.makeMockPlayer(GameType.SURVIVAL);
        PixelScaleHelper.ensurePlayerMini(serverPlayer);
        NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(reborn, serverPlayer, true));
        helper.assertTrue(PixelScaleHelper.isTiny(reborn), "Death keeps an opted-in tiny size");
        helper.succeed();
    }
}
