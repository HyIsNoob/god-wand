package net.godwand.item;

import net.godwand.entity.ArcaneOrbEntity;
import net.godwand.network.CinematicFxPayload;
import net.godwand.skill.RatioCollapseManager;
import net.godwand.skill.ShockwaveSlicer;
import net.godwand.skill.SpatialBridge;
import net.godwand.skill.SpatialPathWalker;
import net.godwand.skill.StoneCannonManager;
import net.godwand.skill.SupernovaManager;
import net.godwand.skill.AtomicManager;
import net.godwand.skill.AtomicSequence;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

public class GodWandItem extends Item {
    public static final int MODE_SPATIAL_PATH = 0;
    public static final int MODE_ARCANE_ORB = 1;
    public static final int MODE_RATIO_COLLAPSE = 2;
    public static final int MODE_SUPERNOVA = 3;
    public static final int MODE_STONE_CANNON = 4;
    public static final int MODE_ATOMIC = 5;
    public static final int TOTAL_MODES = 6;

    public static String chargeMeter(int level, int maximum) {
        int filled = Mth.clamp(level, 0, maximum);
        return "\u00a7f[\u00a7b" + "\u25a0".repeat(filled) + "\u00a78" + "\u25a1".repeat(maximum - filled) + "\u00a7f]";
    }

    public GodWandItem(Properties settings) {
        super(settings.stacksTo(1).attributes(createMeleeAttributes()));
    }

    static net.minecraft.world.item.component.ItemAttributeModifiers createMeleeAttributes() {
        return net.minecraft.world.item.component.ItemAttributeModifiers.builder()
                .add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,
                        new net.minecraft.world.entity.ai.attributes.AttributeModifier(BASE_ATTACK_DAMAGE_ID,4,
                                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE),
                        net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND).build();
    }

    public static int getSkillMode(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null && customData.contains("GodWandMode")) {
            return customData.copyTag().getInt("GodWandMode");
        }
        return MODE_SPATIAL_PATH;
    }

    public static void setSkillMode(ItemStack stack, int mode) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, nbt -> nbt.putInt("GodWandMode", mode));
    }

    public static void selectSkill(Player user, InteractionHand hand, int mode) {
        if (mode < 0 || mode >= TOTAL_MODES) return;
        ItemStack stack = user.getItemInHand(hand);
        if (!(stack.getItem() instanceof GodWandItem)) return;
        if (user.isUsingItem() && user.getUseItem() == stack) {
            if (user instanceof ServerPlayer player) {
                SupernovaManager.cancelCharge(player);
                AtomicManager.cancelCharge(player);
            }
            user.stopUsingItem();
        }
        setSkillMode(stack, mode);
        Level world = user.level();
        if (!world.isClientSide()) {
            if (mode == MODE_ATOMIC) user.displayClientMessage(Component.literal("§dI am Atomic"), true);
            switch (mode) {
                case MODE_SPATIAL_PATH -> {
                    user.displayClientMessage(Component.literal("§bSpatial Path"), true);
                }
                case MODE_ARCANE_ORB -> {
                    user.displayClientMessage(Component.literal("§dSingularity"), true);
                }
                case MODE_RATIO_COLLAPSE -> {
                    user.displayClientMessage(Component.literal("§6Earth Drill"), true);
                }
                case MODE_SUPERNOVA -> {
                    user.displayClientMessage(Component.literal("§cSupernova"), true);
                }
                case MODE_STONE_CANNON -> {
                    user.displayClientMessage(Component.literal("§6Stone Cannon"), true);
                }
            }
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        int currentMode = getSkillMode(stack);

        if (user.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);

        String dimId = world.dimension().location().toString();
        if (net.godwand.config.GodWandConfig.SERVER.isDimensionBlacklisted(dimId)) {
            if (!world.isClientSide()) {
                user.displayClientMessage(Component.translatable("message.godwand.dimension_blocked"), true);
            }
            return InteractionResultHolder.fail(stack);
        }

        // NORMAL RIGHT CLICK: Execute Current Selected Skill
        if (!world.isClientSide() && user instanceof ServerPlayer serverPlayer) {
            if (currentMode == MODE_ARCANE_ORB && ShockwaveSlicer.hasActiveTask(serverPlayer)) {
                boolean snapped = ShockwaveSlicer.triggerEarlySnap(serverPlayer);
                if (snapped) {
                    serverPlayer.stopUsingItem();
                    user.swing(hand, true);
                    serverPlayer.displayClientMessage(Component.literal("§dCollapsed!"), true);
                    return InteractionResultHolder.success(stack);
                }
            }
        }

        switch (currentMode) {
            case MODE_SPATIAL_PATH -> {
                if (!world.isClientSide() && user instanceof ServerPlayer serverPlayer) {
                    if (user.isShiftKeyDown()) {
                        SpatialBridge.toggle(serverPlayer);
                    } else {
                        SpatialPathWalker.toggle(serverPlayer);
                    }
                }
                return InteractionResultHolder.success(stack);
            }
            case MODE_ATOMIC -> {
                if (user instanceof ServerPlayer player && !AtomicManager.beginCharge(player,hand))
                    return InteractionResultHolder.fail(stack);
                user.startUsingItem(hand);
                return InteractionResultHolder.consume(stack);
            }
            case MODE_ARCANE_ORB, MODE_RATIO_COLLAPSE, MODE_SUPERNOVA, MODE_STONE_CANNON -> {
                if (currentMode == MODE_SUPERNOVA && user instanceof ServerPlayer player) SupernovaManager.beginCharge(player);
                user.startUsingItem(hand);
                return InteractionResultHolder.consume(stack);
            }
            default -> {
                return InteractionResultHolder.pass(stack);
            }
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        int mode = getSkillMode(stack);
        if (mode == MODE_ARCANE_ORB || mode == MODE_SUPERNOVA || mode == MODE_RATIO_COLLAPSE || mode == MODE_STONE_CANNON || mode == MODE_ATOMIC) {
            return 72000;
        }
        return 0;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        int mode = getSkillMode(stack);
        if (mode == MODE_ARCANE_ORB || mode == MODE_RATIO_COLLAPSE || mode == MODE_STONE_CANNON) {
            return UseAnim.BOW;
        }
        return UseAnim.NONE;
    }

    @Override
    public void onUseTick(Level world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        int currentMode = getSkillMode(stack);

        if (currentMode == MODE_ATOMIC) {
            int chargeTicks = getUseDuration(stack, user) - remainingUseTicks;
            if (user instanceof ServerPlayer player && chargeTicks >= AtomicSequence.CHARGE_TICKS) {
                AtomicManager.arm(player);
            }
            return;
        }

        // SKILL: ARCANE SINGULARITY
        if (currentMode == MODE_ARCANE_ORB) {
            int chargeTicks = this.getUseDuration(stack, user) - remainingUseTicks;
            if (chargeTicks < 0) return;

            if (!world.isClientSide() && user instanceof ServerPlayer player) {
                ServerLevel sw = (ServerLevel) world;

                Vec3 look = player.getViewVector(1.0f);
                Vec3 muzzle = player.getEyePosition().add(look.scale(2.15)).add(0, -0.16, 0);
                if (chargeTicks % 2 == 0) {
                    for (int i = 0; i < 2; i++) {
                        double angle = Math.random() * Math.PI * 2.0;
                        double dist = 0.50 + Math.random() * 0.65;
                        double px = muzzle.x + Math.cos(angle) * dist;
                        double py = muzzle.y + (Math.random() - 0.5) * dist * 0.7;
                        double pz = muzzle.z + Math.sin(angle) * dist;
                        Vec3 vel = muzzle.subtract(new Vec3(px, py, pz)).normalize().scale(0.16 + (chargeTicks / 45.0) * 0.14);
                        sw.sendParticles(new DustParticleOptions(new Vector3f(0.0f, 0.94f, 1.0f), 0.75f), px, py, pz, 0, vel.x, vel.y, vel.z, 1.0);
                        sw.sendParticles(ParticleTypes.REVERSE_PORTAL, px, py, pz, 0, vel.x, vel.y, vel.z, 1.0);
                    }
                }

                if (chargeTicks % 8 == 0) {
                    float pitch = 1.3f + Math.min(chargeTicks / 45.0f, 1.0f) * 0.6f;
                    world.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.45f, pitch);
                }

                int level;
                String meter;
                if (chargeTicks >= 45) {
                    level = 3;
                    meter = chargeMeter(3, 3);
                    if (chargeTicks == 45) {
                        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.75f, 1.5f);
                        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9f, 1.8f);
                    }
                } else if (chargeTicks >= 20) {
                    level = 2;
                    meter = chargeMeter(2, 3);
                    if (chargeTicks == 20) {
                        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.55f, 1.2f);
                    }
                } else {
                    level = 1;
                    meter = chargeMeter(1, 3);
                }

                player.displayClientMessage(Component.literal("§dSingularity: " + meter), true);
            }
            return;
        }

        // SKILL: STONE CANNON DRILL
        if (currentMode == MODE_RATIO_COLLAPSE) {
            int chargeTicks = this.getUseDuration(stack, user) - remainingUseTicks;
            if (chargeTicks < 0) return;

            int level;
            String meter;
            if (chargeTicks >= 68) {
                level = 4;
                meter = chargeMeter(4, 4);
            } else if (chargeTicks >= 44) {
                level = 3;
                meter = chargeMeter(3, 4);
            } else if (chargeTicks >= 22) {
                level = 2;
                meter = chargeMeter(2, 4);
            } else {
                level = 1;
                meter = chargeMeter(1, 4);
            }

            if (!world.isClientSide() && user instanceof ServerPlayer player) {
                ServerLevel sw = (ServerLevel) world;

                // Level transition sound cue
                if (chargeTicks == 22 || chargeTicks == 44 || chargeTicks == 68) {
                    float pitch = (level == 4) ? 1.8f : (level == 3) ? 1.4f : 1.1f;
                    world.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.65f, pitch);
                    world.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.HEAVY_CORE_PLACE, SoundSource.PLAYERS, 0.65f, pitch);
                }

                // Ground dust & tectonic smoke
                if (chargeTicks % 2 == 0) {
                    sw.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                            player.getX() + (Math.random() - 0.5) * 1.8, player.getY() + 0.1, player.getZ() + (Math.random() - 0.5) * 1.8,
                            1, 0, 0.06 + (level * 0.03), 0, 0.01);
                }

                // Target reach indicator
                double reach = switch (level) {
                    case 4 -> 85.0;
                    case 3 -> 65.0;
                    case 2 -> 45.0;
                    default -> 28.0;
                };
                HitResult hit = player.pick(reach, 0.0f, false);
                Vec3 targetPos = (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bHit)
                        ? bHit.getBlockPos().getCenter()
                        : player.getEyePosition().add(player.getViewVector(1.0f).scale(reach * 0.7));

                if (chargeTicks % 3 == 0) {
                    sw.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                            targetPos.x, targetPos.y + 0.5, targetPos.z, 2, 0.4, 0.2, 0.4, 0.05);
                }

                // Ambient compression pulse
                if (chargeTicks % 10 == 0) {
                    float pitch = 0.7f + (level * 0.25f);
                    world.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.4f, pitch);
                    world.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.HEAVY_CORE_PLACE, SoundSource.PLAYERS, 1.2f, pitch);
                }

                player.displayClientMessage(Component.literal("§6Earth Drill: " + meter), true);
            }
            return;
        }

        // SKILL: AERODYNAMIC STONE CANNON
        if (currentMode == MODE_STONE_CANNON) {
            int chargeTicks = this.getUseDuration(stack, user) - remainingUseTicks;
            if (chargeTicks < 0) return;

            int level = StoneCannonManager.calculateChargeLevel(chargeTicks);
            String meter = switch (level) {
                case 4 -> chargeMeter(4, 4);
                case 3 -> chargeMeter(3, 4);
                case 2 -> chargeMeter(2, 4);
                default -> chargeMeter(1, 4);
            };

            if (!world.isClientSide() && user instanceof ServerPlayer player) {
                StoneCannonManager.tickCharge(player, chargeTicks, level);
                player.displayClientMessage(Component.literal("§6Stone Cannon: " + meter), true);
            }
            return;
        }

        // SKILL: COSMIC SUPERNOVA
        if (currentMode != MODE_SUPERNOVA) return;

        int chargeTicks = this.getUseDuration(stack, user) - remainingUseTicks;
        if (chargeTicks < 0) return;

        if (!world.isClientSide() && user instanceof ServerPlayer player) {
            if (chargeTicks >= 140) {
                SupernovaManager.cast(player);
                player.getCooldowns().addCooldown(this, net.godwand.config.GodWandConfig.skillCooldownTicks(MODE_SUPERNOVA,1));
                player.stopUsingItem();
                player.displayClientMessage(Component.literal("§4Released."), true);
            }
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
        if (getSkillMode(stack) == MODE_ATOMIC) {
            if (user instanceof ServerPlayer player) {
                boolean committed=getUseDuration(stack,user)-remainingUseTicks>=AtomicSequence.CHARGE_TICKS;
                AtomicManager.releaseCharge(player);
                if(committed)player.getCooldowns().addCooldown(this,net.godwand.config.GodWandConfig.skillCooldownTicks(MODE_ATOMIC,1));
            }
            return;
        }
        if (getSkillMode(stack) == MODE_SUPERNOVA) {
            if (user instanceof ServerPlayer player) SupernovaManager.cancelCharge(player);
            int chargeTicks = this.getUseDuration(stack, user) - remainingUseTicks;
            if (chargeTicks < 140) {
                if (!world.isClientSide() && user instanceof Player player) {
                    world.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 2.0f, 0.8f);
                    world.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2.0f, 1.2f);
                    player.displayClientMessage(Component.literal("§7§oCancelled."), true);
                }
            }
            return;
        }

        if (getSkillMode(stack) == MODE_RATIO_COLLAPSE) {
            if (!world.isClientSide() && user instanceof ServerPlayer player) {
                int chargeDuration = this.getUseDuration(stack, user) - remainingUseTicks;
                int level = 1;
                if (chargeDuration >= 68) {
                    level = 4;
                } else if (chargeDuration >= 44) {
                    level = 3;
                } else if (chargeDuration >= 22) {
                    level = 2;
                }

                RatioCollapseManager.cast(player, level);
                player.getCooldowns().addCooldown(this, net.godwand.config.GodWandConfig.skillCooldownTicks(MODE_RATIO_COLLAPSE,level));
            }
            return;
        }

        if (getSkillMode(stack) == MODE_STONE_CANNON) {
            if (!world.isClientSide() && user instanceof ServerPlayer player) {
                int chargeDuration = this.getUseDuration(stack, user) - remainingUseTicks;
                StoneCannonManager.fire(player, chargeDuration);
                player.getCooldowns().addCooldown(this, net.godwand.config.GodWandConfig.skillCooldownTicks(MODE_STONE_CANNON,StoneCannonManager.calculateChargeLevel(chargeDuration)));
            }
            return;
        }

        if (getSkillMode(stack) != MODE_ARCANE_ORB) return;

        if (!world.isClientSide() && user instanceof Player player) {
            if (player.getCooldowns().isOnCooldown(this)) return;
            int chargeDuration = this.getUseDuration(stack, user) - remainingUseTicks;
            int level = 1;
            if (chargeDuration >= 45) {
                level = 3;
            } else if (chargeDuration >= 20) {
                level = 2;
            }

            Vec3 look = player.getViewVector(1.0f);
            Vec3 spawnPos = player.getEyePosition().add(look.scale(2.15)).add(0, -0.16, 0);

            ArcaneOrbEntity orb = new ArcaneOrbEntity(net.godwand.GodWandMod.ARCANE_ORB_ENTITY_TYPE, world);
            orb.setOwner(player);
            orb.setChargeLevel(level);
            orb.setPos(spawnPos.x, spawnPos.y, spawnPos.z);

            double speed = 2.4 + level * 0.35;
            orb.setDeltaMovement(look.scale(speed));
            world.addFreshEntity(orb);
            player.getCooldowns().addCooldown(this, net.godwand.config.GodWandConfig.skillCooldownTicks(MODE_ARCANE_ORB,level));

            // Powerful Arcane Recoil
            double recoilForce = switch (level) {
                case 1 -> 0.45;
                case 2 -> 0.85;
                default -> 1.35;
            };
            player.push(-look.x * recoilForce, -look.y * recoilForce * 0.4 + 0.18, -look.z * recoilForce);
            player.hurtMarked = true;

            // Muzzle Blast VFX
            ServerLevel sw = (ServerLevel) world;
            sw.sendParticles(ParticleTypes.FLASH, spawnPos.x, spawnPos.y, spawnPos.z, 2, 0.1, 0.1, 0.1, 0.0);
            sw.sendParticles(ParticleTypes.SONIC_BOOM, spawnPos.x, spawnPos.y, spawnPos.z, 1, 0, 0, 0, 0);
            sw.sendParticles(new DustParticleOptions(new Vector3f(0.0f, 0.95f, 1.0f), 1.5f),
                    spawnPos.x, spawnPos.y, spawnPos.z, 20, 0.3, 0.3, 0.3, 0.2);

            // Muzzle Audio Layering
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.75f, 1.3f);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.7f, 1.8f);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5f, 1.6f);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        int mode = getSkillMode(stack);
        tooltip.add(Component.literal("§7[Hold V]: §eSelect skill"));
        switch (mode) {
            case MODE_SPATIAL_PATH -> tooltip.add(Component.literal("§a§bSpatial Path §7(Toggle)"));
            case MODE_ARCANE_ORB -> tooltip.add(Component.literal("§a§dSingularity §7(Hold to charge)"));
            case MODE_RATIO_COLLAPSE -> tooltip.add(Component.literal("§a§6Earth Drill §7(Hold to charge)"));
            case MODE_SUPERNOVA -> tooltip.add(Component.literal("§a§cSupernova §7(Hold to charge)"));
            case MODE_STONE_CANNON -> tooltip.add(Component.literal("§a§6Stone Cannon §7(Hold to charge)"));
            case MODE_ATOMIC -> tooltip.add(Component.literal("§dI am Atomic §7(Hold through ATOMIC - Release to detonate)"));
        }
        super.appendHoverText(stack, context, tooltip, type);
    }
}

