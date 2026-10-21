package net.godwand.item;

import net.godwand.entity.ArcaneOrbEntity;
import net.godwand.skill.SpatialPathWalker;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

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
                world.playSound(null, user.getX(), user.getY(), user.getZ(),
                        SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.5f, 1.6f);
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
            orb.setVelocity(look.x * 2.2, look.y * 2.2, look.z * 2.2);
            world.spawnEntity(orb);

            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE, SoundCategory.PLAYERS, 1.2f, 1.4f);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.godwand.god_wand.desc"));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
