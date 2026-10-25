package net.godwand.item;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.godwand.entity.ArcaneOrbEntity;
import net.godwand.network.CinematicFxPayload;
import net.godwand.skill.RatioCollapseManager;
import net.godwand.skill.ShockwaveSlicer;
import net.godwand.skill.SpatialBridge;
import net.godwand.skill.SpatialPathWalker;
import net.godwand.skill.StoneCannonManager;
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
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.List;

public class GodWandItem extends Item {
    public static final int MODE_SPATIAL_PATH = 0;
    public static final int MODE_ARCANE_ORB = 1;
    public static final int MODE_RATIO_COLLAPSE = 2;
    public static final int MODE_SUPERNOVA = 3;
    public static final int MODE_STONE_CANNON = 4;
    public static final int TOTAL_MODES = 5;

    public static String chargeMeter(int level, int maximum) {
        int filled = MathHelper.clamp(level, 0, maximum);
        return "\u00a7f[\u00a7b" + "\u25a0".repeat(filled) + "\u00a78" + "\u25a1".repeat(maximum - filled) + "\u00a7f]";
    }

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

    public static void selectSkill(PlayerEntity user, Hand hand, int mode) {
        if (mode < 0 || mode >= TOTAL_MODES) return;
        ItemStack stack = user.getStackInHand(hand);
        if (!(stack.getItem() instanceof GodWandItem)) return;
        if (user.isUsingItem() && user.getActiveItem() == stack) {
            if (user instanceof ServerPlayerEntity player) SupernovaManager.cancelCharge(player);
            user.clearActiveItem();
        }
        setSkillMode(stack, mode);
        World world = user.getWorld();
        if (!world.isClient()) {
            switch (mode) {
                case MODE_SPATIAL_PATH -> {
                    user.sendMessage(Text.literal("§bSpatial Path"), true);
                }
                case MODE_ARCANE_ORB -> {
                    user.sendMessage(Text.literal("§dSingularity"), true);
                }
                case MODE_RATIO_COLLAPSE -> {
                    user.sendMessage(Text.literal("§6Earth Drill"), true);
                }
                case MODE_SUPERNOVA -> {
                    user.sendMessage(Text.literal("§cSupernova"), true);
                }
                case MODE_STONE_CANNON -> {
                    user.sendMessage(Text.literal("§6Stone Cannon"), true);
                }
            }
        }
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        int currentMode = getSkillMode(stack);

        if (user.getItemCooldownManager().isCoolingDown(this)) return TypedActionResult.fail(stack);

        // NORMAL RIGHT CLICK: Execute Current Selected Skill
        if (!world.isClient() && user instanceof ServerPlayerEntity serverPlayer) {
            if (currentMode == MODE_ARCANE_ORB && ShockwaveSlicer.hasActiveTask(serverPlayer)) {
                boolean snapped = ShockwaveSlicer.triggerEarlySnap(serverPlayer);
                if (snapped) {
                    serverPlayer.clearActiveItem();
                    user.swingHand(hand, true);
                    serverPlayer.sendMessage(Text.literal("§dCollapsed!"), true);
                    return TypedActionResult.success(stack);
                }
            }
        }

        switch (currentMode) {
            case MODE_SPATIAL_PATH -> {
                if (!world.isClient() && user instanceof ServerPlayerEntity serverPlayer) {
                    if (user.isSneaking()) {
                        SpatialBridge.toggle(serverPlayer);
                    } else {
                        SpatialPathWalker.toggle(serverPlayer);
                    }
                }
                return TypedActionResult.success(stack);
            }
            case MODE_ARCANE_ORB, MODE_RATIO_COLLAPSE, MODE_SUPERNOVA, MODE_STONE_CANNON -> {
                if (currentMode == MODE_SUPERNOVA && user instanceof ServerPlayerEntity player) SupernovaManager.beginCharge(player);
                user.setCurrentHand(hand);
                return TypedActionResult.consume(stack);
            }
            default -> {
                return TypedActionResult.pass(stack);
            }
        }
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        int mode = getSkillMode(stack);
        if (mode == MODE_ARCANE_ORB || mode == MODE_SUPERNOVA || mode == MODE_RATIO_COLLAPSE || mode == MODE_STONE_CANNON) {
            return 72000;
        }
        return 0;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        int mode = getSkillMode(stack);
        if (mode == MODE_ARCANE_ORB || mode == MODE_RATIO_COLLAPSE || mode == MODE_STONE_CANNON) {
            return UseAction.BOW;
        } else if (mode == MODE_SUPERNOVA) {
            return UseAction.SPEAR;
        }
        return UseAction.NONE;
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        int currentMode = getSkillMode(stack);

        // SKILL: ARCANE SINGULARITY
        if (currentMode == MODE_ARCANE_ORB) {
            int chargeTicks = this.getMaxUseTime(stack, user) - remainingUseTicks;
            if (chargeTicks < 0) return;

            if (!world.isClient() && user instanceof ServerPlayerEntity player) {
                ServerWorld sw = (ServerWorld) world;

                Vec3d look = player.getRotationVec(1.0f);
                Vec3d muzzle = player.getEyePos().add(look.multiply(2.15)).add(0, -0.16, 0);
                if (chargeTicks % 2 == 0) {
                    for (int i = 0; i < 2; i++) {
                        double angle = Math.random() * Math.PI * 2.0;
                        double dist = 0.50 + Math.random() * 0.65;
                        double px = muzzle.x + Math.cos(angle) * dist;
                        double py = muzzle.y + (Math.random() - 0.5) * dist * 0.7;
                        double pz = muzzle.z + Math.sin(angle) * dist;
                        Vec3d vel = muzzle.subtract(new Vec3d(px, py, pz)).normalize().multiply(0.16 + (chargeTicks / 45.0) * 0.14);
                        sw.spawnParticles(new DustParticleEffect(new Vector3f(0.0f, 0.94f, 1.0f), 0.75f), px, py, pz, 0, vel.x, vel.y, vel.z, 1.0);
                        sw.spawnParticles(ParticleTypes.REVERSE_PORTAL, px, py, pz, 0, vel.x, vel.y, vel.z, 1.0);
                    }
                }

                if (chargeTicks % 8 == 0) {
                    float pitch = 1.3f + Math.min(chargeTicks / 45.0f, 1.0f) * 0.6f;
                    world.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 0.45f, pitch);
                }

                int level;
                String meter;
                if (chargeTicks >= 45) {
                    level = 3;
                    meter = chargeMeter(3, 3);
                    if (chargeTicks == 45) {
                        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 0.75f, 1.5f);
                        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.9f, 1.8f);
                    }
                } else if (chargeTicks >= 20) {
                    level = 2;
                    meter = chargeMeter(2, 3);
                    if (chargeTicks == 20) {
                        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 0.55f, 1.2f);
                    }
                } else {
                    level = 1;
                    meter = chargeMeter(1, 3);
                }

                player.sendMessage(Text.literal("§dSingularity: " + meter), true);
            }
            return;
        }

        // SKILL: STONE CANNON DRILL
        if (currentMode == MODE_RATIO_COLLAPSE) {
            int chargeTicks = this.getMaxUseTime(stack, user) - remainingUseTicks;
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

            if (!world.isClient() && user instanceof ServerPlayerEntity player) {
                ServerWorld sw = (ServerWorld) world;

                // Level transition sound cue
                if (chargeTicks == 22 || chargeTicks == 44 || chargeTicks == 68) {
                    float pitch = (level == 4) ? 1.8f : (level == 3) ? 1.4f : 1.1f;
                    world.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 0.65f, pitch);
                    world.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.BLOCK_HEAVY_CORE_PLACE, SoundCategory.PLAYERS, 0.65f, pitch);
                }

                // Ground dust & tectonic smoke
                if (chargeTicks % 2 == 0) {
                    sw.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
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
                HitResult hit = player.raycast(reach, 0.0f, false);
                Vec3d targetPos = (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bHit)
                        ? bHit.getBlockPos().toCenterPos()
                        : player.getEyePos().add(player.getRotationVec(1.0f).multiply(reach * 0.7));

                if (chargeTicks % 3 == 0) {
                    sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                            targetPos.x, targetPos.y + 0.5, targetPos.z, 2, 0.4, 0.2, 0.4, 0.05);
                }

                // Ambient compression pulse
                if (chargeTicks % 10 == 0) {
                    float pitch = 0.7f + (level * 0.25f);
                    world.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 1.4f, pitch);
                    world.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.BLOCK_HEAVY_CORE_PLACE, SoundCategory.PLAYERS, 1.2f, pitch);
                }

                player.sendMessage(Text.literal("§6Earth Drill: " + meter), true);
            }
            return;
        }

        // SKILL: AERODYNAMIC STONE CANNON
        if (currentMode == MODE_STONE_CANNON) {
            int chargeTicks = this.getMaxUseTime(stack, user) - remainingUseTicks;
            if (chargeTicks < 0) return;

            int level = StoneCannonManager.calculateChargeLevel(chargeTicks);
            String meter = switch (level) {
                case 4 -> chargeMeter(4, 4);
                case 3 -> chargeMeter(3, 4);
                case 2 -> chargeMeter(2, 4);
                default -> chargeMeter(1, 4);
            };

            if (!world.isClient() && user instanceof ServerPlayerEntity player) {
                StoneCannonManager.tickCharge(player, chargeTicks, level);
                player.sendMessage(Text.literal("§6Stone Cannon: " + meter), true);
            }
            return;
        }

        // SKILL: COSMIC SUPERNOVA
        if (currentMode != MODE_SUPERNOVA) return;

        int chargeTicks = this.getMaxUseTime(stack, user) - remainingUseTicks;
        if (chargeTicks < 0) return;

        if (!world.isClient() && user instanceof ServerPlayerEntity player) {
            if (chargeTicks >= 140) {
                SupernovaManager.cast(player);
                player.getItemCooldownManager().set(this, net.godwand.config.GodWandConfig.cooldownTicks(520));
                player.clearActiveItem();
                player.sendMessage(Text.literal("§4Released."), true);
            }
        }
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (getSkillMode(stack) == MODE_SUPERNOVA) {
            if (user instanceof ServerPlayerEntity player) SupernovaManager.cancelCharge(player);
            int chargeTicks = this.getMaxUseTime(stack, user) - remainingUseTicks;
            if (chargeTicks < 140) {
                if (!world.isClient() && user instanceof PlayerEntity player) {
                    world.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 2.0f, 0.8f);
                    world.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 2.0f, 1.2f);
                    player.sendMessage(Text.literal("§7§oCancelled."), true);
                }
            }
            return;
        }

        if (getSkillMode(stack) == MODE_RATIO_COLLAPSE) {
            if (!world.isClient() && user instanceof ServerPlayerEntity player) {
                int chargeDuration = this.getMaxUseTime(stack, user) - remainingUseTicks;
                int level = 1;
                if (chargeDuration >= 68) {
                    level = 4;
                } else if (chargeDuration >= 44) {
                    level = 3;
                } else if (chargeDuration >= 22) {
                    level = 2;
                }

                RatioCollapseManager.cast(player, level);
                player.getItemCooldownManager().set(this, net.godwand.config.GodWandConfig.cooldownTicks(35));
            }
            return;
        }

        if (getSkillMode(stack) == MODE_STONE_CANNON) {
            if (!world.isClient() && user instanceof ServerPlayerEntity player) {
                int chargeDuration = this.getMaxUseTime(stack, user) - remainingUseTicks;
                StoneCannonManager.fire(player, chargeDuration);
                player.getItemCooldownManager().set(this, net.godwand.config.GodWandConfig.cooldownTicks(30));
            }
            return;
        }

        if (getSkillMode(stack) != MODE_ARCANE_ORB) return;

        if (!world.isClient() && user instanceof PlayerEntity player) {
            if (player.getItemCooldownManager().isCoolingDown(this)) return;
            int chargeDuration = this.getMaxUseTime(stack, user) - remainingUseTicks;
            int level = 1;
            if (chargeDuration >= 45) {
                level = 3;
            } else if (chargeDuration >= 20) {
                level = 2;
            }

            Vec3d look = player.getRotationVec(1.0f);
            Vec3d spawnPos = player.getEyePos().add(look.multiply(2.15)).add(0, -0.16, 0);

            ArcaneOrbEntity orb = new ArcaneOrbEntity(net.godwand.GodWandMod.ARCANE_ORB_ENTITY_TYPE, world);
            orb.setOwner(player);
            orb.setChargeLevel(level);
            orb.setPosition(spawnPos.x, spawnPos.y, spawnPos.z);

            double speed = 2.4 + level * 0.35;
            orb.setVelocity(look.multiply(speed));
            world.spawnEntity(orb);
            player.getItemCooldownManager().set(this, net.godwand.config.GodWandConfig.cooldownTicks(20));

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
            sw.spawnParticles(ParticleTypes.FLASH, spawnPos.x, spawnPos.y, spawnPos.z, 2, 0.1, 0.1, 0.1, 0.0);
            sw.spawnParticles(ParticleTypes.SONIC_BOOM, spawnPos.x, spawnPos.y, spawnPos.z, 1, 0, 0, 0, 0);
            sw.spawnParticles(new DustParticleEffect(new Vector3f(0.0f, 0.95f, 1.0f), 1.5f),
                    spawnPos.x, spawnPos.y, spawnPos.z, 20, 0.3, 0.3, 0.3, 0.2);

            // Muzzle Audio Layering
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 0.75f, 1.3f);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.7f, 1.8f);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.5f, 1.6f);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        int mode = getSkillMode(stack);
        tooltip.add(Text.literal("§7[Hold V]: §eSelect skill"));
        switch (mode) {
            case MODE_SPATIAL_PATH -> tooltip.add(Text.literal("§a§bSpatial Path §7(Toggle)"));
            case MODE_ARCANE_ORB -> tooltip.add(Text.literal("§a§dSingularity §7(Hold to charge)"));
            case MODE_RATIO_COLLAPSE -> tooltip.add(Text.literal("§a§6Earth Drill §7(Hold to charge)"));
            case MODE_SUPERNOVA -> tooltip.add(Text.literal("§a§cSupernova §7(Hold to charge)"));
            case MODE_STONE_CANNON -> tooltip.add(Text.literal("§a§6Stone Cannon §7(Hold to charge)"));
        }
        super.appendTooltip(stack, context, tooltip, type);
    }
}
