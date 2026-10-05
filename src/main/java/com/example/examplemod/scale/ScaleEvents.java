package com.example.examplemod.scale;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.init.ModDamageTypes;
import com.example.examplemod.init.ModTags;
import com.example.examplemod.retaliate.FriendlyRetaliateGoal;
import com.example.examplemod.stomp.StompHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = ExampleMod.MODID)
public class ScaleEvents {
    // Predators whose vanilla attack goals cannot target players need the custom hunt goal;
    // foxes and spiders etc. already attack any set target through vanilla MeleeAttackGoal.
    // Axolotl is excluded on purpose: it owns the dedicated F12 whirlpool feeding mechanic.
    private static final java.util.Set<net.minecraft.world.entity.EntityType<?>> PREDATORS_NEEDING_HUNT_GOAL =
            java.util.Set.<net.minecraft.world.entity.EntityType<?>>of(
                    net.minecraft.world.entity.EntityType.CHICKEN,
                    net.minecraft.world.entity.EntityType.CAT,
                    net.minecraft.world.entity.EntityType.OCELOT,
                    net.minecraft.world.entity.EntityType.BAT,
                    net.minecraft.world.entity.EntityType.PARROT,
                    net.minecraft.world.entity.EntityType.FROG);


    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof Player player) {
            PixelScaleHelper.ensurePlayerMini(player);
        } else if (event.getEntity() instanceof LivingEntity living) {
            PixelScaleHelper.migrateLivingEntity(living);
            if (living instanceof Mob mob && !(mob instanceof Enemy)) {
                attachRetaliateGoal(mob);
            }
            if (living instanceof Mob mob && mob.getType().is(ModTags.PREDATORY_INSECTS)) {
                mob.targetSelector.addGoal(0, new NearestAttackableTargetGoal<Player>(mob, Player.class, 10,
                        true, false, target -> target instanceof Player playerTarget && playerTarget.isAlive()
                        && !playerTarget.isCreative() && !playerTarget.isSpectator()
                        && PixelScaleHelper.isTiny(playerTarget)
                        && !(mob instanceof net.minecraft.world.entity.TamableAnimal tamable && tamable.isTame())));
                if (PREDATORS_NEEDING_HUNT_GOAL.contains(mob.getType())) {
                    mob.goalSelector.addGoal(1, new com.example.examplemod.predator.PredatorHuntGoal(mob));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity living) {
            StompHandler.clearCooldown(living);
        } else {
            StompHandler.clearCooldown(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        handleWandInteraction(event.getEntity(), event.getHand(), event.getItemStack(), event.getTarget(), event::setCancellationResult, () -> event.setCanceled(true));
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        handleWandInteraction(event.getEntity(), event.getHand(), event.getItemStack(), event.getTarget(), event::setCancellationResult, () -> event.setCanceled(true));
    }

    private static void handleWandInteraction(
            Player player,
            InteractionHand hand,
            ItemStack stack,
            Entity rawTarget,
            java.util.function.Consumer<InteractionResult> setCancellationResult,
            Runnable setCanceled
    ) {
        if (!stack.is(com.example.examplemod.init.ModItems.SCALE_WAND.get())) {
            return;
        }

        Entity target = rawTarget;
        if (target instanceof net.neoforged.neoforge.entity.PartEntity<?> part && part.getParent() instanceof LivingEntity livingParent) {
            target = livingParent;
        }

        if (target instanceof Player) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.examplemod.scale_wand.reject_player"), true);
            setCancellationResult.accept(InteractionResult.FAIL);
            setCanceled.run();
            return;
        }

        if (!(target instanceof LivingEntity livingTarget)) {
            setCancellationResult.accept(InteractionResult.FAIL);
            setCanceled.run();
            return;
        }

        if (player.getCooldowns().isOnCooldown(com.example.examplemod.init.ModItems.SCALE_WAND.get())) {
            setCancellationResult.accept(InteractionResult.FAIL);
            setCanceled.run();
            return;
        }

        if (player.level().isClientSide) {
            setCancellationResult.accept(InteractionResult.sidedSuccess(true));
            setCanceled.run();
            return;
        }

        PixelScaleHelper.ToggleResult result = PixelScaleHelper.toggleLivingTarget(player, livingTarget);
        if (result == PixelScaleHelper.ToggleResult.APPLIED_MINI || result == PixelScaleHelper.ToggleResult.RESTORED) {
            player.getCooldowns().addCooldown(com.example.examplemod.init.ModItems.SCALE_WAND.get(), 10);
            setCancellationResult.accept(InteractionResult.SUCCESS);
            setCanceled.run();
        } else if (result == PixelScaleHelper.ToggleResult.RESTORE_BLOCKED) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.examplemod.scale_wand.restore_blocked"), true);
            setCancellationResult.accept(InteractionResult.FAIL);
            setCanceled.run();
        } else {
            setCancellationResult.accept(InteractionResult.FAIL);
            setCanceled.run();
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        PixelScaleHelper.ensurePlayerMini(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        PixelScaleHelper.ensurePlayerMini(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        PixelScaleHelper.ensurePlayerMini(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        PixelScaleHelper.ensurePlayerMini(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PixelScaleHelper.ensurePlayerMini(player);
        }
    }

    @SubscribeEvent
    public static void onEntityTickPre(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof LivingEntity living) {
            StompHandler.recordPreTick(living);
        }
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity living) {
            StompHandler.checkStompOnTick(living);
        }
    }

    @SubscribeEvent
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        if (source.is(ModDamageTypes.STOMP)) {
            return;
        }
        Entity attacker = source.getEntity();
        if (attacker instanceof LivingEntity livingAttacker && PixelScaleHelper.isTiny(livingAttacker)) {
            event.setAmount(event.getAmount() * 0.25F);
        }
    }

    @SubscribeEvent
    public static void onLivingKnockBack(LivingKnockBackEvent event) {
        if (PixelScaleHelper.isTiny(event.getEntity())) {
            event.setStrength(event.getStrength() * 2.0F);
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (PixelScaleHelper.isTiny(player)) {
            event.setNewSpeed(event.getNewSpeed() * 0.2F);
        }
    }

    @SubscribeEvent
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof Mob mob && !(mob instanceof Enemy)) {
            Entity attackerEntity = event.getSource().getEntity();
            if (attackerEntity instanceof LivingEntity attacker) {
                FriendlyRetaliateGoal goal = getOrAttachRetaliateGoal(mob);
                if (goal != null) {
                    goal.setRetaliateTarget(attacker);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        StompHandler.clearAllCooldowns();
    }

    public static FriendlyRetaliateGoal getOrAttachRetaliateGoal(Mob mob) {
        for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof FriendlyRetaliateGoal retaliateGoal) {
                return retaliateGoal;
            }
        }
        FriendlyRetaliateGoal newGoal = new FriendlyRetaliateGoal(mob);
        mob.goalSelector.addGoal(0, newGoal);
        return newGoal;
    }

    private static void attachRetaliateGoal(Mob mob) {
        getOrAttachRetaliateGoal(mob);
    }
}
