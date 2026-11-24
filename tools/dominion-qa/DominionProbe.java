package net.godwand.qa;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.godwand.GodWandMod;
import net.godwand.item.GodWandItem;
import net.godwand.skill.BlockDominionManager;
import net.godwand.skill.BlockDominionRules;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Disposable QA & demo showcase recorder for Block Dominion (mode 9).
 * Auto-creates a clean flat world inside build/dominion-qa/run/saves/Dominion QA,
 * exercises every M1+M2 mechanic, asserts Survival parry integrity, and captures
 * every tick at 1280x720 for ffmpeg MP4 encoding + keyframe visual inspection.
 */
public final class DominionProbe implements ClientModInitializer {
    private static final int OX = 0, OZ = 0;
    private static final String WORLD_NAME = "Dominion QA";

    private boolean launchedWorld;
    private boolean initialized;
    private int stage = 0;
    private int ticks = 0;
    private int stageStart = 0;
    private int frame = 0;
    private int groundY = -60;
    private final List<BlockPos> pickupQueue = new ArrayList<>();
    private int pickupCursor = 0;
    private final List<UUID> spawnedMobs = new ArrayList<>();
    private final List<String> checks = new ArrayList<>();
    private double stairAscendStartY = 0.0;
    private int meteorLandedTick = -1;
    private UUID deflectedArrowId = null;
    private Path framesDir;
    private Path keyframesDir;

    @Override
    public void onInitializeClient() {
        if (!Boolean.getBoolean("godwand.dominion.qa")) {
            throw new IllegalStateException("QA flag required");
        }
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private ServerPlayer me(MinecraftServer server, Minecraft c) {
        return server.getPlayerList().getPlayer(c.player.getUUID());
    }

    private void tick(Minecraft c) {
        c.options.pauseOnLostFocus = false;

        // Auto-create fresh flat world from TitleScreen (also skip first-launch AccessibilityOnboardingScreen)
        if (!launchedWorld && c.screen != null
                && (c.screen instanceof TitleScreen || c.screen.getClass().getSimpleName().contains("Onboarding"))) {
            launchedWorld = true;
            System.out.println("DOMINION_QA creating flat world " + WORLD_NAME + " from " + c.screen.getClass().getSimpleName());
            GameRules rules = new GameRules();
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
            LevelSettings settings = new LevelSettings(
                    WORLD_NAME, GameType.CREATIVE, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT);
            c.createWorldOpenFlows().createFreshLevel(
                    WORLD_NAME, settings, new WorldOptions(170170L, false, false),
                    reg -> reg.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                    c.screen);
            return;
        }

        MinecraftServer server = c.getSingleplayerServer();
        if (c.player == null || c.level == null || server == null) return;
        if (!c.gameDirectory.toPath().toAbsolutePath().normalize().endsWith(Path.of("build", "dominion-qa", "run"))) {
            return;
        }

        ticks++;
        if (!initialized && ticks >= 40 && c.screen == null) {
            setupScene(c, server);
            return;
        }
        if (!initialized) return;

        int t = ticks - stageStart;
        switch (stage) {
            case 1 -> stagePickupAndTiers(c, server, t);
            case 2 -> stageStowAndUnfold(c, server, t);
            case 3 -> stageAirStairsAndWingsFlight(c, server, t);
            case 4 -> stageBrainShieldsAndParry(c, server, t);
            case 5 -> stageRapidAndVolley(c, server, t);
            case 6 -> stageAutoModeAndCounter(c, server, t);
            case 7 -> stageSpiralDropAndFinish(c, server, t);
            default -> {}
        }
        grabFrame(c);
    }

    private void setupScene(Minecraft c, MinecraftServer server) {
        initialized = true;
        c.getWindow().setWindowed(1280, 720);
        c.getWindow().setTitle("God Wand 1.7.0 - Block Dominion Demo");
        c.options.framerateLimit().set(60);
        c.options.renderDistance().set(10);
        c.options.guiScale().set(2);
        c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        c.options.broadcastOptions();

        framesDir = c.gameDirectory.toPath().resolve("screenshots/dominion-frames");
        keyframesDir = c.gameDirectory.toPath().resolve("screenshots/dominion-keyframes");
        try {
            Files.createDirectories(framesDir);
            Files.createDirectories(keyframesDir);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        server.submit(() -> {
            ServerPlayer p = me(server, c);
            ServerLevel level = p.serverLevel();
            level.setDayTime(6000);
            level.setWeatherParameters(60000, 0, false, false);
            groundY = -60;

            // Build clean stone/grass arena + rich mosaic of liftable blocks right around player
            Block[] palette = {
                    Blocks.DEEPSLATE_BRICKS, Blocks.GILDED_BLACKSTONE, Blocks.AMETHYST_BLOCK,
                    Blocks.POLISHED_BLACKSTONE, Blocks.GOLD_BLOCK, Blocks.OBSIDIAN,
                    Blocks.POLISHED_DEEPSLATE, Blocks.MOSSY_STONE_BRICKS
            };
            pickupQueue.clear();
            int idx = 0;
            for (int r = 3; r <= 6; r++) {
                int pts = r * 5;
                for (int i = 0; i < pts && pickupQueue.size() < 48; i++) {
                    double ang = Math.PI * 2.0 * i / pts;
                    int bx = OX + (int) Math.round(Math.cos(ang) * r);
                    int bz = OZ + (int) Math.round(Math.sin(ang) * r);
                    BlockPos pos = new BlockPos(bx, groundY, bz);
                    if (!pickupQueue.contains(pos)) {
                        level.setBlock(pos, palette[idx++ % palette.length].defaultBlockState(), 3);
                        pickupQueue.add(pos);
                    }
                }
            }

            // Give God Wand set to Block Dominion
            ItemStack wand = new ItemStack(GodWandMod.GOD_WAND_ITEM);
            GodWandItem.setSkillMode(wand, GodWandItem.MODE_BLOCK_DOMINION);
            p.getInventory().setItem(0, wand);
            p.getInventory().selected = 0;
            p.getInventory().setItem(40, ItemStack.EMPTY);
            p.setGameMode(GameType.CREATIVE);
            p.connection.teleport(OX + 0.5, groundY, OZ + 0.5, 0f, 8f);
            p.inventoryMenu.broadcastChanges();
        }).join();

        c.player.getInventory().selected = 0;
        c.player.connection.send(new ServerboundSetCarriedItemPacket(0));
        stage = 1;
        stageStart = ticks;
        System.out.println("DOMINION_QA SETUP complete, groundY=" + groundY + " pickupQueue=" + pickupQueue.size());
    }

    /** Stage 1 (108 ticks): Enable Manual, lift 48 blocks into 3-tier armada, then preview crisp 1st-person view! */
    private void stagePickupAndTiers(Minecraft c, MinecraftServer server, int t) {
        if (t == 6) {
            server.submit(() -> BlockDominionManager.cycleMode(me(server, c))).join();
        }
        if (t >= 12 && pickupCursor < pickupQueue.size()) {
            BlockPos target = pickupQueue.get(pickupCursor++);
            server.submit(() -> BlockDominionManager.tryPickUpBlock(me(server, c), target)).join();
        }
        if (t == 24) saveKeyframe(c, "kf_01_orbit_tier1.png");
        if (t == 42) saveKeyframe(c, "kf_02_orbit_tier2.png");
        if (t == 74) {
            int count = server.submit(() -> BlockDominionManager.reserveCount(me(server, c))).join();
            recordCheck("pickup_48_blocks", count >= 44, "count=" + count);
            saveKeyframe(c, "kf_03_orbit_halo.png");
        }
        if (t == 82) {
            // Switch to 1st-person view so owner can verify unobstructed aiming sightline with 48 blocks orbiting!
            c.options.setCameraType(CameraType.FIRST_PERSON);
        }
        if (t == 96) {
            saveKeyframe(c, "kf_03b_first_person_clear.png");
        }
        if (t >= 106) {
            c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            stage = 2;
            stageStart = ticks;
        }
    }

    /** Stage 2 (52 ticks): Switch to Spatial Path (stowed shoulder cluster), then back to Block Dominion (unfold wave). */
    private void stageStowAndUnfold(Minecraft c, MinecraftServer server, int t) {
        if (t == 1) {
            server.submit(() -> GodWandItem.selectSkill(me(server, c), InteractionHand.MAIN_HAND, GodWandItem.MODE_SPATIAL_PATH)).join();
        }
        if (t == 20) saveKeyframe(c, "kf_04_stowed.png");
        if (t == 26) {
            server.submit(() -> GodWandItem.selectSkill(me(server, c), InteractionHand.MAIN_HAND, GodWandItem.MODE_BLOCK_DOMINION)).join();
        }
        if (t == 32) saveKeyframe(c, "kf_05_unfold.png");
        if (t >= 48) {
            stage = 3;
            stageStart = ticks;
        }
    }

    /**
     * Stage 3 (248 ticks):
     * A) Slow walking descending world-anchored Block Stairs off high tower
     * B) Walking ascending sky Block Stairs (W + Space)
     * C) Wing Mode 1: HOVER free levitation + smooth deceleration
     * D) Wing Mode 2: ELYTRA GLIDE + Jet Rocket Boost + 1st-person wing clarity check
     * E) QoL Auto Rain Umbrella Canopy under open rain!
     */
    private void stageAirStairsAndWingsFlight(Minecraft c, MinecraftServer server, int t) {
        final int S3X = OX - 50, S3Z = OZ + 20;
        if (t == 1) {
            c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            server.submit(() -> {
                ServerPlayer p = me(server, c);
                ServerLevel level = p.serverLevel();
                // Build a dramatic 12-block high obsidian/gold overlook platform
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        level.setBlock(new BlockPos(S3X + dx, groundY + 11, S3Z + dz),
                                ((dx + dz) & 1) == 0 ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : Blocks.GILDED_BLACKSTONE.defaultBlockState(), 3);
                    }
                }
                for (int y = groundY; y < groundY + 11; y++) {
                    level.setBlock(new BlockPos(S3X - 2, y, S3Z - 2), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 3);
                    level.setBlock(new BlockPos(S3X + 2, y, S3Z - 2), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 3);
                }
                p.setGameMode(GameType.SURVIVAL);
                p.setHealth(p.getMaxHealth());
                p.connection.teleport(S3X + 0.5, groundY + 12.0, S3Z + 1.5, 0f, 18f);
            }).join();
        }

        // Part A: Walk calmly off the cliff edge (+Z) to trigger slow descending world-anchored Block Stairs!
        if (t >= 6 && t < 46) {
            c.options.keyUp.setDown(true);
            c.options.keyShift.setDown(false);
            c.options.keyJump.setDown(false);
        }
        if (t == 32) {
            boolean stairsOn = server.submit(() -> BlockDominionManager.isAirStairsActive(me(server, c))).join();
            recordCheck("air_stairs_active_midair", stairsOn, "stairs=" + stairsOn);
            recordCheck("custom_glsl_shader_compiled", net.godwand.client.DominionShader.isAvailable(),
                    "available=" + net.godwand.client.DominionShader.isAvailable());
            recordCheck("cinematic_postfx_ready", net.godwand.client.DominionPostFx.ready(),
                    "ready=" + net.godwand.client.DominionPostFx.ready());
            saveKeyframe(c, "kf_05b_stairs_down.png");
        }

        // Part B: Hold W + Space to smoothly walk UP ascending floating sky stairs!
        if (t == 48) {
            stairAscendStartY = c.player.getY();
            c.options.keyUp.setDown(true);
            c.options.keyJump.setDown(true);
        }
        if (t == 78) {
            saveKeyframe(c, "kf_05c_stairs_up.png");
        }
        if (t == 86) {
            double climbed = c.player.getY() - stairAscendStartY;
            recordCheck("air_stairs_ascend_rises", climbed >= 2.2,
                    String.format(Locale.ROOT, "climbed=%.2f", climbed));
            c.options.keyUp.setDown(false);
            c.options.keyJump.setDown(false);
        }

        // Part C: Wing Mode 1 — HOVER (Free 3D Levitation) -> idle ~1.7s -> SOVEREIGN PERCH floating pedestal!
        if (t == 92) {
            server.submit(() -> {
                ServerPlayer p = me(server, c);
                p.connection.teleport(p.getX(), groundY + 16.0, p.getZ(), 0f, 4f);
                BlockDominionManager.setFlightStyle(p, false);
                boolean started = BlockDominionManager.setWingFlight(p, true);
                recordCheck("wing_flight_engaged", started, "started=" + started);
            }).join();
        }
        if (t == 108) {
            saveKeyframe(c, "kf_05d_wings_hover.png");
        }
        if (t == 146) {
            boolean perched = server.submit(() -> BlockDominionManager.isSovereignPerchActive(me(server, c))).join();
            recordCheck("sovereign_perch_active", perched, "perch=" + perched);
            saveKeyframe(c, "kf_05d2_sovereign_perch.png");
        }

        // Part D: Wing Mode 2 — ELYTRA GLIDE + Jet Rocket Boost!
        if (t == 152) {
            server.submit(() -> {
                ServerPlayer p = me(server, c);
                BlockDominionManager.setFlightStyle(p, true);
                p.connection.teleport(p.getX(), groundY + 20.0, p.getZ(), 0f, 12f);
                recordCheck("wing_glide_mode_active", BlockDominionManager.isGlideMode(p),
                        "glide=" + BlockDominionManager.isGlideMode(p));
            }).join();
        }
        // Glide dive smoothly for 16 ticks, then pitch level & fire Jet Rocket Boost!
        if (t == 168) {
            server.submit(() -> {
                ServerPlayer p = me(server, c);
                p.connection.teleport(p.getX(), p.getY(), p.getZ(), 0f, -4f);
            }).join();
            c.options.keySprint.setDown(true);
            c.options.keyJump.setDown(true);
        }
        if (t == 178) {
            // Subtle aerodynamic bank right to showcase Glide camera roll + radial speed blur!
            c.options.keyRight.setDown(true);
        }
        if (t == 186) {
            double spd = c.player.getDeltaMovement().length();
            recordCheck("wing_glide_jet_boost", spd >= 1.30, String.format(Locale.ROOT, "speed=%.2f", spd));
            saveKeyframe(c, "kf_05e_wings_glide_boost.png");
        }
        // Part D2: Steep Vertical Climb UP (verify gimbal-free aerodynamic wings behind climbing back!)
        if (t == 192) {
            c.options.keyRight.setDown(false);
            c.options.keySprint.setDown(true);
            c.options.keyJump.setDown(true);
            server.submit(() -> {
                ServerPlayer p = me(server, c);
                p.connection.teleport(p.getX(), groundY + 14.0, p.getZ(), 0f, -72f);
            }).join();
        }
        if (t == 206) {
            saveKeyframe(c, "kf_05e2_wings_climb_up.png");
        }

        // Part D3: FULL-CHARGE TIER III CATACLYSM Meteor Strike Dive from high altitude (groundY + 94)!
        if (t == 212) {
            meteorLandedTick = -1;
            net.godwand.client.BlockDominionInput.reset();
            c.options.keySprint.setDown(true);
            c.options.keyJump.setDown(true);
            server.submit(() -> {
                ServerPlayer p = me(server, c);
                ServerLevel level = p.serverLevel();
                double mx = S3X - 32.5, mz = S3Z + 4.5;
                BlockDominionManager.setWingFlight(p, false);
                p.setDeltaMovement(Vec3.ZERO);
                p.connection.teleport(mx, groundY + 94.0, mz, 0f, 85f);
                BlockDominionManager.setFlightStyle(p, true);
                BlockDominionManager.setWingFlight(p, true);
                for (int k = -2; k <= 2; k++) {
                    Mob h = EntityType.HUSK.create(level);
                    if (h != null) {
                        h.setPos(mx + k * 2.4, groundY, mz + 18.0);
                        h.setNoAi(true);
                        level.addFreshEntity(h);
                    }
                }
            }).join();
        }
        if (t == 254) {
            int tier = server.submit(() -> BlockDominionManager.meteorDiveTier(me(server, c))).join();
            boolean diving = server.submit(() -> BlockDominionManager.isMeteorDiving(me(server, c))).join();
            recordCheck("meteor_dive_ignited", diving && tier >= 3, "diving=" + diving + " tier=" + tier);
            saveKeyframe(c, "kf_05e3_meteor_dive.png");
        }
        if (meteorLandedTick < 0 && t >= 235 && t <= 278) {
            boolean landedNow = server.submit(() -> !BlockDominionManager.isWingFlying(me(server, c))).join();
            if (landedNow) {
                meteorLandedTick = t;
            }
        }
        if (meteorLandedTick >= 0 && t == meteorLandedTick + 3) {
            saveKeyframe(c, "kf_05e4_meteor_strike_impact.png");
        }
        if (meteorLandedTick >= 0 && t == meteorLandedTick + 10) {
            saveKeyframe(c, "kf_05e5_meteor_crater_aftermath.png");
        }
        if (t == 280) {
            c.options.keySprint.setDown(false);
            c.options.keyJump.setDown(false);
            if (meteorLandedTick < 0) {
                saveKeyframe(c, "kf_05e4_meteor_strike_impact.png");
            }
            server.submit(() -> {
                ServerPlayer p = me(server, c);
                boolean landed = !BlockDominionManager.isWingFlying(p);
                float hp = p.getHealth();
                int remBlocks = BlockDominionManager.reserveCount(p);
                recordCheck("meteor_strike_safe_hp", landed && hp >= 19.9f,
                        String.format(Locale.ROOT, "landed=%s hp=%.1f", landed, hp));
                recordCheck("meteor_strike_consumes_blocks", remBlocks == 0, "remaining=" + remBlocks);
                // Clean up any surviving husks so Rain Umbrella scene stays completely threat-free!
                var box = new net.minecraft.world.phys.AABB(S3X - 54, groundY - 8, S3Z - 6, S3X - 10, groundY + 18, S3Z + 48);
                int fullHpSurvivors = 0;
                for (Mob mob : p.serverLevel().getEntitiesOfClass(Mob.class, box)) {
                    if (mob.isAlive() && mob.getHealth() >= mob.getMaxHealth() - 0.1f) fullHpSurvivors++;
                    mob.discard();
                }
                int affected = Math.max(0, 5 - fullHpSurvivors);
                recordCheck("meteor_strike_aoe_hits", affected >= 3,
                        String.format(Locale.ROOT, "affected=%d landZ=%.1f targetZ=%.1f", affected, p.getZ(), S3Z + 22.5));
                // Refill reserve cleanly after Tier III Cataclysm emptied all 48 blocks so subsequent QA stages run full!
                ServerLevel level = p.serverLevel();
                Block[] refill = {Blocks.DEEPSLATE_BRICKS, Blocks.GILDED_BLACKSTONE, Blocks.AMETHYST_BLOCK, Blocks.GOLD_BLOCK, Blocks.OBSIDIAN};
                int rx = S3X + 20, rz = S3Z + 20;
                p.connection.teleport(rx + 0.5, groundY, rz + 0.5, 0f, 0f);
                for (int i = 0; i < 48; i++) {
                    BlockPos bp = new BlockPos(rx - 3 + (i % 8), groundY + 1 + (i / 24), rz - 2 + ((i / 8) % 3));
                    level.setBlock(bp, refill[i % refill.length].defaultBlockState(), 3);
                    BlockDominionManager.tryPickUpBlock(p, bp);
                }
            }).join();
        }

        // Brief 1st-person wing glide preview so owner sees unobstructed sightline while flying!
        if (t == 286) {
            server.submit(() -> {
                ServerPlayer p = me(server, c);
                p.connection.teleport(S3X + 0.5, groundY + 18.0, S3Z + 22.5, 0f, 0f);
                BlockDominionManager.setFlightStyle(p, true);
                BlockDominionManager.setWingFlight(p, true);
            }).join();
            c.options.setCameraType(CameraType.FIRST_PERSON);
        }
        if (t == 296) {
            saveKeyframe(c, "kf_05f_wings_first_person.png");
        }
        if (t == 302) {
            c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            server.submit(() -> {
                ServerPlayer p = me(server, c);
                BlockDominionManager.setWingFlight(p, false);
                float hp = p.getHealth();
                recordCheck("wing_flight_safe_hp", hp >= 19.9f, String.format(Locale.ROOT, "hp=%.1f", hp));
            }).join();
        }

        // Part E: QoL Auto Rain Umbrella Canopy under rain (30 ticks so rainLevel > 0.20 deterministically)!
        if (t == 304) {
            server.submit(() -> {
                ServerPlayer p = me(server, c);
                ServerLevel level = p.serverLevel();
                p.connection.teleport(S3X + 0.5, groundY, S3Z + 30.5, 0f, 6f);
                level.setWeatherParameters(0, 200, true, false);
            }).join();
        }
        if (t == 334) {
            boolean umbrella = server.submit(() -> BlockDominionManager.isUmbrellaActive(me(server, c))).join();
            recordCheck("rain_umbrella_active", umbrella, "umbrella=" + umbrella);
            saveKeyframe(c, "kf_05g_rain_umbrella.png");
        }
        if (t == 340) {
            // Part F: QoL Liquid Stride across glowing Lava Pool in Survival!
            server.submit(() -> {
                ServerPlayer p = me(server, c);
                ServerLevel level = p.serverLevel();
                level.setWeatherParameters(60000, 0, false, false);
                for (int dx = -4; dx <= 4; dx++) {
                    for (int dz = 0; dz <= 10; dz++) {
                        level.setBlock(new BlockPos(S3X + dx, groundY - 1, S3Z + 42 + dz), Blocks.LAVA.defaultBlockState(), 3);
                        level.setBlock(new BlockPos(S3X + dx, groundY, S3Z + 42 + dz), Blocks.AIR.defaultBlockState(), 3);
                    }
                }
                p.connection.teleport(S3X + 0.5, groundY + 0.02, S3Z + 42.5, 0f, 16f);
            }).join();
            c.options.keyUp.setDown(true);
            c.options.keyShift.setDown(false);
        }
        if (t == 362) {
            boolean stride = server.submit(() -> BlockDominionManager.isLiquidStrideActive(me(server, c))).join();
            float hp = server.submit(() -> me(server, c).getHealth()).join();
            recordCheck("liquid_stride_lava_safe", stride && hp >= 19.9f,
                    String.format(Locale.ROOT, "stride=%s hp=%.1f", stride, hp));
            saveKeyframe(c, "kf_05h_liquid_stride_lava.png");
        }
        if (t == 370) {
            c.options.keyUp.setDown(false);
        }
        if (t >= 376) {
            stage = 4;
            stageStart = ticks;
        }
    }

    /** Stage 4 (110 ticks): Survival mode! Single shield + arrow parry -> Dual shields -> 360 Dome + TNT parry! */
    private void stageBrainShieldsAndParry(Minecraft c, MinecraftServer server, int t) {
        if (t == 1) {
            c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            server.submit(() -> {
                ServerPlayer p = me(server, c);
                ServerLevel level = p.serverLevel();
                p.setGameMode(GameType.SURVIVAL);
                p.setHealth(p.getMaxHealth());
                p.connection.teleport(OX + 0.5, groundY, OZ + 0.5, 0f, 10f);
                // Threat 1: Skeleton ahead (+Z)
                Mob skel = EntityType.SKELETON.create(level);
                skel.setPos(OX + 0.5, groundY, OZ + 8.5);
                skel.setTarget(p);
                skel.setPersistenceRequired();
                level.addFreshEntity(skel);
                spawnedMobs.add(skel.getUUID());
            }).join();
        }
        if (t == 16) saveKeyframe(c, "kf_06_shield_single.png");
        // Launch real hostile arrow straight at player to trigger Shield Kinetic Deflect!
        if (t == 20) {
            server.submit(() -> {
                ServerPlayer p = me(server, c);
                ServerLevel level = p.serverLevel();
                Entity shooter = spawnedMobs.isEmpty() ? null : level.getEntity(spawnedMobs.get(0));
                Arrow arrow = shooter instanceof LivingEntity l
                        ? new Arrow(level, l, new ItemStack(net.minecraft.world.item.Items.ARROW), null)
                        : new Arrow(EntityType.ARROW, level);
                arrow.setPos(OX + 0.5, groundY + 1.4, OZ + 7.2);
                arrow.setDeltaMovement(0, 0.02, -1.65);
                level.addFreshEntity(arrow);
                deflectedArrowId = arrow.getUUID();
            }).join();
        }
        if (t == 26) {
            boolean reversed = server.submit(() -> {
                ServerLevel level = me(server, c).serverLevel();
                Entity e = deflectedArrowId == null ? null : level.getEntity(deflectedArrowId);
                // Either arrow is actively flying back (+Z) or already hit & hurt the Skeleton!
                Entity skel = spawnedMobs.isEmpty() ? null : level.getEntity(spawnedMobs.get(0));
                boolean skelHit = skel instanceof LivingEntity l && l.getHealth() < l.getMaxHealth();
                return (e != null && e.getDeltaMovement().z > 0.5) || skelHit;
            }).join();
            recordCheck("kinetic_deflect_reverses_arrow", reversed, "reversed=" + reversed);
            saveKeyframe(c, "kf_07_parry_arrow.png");
        }
        if (t == 34) {
            float hp = server.submit(() -> me(server, c).getHealth()).join();
            recordCheck("survival_arrow_parry_zero_damage", hp >= 19.9f, "hp=" + hp);
            // Threat 2: Husk on opposite side (-X) -> triggers Dual Shield
            server.submit(() -> {
                ServerPlayer p = me(server, c);
                ServerLevel level = p.serverLevel();
                Mob husk = EntityType.HUSK.create(level);
                husk.setPos(OX - 4.5, groundY, OZ - 1.5);
                husk.setTarget(p);
                husk.setPersistenceRequired();
                level.addFreshEntity(husk);
                spawnedMobs.add(husk.getUUID());
            }).join();
        }
        if (t == 50) saveKeyframe(c, "kf_08_shield_multi.png");
        // Threat 3: Primed TNT nearby -> snaps into 360 Dome and absorbs blast!
        if (t == 58) {
            server.submit(() -> {
                ServerPlayer p = me(server, c);
                ServerLevel level = p.serverLevel();
                PrimedTnt tnt = new PrimedTnt(level, OX + 3.2, groundY + 0.5, OZ + 2.2, null);
                tnt.setFuse(28);
                level.addFreshEntity(tnt);
            }).join();
        }
        if (t == 72) saveKeyframe(c, "kf_09_shield_dome.png");
        if (t == 96) {
            float hp = server.submit(() -> me(server, c).getHealth()).join();
            recordCheck("survival_tnt_dome_parry_zero_damage", hp >= 19.9f, "hp=" + hp);
            // Clean up mobs before offensive showcase
            server.submit(() -> {
                ServerLevel level = me(server, c).serverLevel();
                for (UUID id : spawnedMobs) {
                    Entity e = level.getEntity(id);
                    if (e != null) e.discard();
                }
                spawnedMobs.clear();
            }).join();
        }
        if (t >= 110) {
            stage = 5;
            stageStart = ticks;
        }
    }

    /** Stage 5 (115 ticks): Clean arena -> Rapid fire + Myriad Volley (Shift + Right-Click fan -> impact frame + center-out barrage!). */
    private void stageRapidAndVolley(Minecraft c, MinecraftServer server, int t) {
        final int S4Z = OZ + 50;
        if (t == 1) {
            server.submit(() -> {
                ServerPlayer p = me(server, c);
                ServerLevel level = p.serverLevel();
                p.setGameMode(GameType.CREATIVE);
                // Stand closer (8.5m away) for front-row Monolith Cage + Impaling Stone Lances showcase!
                p.connection.teleport(OX + 0.5, groundY, S4Z + 6.5, 0f, -6f);
                // Build dramatic target fortress wall + 5 Husks at S4Z + 18 on clean flat grass
                for (int dx = -7; dx <= 7; dx++) {
                    for (int dy = 0; dy < 6; dy++) {
                        BlockState bs = ((dx + dy) & 1) == 0
                                ? Blocks.STONE_BRICKS.defaultBlockState()
                                : Blocks.DEEPSLATE_TILES.defaultBlockState();
                        level.setBlock(new BlockPos(OX + dx, groundY + dy, S4Z + 18), bs, 3);
                    }
                }
                for (int i = -2; i <= 2; i++) {
                    Mob m = EntityType.HUSK.create(level);
                    m.setPos(OX + 0.5 + i * 2.2, groundY, S4Z + 15.0);
                    m.setNoAi(true);
                    m.setPersistenceRequired();
                    level.addFreshEntity(m);
                    spawnedMobs.add(m.getUUID());
                }
            }).join();
        }
        // Full 3-Phase Monolith Cage Showcase: Seal -> 4 Stone Lances Forge -> Impale Execution -> Impaled Monument!
        if (t == 4) {
            server.submit(() -> {
                ServerPlayer p = me(server, c);
                ServerLevel level = p.serverLevel();
                Entity midHusk = spawnedMobs.size() > 2 ? level.getEntity(spawnedMobs.get(2)) : null;
                boolean caged = midHusk != null && BlockDominionManager.triggerMonolithCage(p, midHusk.getId());
                recordCheck("monolith_cage_seals_target", caged && BlockDominionManager.hasActiveCage(p),
                        "caged=" + caged);
            }).join();
        }
        if (t == 14) saveKeyframe(c, "kf_09b_monolith_cage.png");
        if (t == 34) saveKeyframe(c, "kf_09c_cage_spears_charge.png");
        if (t == 44) saveKeyframe(c, "kf_09d_cage_impale_strike.png");
        if (t == 58) {
            boolean impaledKill = server.submit(() -> {
                ServerLevel level = me(server, c).serverLevel();
                Entity midHusk = spawnedMobs.size() > 2 ? level.getEntity(spawnedMobs.get(2)) : null;
                return midHusk == null || !midHusk.isAlive();
            }).join();
            recordCheck("monolith_cage_impale_kills", impaledKill, "killed=" + impaledKill);
            saveKeyframe(c, "kf_09e_cage_impaled_monument.png");
        }
        // Step back slightly for Rapid Fire (Railgun Muzzle Tracers!) & Myriad Volley
        if (t == 64) {
            server.submit(() -> {
                me(server, c).connection.teleport(OX + 0.5, groundY, S4Z + 0.5, 0f, -4f);
            }).join();
        }
        // Rapid fire burst (t = 68..84)
        if (t == 68) {
            c.options.keyShift.setDown(false);
            c.options.keyUse.setDown(true);
        }
        if (t == 76) saveKeyframe(c, "kf_10_rapid_fire.png");
        if (t == 84) {
            c.options.keyUse.setDown(false);
        }
        // Hold Shift 4 ticks BEFORE right-click so server sees sneaking cleanly -> triggers Myriad Volley fan!
        if (t == 92) {
            c.options.keyShift.setDown(true);
        }
        if (t == 96) {
            c.options.keyUse.setDown(true);
        }
        if (t == 118) saveKeyframe(c, "kf_11_volley_fan.png");
        if (t == 122) {
            c.options.keyUse.setDown(false);
            c.options.keyShift.setDown(false);
        }
        if (t == 124) saveKeyframe(c, "kf_12a_volley_impact_frame.png");
        if (t == 130) saveKeyframe(c, "kf_12_volley_launch.png");
        if (t == 144) {
            int rem = server.submit(() -> BlockDominionManager.reserveCount(me(server, c))).join();
            recordCheck("myriad_volley_empties_reserve", rem == 0, "remaining=" + rem);
            saveKeyframe(c, "kf_13_volley_impact.png");
        }
        // Milestone 4 Showcase: Earthen Surge on Amethyst field -> Royal Violet Material Resonance -> Low-Alt Ground Wash Wings -> Aerial Orbital Meteor Barrage!
        final int S4X = OX + 65;
        if (t == 158) {
            server.submit(() -> {
                ServerPlayer p = me(server, c);
                ServerLevel level = p.serverLevel();
                for (UUID id : spawnedMobs) {
                    Entity e = level.getEntity(id);
                    if (e != null) e.discard();
                }
                spawnedMobs.clear();
                // Lay an Amethyst + Purpur courtyard around (S4X, S4Z) so Earthen Surge triggers AMETHYST_ROYAL resonance!
                for (int dx = -7; dx <= 7; dx++) {
                    for (int dz = -7; dz <= 7; dz++) {
                        int h2 = dx * dx + dz * dz;
                        if (h2 < 4 || h2 > 49) continue;
                        BlockState bs = ((dx + dz) & 1) == 0
                                ? Blocks.AMETHYST_BLOCK.defaultBlockState()
                                : Blocks.PURPUR_BLOCK.defaultBlockState();
                        level.setBlock(new BlockPos(S4X + dx, groundY, S4Z + dz), bs, 3);
                    }
                }
                // Target pillar at S4Z + 18 for Aerial Orbital Meteor Barrage
                for (int dx = -4; dx <= 4; dx++) {
                    for (int dy = 0; dy < 5; dy++) {
                        level.setBlock(new BlockPos(S4X + dx, groundY + dy, S4Z + 18), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 3);
                    }
                }
                p.connection.teleport(S4X + 0.5, groundY, S4Z + 0.5, 0f, 10f);
            }).join();
        }
        if (t == 166) {
            int surged = server.submit(() -> BlockDominionManager.triggerEarthenSurge(me(server, c))).join();
            recordCheck("earthen_surge_rips_blocks", surged >= 20, "surged=" + surged);
        }
        if (t == 176) saveKeyframe(c, "kf_13b_earthen_surge_amethyst.png");
        if (t == 184) {
            var pal = net.godwand.client.BlockDominionRenderer.localPalette();
            recordCheck("material_resonance_amethyst", pal == net.godwand.skill.BlockDominionPalette.AMETHYST_ROYAL, "palette=" + pal);
            // Low-altitude wing flight (3 blocks off ground) to showcase Royal Violet Wings + Ground Wash!
            server.submit(() -> {
                ServerPlayer p = me(server, c);
                BlockDominionManager.setFlightStyle(p, false);
                p.connection.teleport(S4X + 0.5, groundY + 3.2, S4Z + 0.5, 0f, 12f);
                BlockDominionManager.setWingFlight(p, true);
            }).join();
        }
        if (t == 200) saveKeyframe(c, "kf_13c_amethyst_wings_groundwash.png");
        // Rise higher and unleash Aerial Orbital Meteor Barrage (Shift + Right-Click while wing-flying)!
        if (t == 206) {
            server.submit(() -> {
                ServerPlayer p = me(server, c);
                net.godwand.skill.SkillCooldowns.reset(p);
                p.connection.teleport(S4X + 0.5, groundY + 12.0, S4Z + 0.5, 0f, 26f);
            }).join();
            c.options.keyShift.setDown(true);
        }
        if (t == 209) {
            c.options.keyUse.setDown(true);
        }
        if (t == 228) {
            boolean stillFlying = server.submit(() -> BlockDominionManager.isWingFlying(me(server, c))).join();
            recordCheck("orbital_meteor_hovers_midair", stillFlying, "flying=" + stillFlying);
            saveKeyframe(c, "kf_13d_orbital_meteor_charge.png");
        }
        if (t == 232) {
            c.options.keyUse.setDown(false);
            c.options.keyShift.setDown(false);
        }
        if (t == 234) saveKeyframe(c, "kf_13e_orbital_meteor_rain.png");
        if (t >= 258) {
            server.submit(() -> BlockDominionManager.setWingFlight(me(server, c), false)).join();
            stage = 6;
            stageStart = ticks;
        }
    }

    /** Stage 6 (62 ticks): Auto Mode gravity field + Survival auto-parry & auto-counter launch! */
    private void stageAutoModeAndCounter(Minecraft c, MinecraftServer server, int t) {
        if (t == 1) {
            server.submit(() -> {
                ServerPlayer p = me(server, c);
                ServerLevel level = p.serverLevel();
                // Teleport to clean spot for Auto Mode field showcase
                p.connection.teleport(OX + 30.5, groundY, OZ + 0.5, 0f, 12f);
                // Place decorative stone patches around feet for Auto pebbles/pull
                for (int dx = -6; dx <= 6; dx += 2) {
                    for (int dz = -6; dz <= 6; dz += 2) {
                        if (dx == 0 && dz == 0) continue;
                        level.setBlock(new BlockPos(OX + 30 + dx, groundY - 1, OZ + dz),
                                ((dx + dz) & 2) == 0 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState(), 3);
                    }
                }
                // Cycle MANUAL -> AUTO
                BlockDominionManager.cycleMode(p);
                p.setGameMode(GameType.SURVIVAL);
                p.setHealth(p.getMaxHealth());
            }).join();
        }
        if (t == 18) saveKeyframe(c, "kf_14_auto_gravity_field.png");
        if (t == 24) {
            // Spawn attacker and deal real melee damage -> Auto pulls a block, parries with 0 dmg, and counter-launches!
            server.submit(() -> {
                ServerPlayer p = me(server, c);
                ServerLevel level = p.serverLevel();
                Mob husk = EntityType.HUSK.create(level);
                husk.setPos(OX + 30.5, groundY, OZ + 7.5);
                husk.setTarget(p);
                husk.setPersistenceRequired();
                level.addFreshEntity(husk);
                spawnedMobs.add(husk.getUUID());
                p.hurt(level.damageSources().mobAttack(husk), 6.0f);
            }).join();
        }
        if (t == 29) saveKeyframe(c, "kf_15_auto_counter.png");
        if (t == 46) {
            float hp = server.submit(() -> me(server, c).getHealth()).join();
            recordCheck("auto_mode_parry_zero_damage", hp >= 19.9f, "hp=" + hp);
        }
        if (t >= 62) {
            stage = 7;
            stageStart = ticks;
        }
    }

    /** Stage 7 (55 ticks): Lift 10 blocks, deactivate to Off -> spiral drop, then finish cleanly. */
    private void stageSpiralDropAndFinish(Minecraft c, MinecraftServer server, int t) {
        if (t == 1) {
            server.submit(() -> {
                ServerPlayer p = me(server, c);
                ServerLevel level = p.serverLevel();
                p.setGameMode(GameType.CREATIVE);
                p.connection.teleport(OX - 30.5, groundY, OZ + 0.5, 0f, 18f);
                // Cycle AUTO -> OFF -> MANUAL
                BlockDominionManager.cycleMode(p);
                BlockDominionManager.cycleMode(p);
                for (int i = 0; i < 10; i++) {
                    BlockPos pos = new BlockPos(OX - 30 + (i - 5), groundY, OZ + 4);
                    level.setBlock(pos, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
                    BlockDominionManager.tryPickUpBlock(p, pos);
                }
            }).join();
        }
        if (t == 14) {
            // Turn Off -> spiral drop!
            server.submit(() -> {
                ServerPlayer p = me(server, c);
                BlockDominionManager.cycleMode(p); // MANUAL -> AUTO (drops spiral!)
                BlockDominionManager.cycleMode(p); // AUTO -> OFF
            }).join();
        }
        if (t == 24) saveKeyframe(c, "kf_16_spiral_drop.png");
        if (t == 42) {
            // Re-enable within 30s -> verify recall lifts blocks back!
            server.submit(() -> BlockDominionManager.cycleMode(me(server, c))).join();
            int recalled = server.submit(() -> BlockDominionManager.reserveCount(me(server, c))).join();
            recordCheck("recall_within_30s", recalled >= 6, "recalled=" + recalled);
            saveKeyframe(c, "kf_17_recalled.png");
        }
        if (t >= 54) {
            System.out.println("DOMINION_QA SUMMARY frames=" + frame);
            for (String line : checks) System.out.println("DOMINION_QA CHECK " + line);
            System.out.println("DOMINION_QA DONE");
            c.stop();
        }
    }

    private void recordCheck(String name, boolean pass, String detail) {
        String msg = (pass ? "PASS " : "FAIL ") + name + " (" + detail + ")";
        checks.add(msg);
        System.out.println("DOMINION_QA " + msg);
    }

    private void grabFrame(Minecraft c) {
        String name = String.format(Locale.ROOT, "dominion-frames/f-%04d.png", frame++);
        Screenshot.grab(c.gameDirectory, name, c.getMainRenderTarget(), m -> {});
    }

    private void saveKeyframe(Minecraft c, String filename) {
        Screenshot.grab(c.gameDirectory, "dominion-keyframes/" + filename, c.getMainRenderTarget(), m -> {});
    }
}
