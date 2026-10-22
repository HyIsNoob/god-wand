package net.godwand.item;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.godwand.entity.ArcaneOrbEntity;
import net.godwand.network.CinematicFxPayload;
import net.godwand.skill.RatioCollapseManager;
import net.godwand.skill.ShockwaveSlicer;
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
                        user.sendMessage(Text.literal("§b§l✦ Spatial Path Walker ✦"), true);
                        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                                SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 2.0f, 1.3f);
                    }
                    case MODE_ARCANE_ORB -> {
                        user.sendMessage(Text.literal("§d§l✦ Arcane Singularity ✦"), true);
                        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                                SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 2.0f, 1.4f);
                    }
                    case MODE_RATIO_COLLAPSE -> {
                        user.sendMessage(Text.literal("§6§l✦ Stone Cannon Drill ✦"), true);
                        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                                SoundEvents.BLOCK_HEAVY_CORE_PLACE, SoundCategory.PLAYERS, 2.5f, 0.9f);
                    }
                    case MODE_SUPERNOVA -> {
                        user.sendMessage(Text.literal("§c§l✦ Cosmic Supernova ✦"), true);
                        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                                SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 2.5f, 0.6f);
                        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                                SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 2.5f, 0.5f);
                    }
                    case MODE_STONE_CANNON -> {
                        user.sendMessage(Text.literal("§6§l✦ Aerodynamic Stone Cannon ✦"), true);
                        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                                SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.PLAYERS, 2.5f, 1.2f);
                        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                                SoundEvents.ITEM_ELYTRA_FLYING, SoundCategory.PLAYERS, 2.0f, 1.4f);
                    }
                }
            }
            return TypedActionResult.success(stack);
        }

        // NORMAL RIGHT CLICK: Execute Current Selected Skill
        if (!world.isClient() && user instanceof ServerPlayerEntity serverPlayer) {
            if (currentMode == MODE_ARCANE_ORB && ShockwaveSlicer.hasActiveTask(serverPlayer)) {
                boolean snapped = ShockwaveSlicer.triggerEarlySnap(serverPlayer);
                if (snapped) {
                    serverPlayer.clearActiveItem();
                    user.swingHand(hand, true);
                    serverPlayer.sendMessage(Text.literal("§d§l✦ Singularity Collapsed! ✦"), true);
                    return TypedActionResult.success(stack);
                }
            }
        }

        switch (currentMode) {
            case MODE_SPATIAL_PATH -> {
                if (!world.isClient() && user instanceof ServerPlayerEntity serverPlayer) {
                    SpatialPathWalker.toggle(serverPlayer);
                }
                return TypedActionResult.success(stack);
            }
            case MODE_ARCANE_ORB, MODE_RATIO_COLLAPSE, MODE_SUPERNOVA, MODE_STONE_CANNON -> {
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
                Vec3d muzzle = player.getEyePos().add(look.multiply(0.8));
                if (chargeTicks % 2 == 0) {
                    sw.spawnParticles(ParticleTypes.PORTAL, muzzle.x, muzzle.y, muzzle.z,
                            1, (Math.random() - 0.5) * 0.3, (Math.random() - 0.5) * 0.3, (Math.random() - 0.5) * 0.3, 0.05);
                }

                int level;
                String meter;
                if (chargeTicks >= 45) {
                    level = 3;
                    meter = "§5Level III §8[§5▰▰▰ §eMAX§8]";
                    if (chargeTicks == 45) {
                        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 1.8f, 1.8f);
                    }
                } else if (chargeTicks >= 20) {
                    level = 2;
                    meter = "§dLevel II §8[§d▰▰§8▱]";
                    if (chargeTicks == 20) {
                        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 1.5f, 1.4f);
                    }
                } else {
                    level = 1;
                    meter = "§bLevel I §8[§b▰§8▱▱]";
                }

                player.sendMessage(Text.literal("§d§l✦ Arcane Singularity: " + meter + " ✦"), true);
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
                meter = "§4Level IV §8[§4§l▰▰▰▰ §eMAX§8]";
            } else if (chargeTicks >= 44) {
                level = 3;
                meter = "§cLevel III §8[§c▰▰▰§8▱]";
            } else if (chargeTicks >= 22) {
                level = 2;
                meter = "§6Level II §8[§6▰▰§8▱▱]";
            } else {
                level = 1;
                meter = "§eLevel I §8[§e▰§8▱▱▱]";
            }

            if (!world.isClient() && user instanceof ServerPlayerEntity player) {
                ServerWorld sw = (ServerWorld) world;

                // Level transition sound cue
                if (chargeTicks == 22 || chargeTicks == 44 || chargeTicks == 68) {
                    float pitch = (level == 4) ? 1.8f : (level == 3) ? 1.4f : 1.1f;
                    world.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 2.0f, pitch);
                    world.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.BLOCK_HEAVY_CORE_PLACE, SoundCategory.PLAYERS, 2.0f, pitch);
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

                player.sendMessage(Text.literal("§6§l✦ Stone Cannon Drill: " + meter + " ✦"), true);
            }
            return;
        }

        // SKILL: AERODYNAMIC STONE CANNON
        if (currentMode == MODE_STONE_CANNON) {
            int chargeTicks = this.getMaxUseTime(stack, user) - remainingUseTicks;
            if (chargeTicks < 0) return;

            int level = StoneCannonManager.calculateChargeLevel(chargeTicks);
            String meter = switch (level) {
                case 4 -> "§4Level IV §8[§4§l▰▰▰▰ §eHYPER-SONIC§8]";
                case 3 -> "§cLevel III §8[§c▰▰▰§8▱]";
                case 2 -> "§6Level II §8[§6▰▰§8▱▱]";
                default -> "§eLevel I §8[§e▰§8▱▱▱]";
            };

            if (!world.isClient() && user instanceof ServerPlayerEntity player) {
                StoneCannonManager.tickCharge(player, chargeTicks, level);
                player.sendMessage(Text.literal("§6§l✦ Stone Cannon: " + meter + " ✦"), true);
            }
            return;
        }

        // SKILL: COSMIC SUPERNOVA
        if (currentMode != MODE_SUPERNOVA) return;

        int chargeTicks = this.getMaxUseTime(stack, user) - remainingUseTicks;
        if (chargeTicks < 0) return;

        double progress = Math.min(1.0, (double) chargeTicks / 140.0);

        if (!world.isClient() && user instanceof ServerPlayerEntity player) {
            ServerWorld sw = (ServerWorld) world;

            int level;
            String meter;
            if (chargeTicks >= 120) {
                level = 4;
                meter = "§4Level IV §8[§4§l▰▰▰▰ §eCRITICAL§8]";
            } else if (chargeTicks >= 80) {
                level = 3;
                meter = "§cLevel III §8[§c▰▰▰§8▱]";
            } else if (chargeTicks >= 40) {
                level = 2;
                meter = "§6Level II §8[§6▰▰§8▱▱]";
            } else {
                level = 1;
                meter = "§eLevel I §8[§e▰§8▱▱▱]";
            }

            if (chargeTicks == 40 || chargeTicks == 80 || chargeTicks == 120) {
                float pitch = (level == 4) ? 1.8f : (level == 3) ? 1.3f : 0.9f;
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 2.5f, pitch);
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 2.0f, pitch);
            }

            // 1. Swirling portal ring at feet
            double spinSpeed = 0.2 + progress * 0.8;
            double angle = (chargeTicks * spinSpeed) % (Math.PI * 2.0);
            double ringRadius = 1.6 + (1.0 - progress) * 0.8;
            double px = player.getX() + Math.cos(angle) * ringRadius;
            double pz = player.getZ() + Math.sin(angle) * ringRadius;
            sw.spawnParticles(ParticleTypes.PORTAL, px, player.getY() + 0.15, pz, 2, 0.05, 0.1, 0.05, 0.02);
            sw.spawnParticles(ParticleTypes.ENCHANT, px, player.getY() + 0.25, pz, 1, 0, 0.05, 0, 0.05);

            if (chargeTicks % 2 == 0) {
                sw.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        player.getX() + (Math.random() - 0.5) * 2.0, player.getY() + 0.1, player.getZ() + (Math.random() - 0.5) * 2.0,
                        1, 0, 0.08 + progress * 0.12, 0, 0.01);
            }

            // 2. Camera shake & FOV zoom
            float trauma = (float) (0.05 + progress * 0.30);
            CinematicFxPayload shake = new CinematicFxPayload(
                    CinematicFxPayload.EVENT_TRAUMA_SHAKE,
                    player.getX(), player.getY(), player.getZ(),
                    trauma, 4
            );
            ServerPlayNetworking.send(player, shake);

            if (chargeTicks % 12 == 0) {
                float fovTarget = (float) (1.0 - progress * 0.15);
                CinematicFxPayload focus = new CinematicFxPayload(
                        CinematicFxPayload.EVENT_METEOR_FOCUS,
                        player.getX(), player.getY(), player.getZ(),
                        fovTarget, 16
                );
                ServerPlayNetworking.send(player, focus);
            }

            // 3. Heartbeat & anchor charge audio
            if (chargeTicks % 16 == 0) {
                float pitch = (float) (0.5f + progress * 1.3f);
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 2.5f, pitch);
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 1.8f, pitch);
            }

            // 4. Aim Target indicator
            HitResult hit = player.raycast(45.0, 0.0f, false);
            Vec3d targetPos;
            if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bHit) {
                targetPos = bHit.getBlockPos().toCenterPos();
            } else {
                targetPos = player.getEyePos().add(player.getRotationVec(1.0f).multiply(30.0));
            }
            Vec3d seedPos = targetPos.add(0, 18.0, 0);

            sw.spawnParticles(ParticleTypes.END_ROD, seedPos.x, seedPos.y, seedPos.z, 2, 0.1, 0.1, 0.1, 0.02);
            sw.spawnParticles(ParticleTypes.REVERSE_PORTAL, seedPos.x, seedPos.y, seedPos.z, 4, 0.5 + progress * 1.2, 0.5 + progress * 1.2, 0.5 + progress * 1.2, 0.08);

            player.sendMessage(Text.literal("§c§l✦ Cosmic Supernova: " + meter + " ✦"), true);

            if (chargeTicks >= 140) {
                SupernovaManager.cast(player);
                player.getItemCooldownManager().set(this, 520);
                player.clearActiveItem();
                player.sendMessage(Text.literal("§4§l✦ Cosmic Supernova Unleashed! ✦"), true);
            }
        }
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (getSkillMode(stack) == MODE_SUPERNOVA) {
            int chargeTicks = this.getMaxUseTime(stack, user) - remainingUseTicks;
            if (chargeTicks < 140) {
                if (!world.isClient() && user instanceof PlayerEntity player) {
                    world.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 2.0f, 0.8f);
                    world.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 2.0f, 1.2f);
                    player.sendMessage(Text.literal("§7§o[Cosmic Supernova Cancelled]"), true);
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
                player.getItemCooldownManager().set(this, 35);
            }
            return;
        }

        if (getSkillMode(stack) == MODE_STONE_CANNON) {
            if (!world.isClient() && user instanceof ServerPlayerEntity player) {
                int chargeDuration = this.getMaxUseTime(stack, user) - remainingUseTicks;
                StoneCannonManager.fire(player, chargeDuration);
                player.getItemCooldownManager().set(this, 30);
            }
            return;
        }

        if (getSkillMode(stack) != MODE_ARCANE_ORB) return;

        if (!world.isClient() && user instanceof PlayerEntity player) {
            int chargeDuration = this.getMaxUseTime(stack, user) - remainingUseTicks;
            int level = 1;
            if (chargeDuration >= 45) {
                level = 3;
            } else if (chargeDuration >= 20) {
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
        tooltip.add(Text.literal("§7[Shift + Right-Click]: §eSwitch Skill"));
        switch (mode) {
            case MODE_SPATIAL_PATH -> tooltip.add(Text.literal("§aSelected: §bSpatial Path Walker §7(Toggle)"));
            case MODE_ARCANE_ORB -> tooltip.add(Text.literal("§aSelected: §dArcane Singularity §7(Hold to Charge)"));
            case MODE_RATIO_COLLAPSE -> tooltip.add(Text.literal("§aSelected: §6Stone Cannon Drill §7(Hold to Charge)"));
            case MODE_SUPERNOVA -> tooltip.add(Text.literal("§aSelected: §cCosmic Supernova §7(Hold to Unleash)"));
            case MODE_STONE_CANNON -> tooltip.add(Text.literal("§aSelected: §6Aerodynamic Stone Cannon §7(Hold to Charge & Spin Drill)"));
        }
        tooltip.add(Text.literal("§8--------------------------------"));
        tooltip.add(Text.translatable("item.godwand.god_wand.desc"));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
