package net.godwand.item;

import net.godwand.entity.ArcaneOrbEntity;
import net.godwand.skill.SpatialPathWalker;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.List;

public class GodWandItem extends Item {
    public GodWandItem(Settings settings) {
        super(settings.maxCount(1));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (user.isSneaking()) {
            if (!world.isClient() && user instanceof ServerPlayerEntity serverPlayer) {
                SpatialPathWalker.toggle(serverPlayer);
            }
            return TypedActionResult.success(stack);
        } else {
            user.setCurrentHand(hand);
            return TypedActionResult.consume(stack);
        }
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BOW;
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (!world.isClient() && user instanceof PlayerEntity player) {
            int chargeDuration = this.getMaxUseTime(stack, user) - remainingUseTicks;
            int level = 1;
            if (chargeDuration >= 50) { // 2.5s+ -> Calamity Level 3
                level = 3;
            } else if (chargeDuration >= 24) { // 1.2s -> Level 2
                level = 2;
            }

            ArcaneOrbEntity orb = new ArcaneOrbEntity(net.godwand.GodWandMod.ARCANE_ORB_ENTITY_TYPE, world);
            orb.setOwner(player);
            orb.setChargeLevel(level);
            orb.setPosition(player.getEyePos().x, player.getEyePos().y - 0.1, player.getEyePos().z);

            Vec3d look = player.getRotationVec(1.0f);
            orb.setVelocity(look.x * 2.3, look.y * 2.3, look.z * 2.3);
            world.spawnEntity(orb);

            // Powerful Arcane Recoil: Player physically experiences the thrust of the god-tier weapon
            double recoilForce = switch (level) {
                case 1 -> 0.45;
                case 2 -> 0.85;
                default -> 1.35; // Level 3 Calamity recoil
            };
            player.addVelocity(-look.x * recoilForce, -look.y * recoilForce * 0.4 + 0.18, -look.z * recoilForce);
            player.velocityModified = true;

            // Muzzle Blast VFX
            ServerWorld sw = (ServerWorld) world;
            sw.spawnParticles(ParticleTypes.FLASH, player.getX() + look.x, player.getEyeY() + look.y, player.getZ() + look.z, 2, 0.1, 0.1, 0.1, 0.0);
            sw.spawnParticles(ParticleTypes.SONIC_BOOM, player.getX() + look.x * 1.5, player.getEyeY() + look.y * 1.5, player.getZ() + look.z * 1.5, 1, 0, 0, 0, 0);
            sw.spawnParticles(new DustParticleEffect(new Vector3f(0.0f, 0.95f, 1.0f), 1.5f),
                    player.getX() + look.x, player.getEyeY() + look.y, player.getZ() + look.z, 25, 0.3, 0.3, 0.3, 0.2);

            // Muzzle Audio Layering
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.PLAYERS, 2.0f, 1.3f);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 2.0f, 1.5f);
            if (level == 3) {
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 2.5f, 1.0f);
            }
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.godwand.god_wand.desc"));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
