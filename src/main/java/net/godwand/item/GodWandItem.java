package net.godwand.item;

import net.godwand.entity.ArcaneOrbEntity;
import net.godwand.skill.RatioCollapseManager;
import net.godwand.skill.SpatialPathWalker;
import net.godwand.skill.SupernovaManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
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
    public static final int MODE_SPATIAL_PATH = 0;
    public static final int MODE_ARCANE_ORB = 1;
    public static final int MODE_RATIO_COLLAPSE = 2;
    public static final int MODE_SUPERNOVA = 3;
    public static final int TOTAL_MODES = 4;

    public GodWandItem(Settings settings) {
        super(settings.maxCount(1));
    }

    public static int getSkillMode(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData != null && customData.contains("GodWandMode")) {
            return customData.copyNbt().getInt("GodWandMode");
        }
        return MODE_SPATIAL_PATH;
    }

    public static void setSkillMode(ItemStack stack, int mode) {
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> nbt.putInt("GodWandMode", mode));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        int currentMode = getSkillMode(stack);

        // SHIFT + RIGHT CLICK: Cycle Skill Mode
        if (user.isSneaking()) {
            int nextMode = (currentMode + 1) % TOTAL_MODES;
            setSkillMode(stack, nextMode);

            if (!world.isClient()) {
                switch (nextMode) {
                    case MODE_SPATIAL_PATH -> {
                        user.sendMessage(Text.literal("§b§l[✦ CHIÊU 1/3: LỐI ĐI KHÔNG GIAN (SPATIAL PATH) ✦]"), true);
                        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                                SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 2.0f, 1.3f);
                    }
                    case MODE_ARCANE_ORB -> {
                        user.sendMessage(Text.literal("§d§l[✦ CHIÊU 2/3: CẦU MA PHÁP HỦY DIỆT (SINGULARITY ORB) ✦]"), true);
                        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                                SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 2.0f, 1.4f);
                    }
                    case MODE_RATIO_COLLAPSE -> {
                        user.sendMessage(Text.literal("§6§l[✦ CHIÊU 3/4: SỤP ĐỔ THIÊN THẠCH (RATIO COLLAPSE) ✦]"), true);
                        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                                SoundEvents.BLOCK_HEAVY_CORE_PLACE, SoundCategory.PLAYERS, 2.5f, 0.9f);
                    }
                    case MODE_SUPERNOVA -> {
                        user.sendMessage(Text.literal("§c§l[✦ CHIÊU 4/4: SIÊU TÂN TINH (SUPERNOVA & CHRONO REVERSAL) ✦]"), true);
                        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                                SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 2.5f, 0.6f);
                        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                                SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 2.5f, 0.5f);
                    }
                }
            }
            return TypedActionResult.success(stack);
        }

        // NORMAL RIGHT CLICK: Execute Current Selected Skill
        switch (currentMode) {
            case MODE_SPATIAL_PATH -> {
                if (!world.isClient() && user instanceof ServerPlayerEntity serverPlayer) {
                    SpatialPathWalker.toggle(serverPlayer);
                }
                return TypedActionResult.success(stack);
            }
            case MODE_ARCANE_ORB -> {
                user.setCurrentHand(hand);
                return TypedActionResult.consume(stack);
            }
            case MODE_RATIO_COLLAPSE -> {
                if (!world.isClient() && user instanceof ServerPlayerEntity serverPlayer) {
                    RatioCollapseManager.cast(serverPlayer);
                    user.getItemCooldownManager().set(this, 30); // 1.5s tactical cooldown
                }
                return TypedActionResult.success(stack);
            }
            case MODE_SUPERNOVA -> {
                if (!world.isClient() && user instanceof ServerPlayerEntity serverPlayer) {
                    SupernovaManager.cast(serverPlayer);
                    user.getItemCooldownManager().set(this, 140); // 7s cooldown for apocalyptic skill
                }
                return TypedActionResult.success(stack);
            }
            default -> {
                return TypedActionResult.pass(stack);
            }
        }
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        if (getSkillMode(stack) == MODE_ARCANE_ORB) {
            return 72000;
        }
        return 0;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        if (getSkillMode(stack) == MODE_ARCANE_ORB) {
            return UseAction.BOW;
        }
        return UseAction.NONE;
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (getSkillMode(stack) != MODE_ARCANE_ORB) return;

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

            // Powerful Arcane Recoil
            double recoilForce = switch (level) {
                case 1 -> 0.45;
                case 2 -> 0.85;
                default -> 1.35;
            };
            player.addVelocity(-look.x * recoilForce, -look.y * recoilForce * 0.4 + 0.18, -look.z * recoilForce);
            player.velocityModified = true;

            // Muzzle Blast VFX
            ServerWorld sw = (ServerWorld) world;
            sw.spawnParticles(ParticleTypes.FLASH, player.getX() + look.x, player.getEyeY() + look.y, player.getZ() + look.z, 2, 0.1, 0.1, 0.1, 0.0);
            sw.spawnParticles(ParticleTypes.SONIC_BOOM, player.getX() + look.x * 1.5, player.getEyeY() + look.y * 1.5, player.getZ() + look.z * 1.5, 1, 0, 0, 0, 0);
            sw.spawnParticles(new DustParticleEffect(new Vector3f(0.0f, 0.95f, 1.0f), 1.5f),
                    player.getX() + look.x, player.getEyeY() + look.y, player.getZ() + look.z, 20, 0.3, 0.3, 0.3, 0.2);

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
        int mode = getSkillMode(stack);
        tooltip.add(Text.literal("§7[Shift + Chuột Phải]: §eĐổi chiêu thức"));
        switch (mode) {
            case MODE_SPATIAL_PATH -> tooltip.add(Text.literal("§aĐang chọn: §b[1] Lối Đi Không Gian §7(Bật/Tắt rẽ khối)"));
            case MODE_ARCANE_ORB -> tooltip.add(Text.literal("§aĐang chọn: §d[2] Cầu Ma Pháp Hủy Diệt §7(Giữ bắn)"));
            case MODE_RATIO_COLLAPSE -> tooltip.add(Text.literal("§aĐang chọn: §6[3] Sụp Đổ Thiên Thạch §7(Bứt phá & Giáng sập)"));
            case MODE_SUPERNOVA -> tooltip.add(Text.literal("§aĐang chọn: §c[4] Siêu Tân Tinh & Nghịch Đảo Thời Gian §7(Lỗ đen & Nổ bùng)"));
        }
        tooltip.add(Text.literal("§8--------------------------------"));
        tooltip.add(Text.translatable("item.godwand.god_wand.desc"));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
