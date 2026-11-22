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
            case 3 -> stageBrainShieldsAndParry(c, server, t);
            case 4 -> stageRapidAndVolley(c, server, t);
            case 5 -> stageAutoModeAndCounter(c, server, t);
            case 6 -> stageSpiralDropAndFinish(c, server, t);
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
            for (int r = 3; r <= 5; r++) {
                int pts = r * 4;
                for (int i = 0; i < pts && pickupQueue.size() < 28; i++) {
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

    /** Stage 1 (86 ticks): Enable Manual, lift 28 blocks into 3 orbital tiers (ring -> twin rings -> sovereign halo). */
    private void stagePickupAndTiers(Minecraft c, MinecraftServer server, int t) {
        if (t == 6) {
            server.submit(() -> BlockDominionManager.cycleMode(me(server, c))).join();
        }
        if (t >= 12 && t % 2 == 0 && pickupCursor < pickupQueue.size()) {
            BlockPos target = pickupQueue.get(pickupCursor++);
            server.submit(() -> BlockDominionManager.tryPickUpBlock(me(server, c), target)).join();
        }
        if (t == 30) saveKeyframe(c, "kf_01_orbit_tier1.png");
        if (t == 54) saveKeyframe(c, "kf_02_orbit_tier2.png");
        if (t == 76) {
            int count = server.submit(() -> BlockDominionManager.reserveCount(me(server, c))).join();
            recordCheck("pickup_28_blocks", count == 28, "count=" + count);
            saveKeyframe(c, "kf_03_orbit_halo.png");
        }
        if (t >= 86) {
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
        if (t >= 52) {
            stage = 3;
            stageStart = ticks;
        }
    }

    /** Stage 3 (110 ticks): Survival mode! Single shield + arrow parry -> Dual shields -> 360 Dome + TNT parry! */
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
        // Launch real hostile arrow straight at player to trigger Parry!
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
            }).join();
        }
        if (t == 26) saveKeyframe(c, "kf_07_parry_arrow.png");
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
            stage = 4;
            stageStart = ticks;
        }
    }

    /** Stage 4 (115 ticks): Clean arena -> Rapid fire + Myriad Volley (Shift + Right-Click fan -> center-out barrage!). */
    private void stageRapidAndVolley(Minecraft c, MinecraftServer server, int t) {
        final int S4Z = OZ + 50;
        if (t == 1) {
            server.submit(() -> {
                ServerPlayer p = me(server, c);
                ServerLevel level = p.serverLevel();
                p.setGameMode(GameType.CREATIVE);
                p.connection.teleport(OX + 0.5, groundY, S4Z + 0.5, 0f, -4f);
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
                    m.setPersistenceRequired();
                    level.addFreshEntity(m);
                    spawnedMobs.add(m.getUUID());
                }
            }).join();
        }
        // Rapid fire burst (t = 12..30)
        if (t == 12) {
            c.options.keyShift.setDown(false);
            c.options.keyUse.setDown(true);
        }
        if (t == 22) saveKeyframe(c, "kf_10_rapid_fire.png");
        if (t == 30) {
            c.options.keyUse.setDown(false);
        }
        // Hold Shift 4 ticks BEFORE right-click so server sees sneaking cleanly -> triggers Myriad Volley fan!
        if (t == 38) {
            c.options.keyShift.setDown(true);
        }
        if (t == 42) {
            c.options.keyUse.setDown(true);
        }
        if (t == 64) saveKeyframe(c, "kf_11_volley_fan.png");
        if (t == 68) {
            c.options.keyUse.setDown(false);
            c.options.keyShift.setDown(false);
        }
        if (t == 76) saveKeyframe(c, "kf_12_volley_launch.png");
        if (t == 90) {
            int rem = server.submit(() -> BlockDominionManager.reserveCount(me(server, c))).join();
            recordCheck("myriad_volley_empties_reserve", rem == 0, "remaining=" + rem);
            saveKeyframe(c, "kf_13_volley_impact.png");
        }
        if (t >= 112) {
            server.submit(() -> {
                ServerLevel level = me(server, c).serverLevel();
                for (UUID id : spawnedMobs) {
                    Entity e = level.getEntity(id);
                    if (e != null) e.discard();
                }
                spawnedMobs.clear();
            }).join();
            stage = 5;
            stageStart = ticks;
        }
    }

    /** Stage 5 (62 ticks): Auto Mode gravity field + Survival auto-parry & auto-counter launch! */
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
            stage = 6;
            stageStart = ticks;
        }
    }

    /** Stage 6 (55 ticks): Lift 10 blocks, deactivate to Off -> spiral drop, then finish cleanly. */
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
