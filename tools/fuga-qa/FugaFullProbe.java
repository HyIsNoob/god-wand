package net.godwand.qa;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.godwand.GodWandMod;
import net.godwand.client.FugaCinematic;
import net.godwand.client.FugaRenderer;
import net.godwand.config.GodWandConfig;
import net.godwand.item.GodWandItem;
import net.godwand.skill.FugaManager;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.util.*;

/** Development-only: one complete cast, captured every client tick from the real render target. */
public final class FugaFullProbe implements ClientModInitializer {
    private int ticks, stage, since, frame, fpsMin = Integer.MAX_VALUE;
    private long fpsSum, fpsCount, mspt, impactMspt;
    private boolean released;
    private Vec3 anchor, impact = Vec3.ZERO;
    private final String run = Long.toString(System.currentTimeMillis());
    private final Set<String> marks = new HashSet<>();
    private final Map<String, Integer> phaseMin = new TreeMap<>();

    public void onInitializeClient() {
        if (!Boolean.getBoolean("godwand.fuga.qa")) throw new IllegalStateException("QA flag required");
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    private void tick(Minecraft c) {
        var server = c.getSingleplayerServer();
        if (c.player == null || c.level == null || server == null
                || !c.gameDirectory.toPath().toAbsolutePath().normalize().endsWith(Path.of("build", "fuga-qa", "run"))) return;
        c.options.pauseOnLostFocus = false;
        if (Files.exists(c.gameDirectory.toPath().getParent().resolve("stop"))) { c.stop(); return; }
        ticks++;
        if (stage == 0 && ticks > 80 && c.screen == null) {
            c.getWindow().setWindowed(1280, 720);
            c.getWindow().setTitle("God Wand 1.4.0 - Fuga full QA");
            c.options.framerateLimit().set(60); c.options.renderDistance().set(16);
            c.options.guiScale().set(2); c.options.setCameraType(CameraType.FIRST_PERSON); c.options.broadcastOptions();
            GodWandConfig.CLIENT.fugaCutscene = true;
            server.submit(() -> {
                var p = server.getPlayerList().getPlayer(c.player.getUUID());
                var level = p.serverLevel();
                p.setGameMode(GameType.CREATIVE);
                GodWandConfig.SERVER.fugaTerrainRadius = new GodWandConfig.Server().fugaTerrainRadius;
                level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false, server);
                level.setDayTime(Long.getLong("godwand.fuga.time", 13000)); level.setWeatherParameters(60000, 0, false, false);
                int ground = level.getHeight(Heightmap.Types.WORLD_SURFACE, 0, 0);
                // A raised field around the impact gives the crater real depth on a flat test world.
                for (int x = -60; x <= 60; x++) for (int z = 100; z <= 220; z++) {
                    int top = ground;
                    for (int y = ground - 30; y < top; y++)
                        level.setBlock(new BlockPos(x, y, z), y == top - 1 ? Blocks.GRASS_BLOCK.defaultBlockState()
                                : y > top - 4 ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState(), 2);
                }
                var wand = new ItemStack(GodWandMod.GOD_WAND_ITEM); GodWandItem.setSkillMode(wand, 6);
                p.getInventory().setItem(0, wand); p.getInventory().selected = 0;
                p.getInventory().setItem(40, ItemStack.EMPTY);
                p.getCooldowns().removeCooldown(GodWandMod.GOD_WAND_ITEM);
                p.connection.teleport(.5, ground, .5, 0, 0); p.inventoryMenu.broadcastChanges();
            }).join();
            c.player.getInventory().selected = 0; c.player.connection.send(new ServerboundSetCarriedItemPacket(0));
            try { Files.createDirectories(c.gameDirectory.toPath().resolve("screenshots/fuga-full-" + run)); }
            catch (java.io.IOException e) { throw new RuntimeException(e); }
            stage = 1; since = ticks;
            System.out.println("FUGA_FULL SETUP run=" + run + " Java=" + System.getProperty("java.version"));
        }
        if (stage == 1 && ticks - since > 80 && GodWandItem.getSkillMode(c.player.getMainHandItem()) == 6) {
            stage = 2; since = ticks; anchor = c.player.position();
            c.player.setYRot(0); c.player.setXRot(0); c.options.keyUse.setDown(true);
            c.gameMode.useItem(c.player, InteractionHand.MAIN_HAND);
            System.out.println("FUGA_FULL START");
        }
        if (stage == 3) {
            // Aftermath overview: high above the crater looking down, then an oblique view.
            if (ticks - since == 230) server.submit(() -> {
                var level = server.getPlayerList().getPlayer(c.player.getUUID()).serverLevel();
                StringBuilder radii = new StringBuilder();
                for (int k = 0; k < 8; k++) {
                    double a = k * Math.PI / 4; int r = 0;
                    while (r < 80 && level.getBlockState(BlockPos.containing(impact.x + Math.cos(a) * r, impact.y - 3, impact.z + Math.sin(a) * r)).isAir()) r++;
                    radii.append(r).append(' ');
                }
                int depth = 0; while (depth < 80 && level.getBlockState(BlockPos.containing(impact.x, impact.y - 1 - depth, impact.z)).isAir()) depth++;
                System.out.println("FUGA_FULL CRATER center=" + impact + " radii(y-3)=" + radii + "depth=" + depth + " impactMsptPeak=" + impactMspt / 1e6);
            }).join();
            if (ticks - since == 235 || ticks - since == 255) {
                boolean top = ticks - since == 235;
                server.submit(() -> {
                    var p = server.getPlayerList().getPlayer(c.player.getUUID());
                    p.setGameMode(GameType.SPECTATOR);
                    p.connection.teleport(impact.x + (top ? 0 : -70), impact.y + (top ? 95 : 45), impact.z + (top ? 0 : -70), top ? 0 : -45, top ? 90 : 28);
                }).join();
            }
            if (ticks - since == 250 || ticks - since == 270) grab(c);
            if (ticks - since > 280) c.stop();
            return;
        }
        if (stage != 2) return;
        if (ticks - since > 900) throw new AssertionError("Fuga full QA timeout");
        var f = FugaRenderer.localFrame(0);
        // The cooling-ground afterglow frame (past END_TICK) counts as finished.
        if (f != null && f.phase() == 2 && f.age() >= net.godwand.skill.FugaSequence.END_TICK) f = null;
        if (!released) c.options.keyUse.setDown(true);
        grab(c);
        if (f == null) {
            if (released && ticks - since > 300) {
                stage = 3; since = ticks; c.options.keyUse.setDown(false);
                System.out.printf(Locale.ROOT, "FUGA_FULL PASS frames=%d fpsAvg=%.1f fpsMin=%d serverMsptAvg=%.2f phaseFpsMin=%s%n",
                        frame, fpsSum / (double)Math.max(1, fpsCount), fpsMin, mspt / 1e6, phaseMin);
            }
            return;
        }
        int fps = c.getFps(); if (ticks % 5 == 0 && fps > 0) { fpsSum += fps; fpsCount++; fpsMin = Math.min(fpsMin, fps); phaseMin.merge(f.phase() == 2 ? (f.age() < 80 ? "sphere" : "column") : "phase" + f.phase(), fps, Math::min); }
        mspt = Math.max(mspt, server.getAverageTickTimeNanos());
        if (f.phase() == 2) { long[] times = server.getTickTimesNanos(); long last = times[(server.getTickCount() + times.length - 1) % times.length]; impactMspt = Math.max(impactMspt, last); }
        if (f.phase() == 0) {
            // Free aim while holding, then return to the shot line before release.
            if (f.age() > 215 && f.age() < 240) { c.player.setYRot((float)(f.age() - 215) * -1.4f); c.player.setXRot(-6); }
            if (f.age() >= 240 && f.age() < 265) { c.player.setYRot((float)(265 - f.age()) * -1.4f); c.player.setXRot(0); }
            if (f.age() >= 222 && marks.add("held")) {
                if (!c.player.isUsingItem() || c.player.position().distanceToSqr(anchor) > .03) throw new AssertionError("Held aim lost lock");
                server.submit(() -> {
                    var p = server.getPlayerList().getPlayer(c.player.getUUID());
                    if (!FugaManager.movementLocked(p) || !FugaManager.invulnerable(p)) throw new AssertionError("Server hold guard missing");
                }).join();
                System.out.println("FUGA_FULL PASS held aim and server lock");
            }
            if (f.age() >= 280 && !released) {
                released = true; c.player.setYRot(0); c.player.setXRot(0);
                c.options.keyUse.setDown(false); c.gameMode.releaseUsingItem(c.player);
                System.out.println("FUGA_FULL RELEASE");
            }
        } else if (marks.add("phase" + f.phase())) System.out.println("FUGA_FULL PHASE " + f.phase() + " director=" + FugaCinematic.active());
        if (f.phase() == 2) impact = f.origin();
    }
    private void grab(Minecraft c) {
        Screenshot.grab(c.gameDirectory, "fuga-full-" + run + "/f-" + String.format(Locale.ROOT, "%04d", frame++) + ".png", c.getMainRenderTarget(), m -> {});
    }
}
