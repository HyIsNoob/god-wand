package net.godwand.qa;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.godwand.GodWandMod;
import net.godwand.client.SilentGateRenderer;
import net.godwand.config.GodWandConfig;
import net.godwand.item.GodWandItem;
import net.godwand.skill.SilentGateManager;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import java.nio.file.*;
import java.util.*;

/** Development-only: one complete Silent Gate cast (click, build, wait, click, barrage, ending), captured from the real render target. */
public final class SilentGateProbe implements ClientModInitializer {
    private static final String[] PHASES = {"BUILD", "WAIT", "BARRAGE", "END"};
    // Clean spot on the flat QA world (the copied world has an old Fuga crater next to spawn).
    private static final int OX = 4000, OZ = 4000;
    private static final int FIELD_X0 = OX - 130, FIELD_X1 = OX + 130, FIELD_Z0 = OZ + 8, FIELD_Z1 = OZ + 150;
    // Script v3: the middle tick of each of the 18 build shots, of B1..B6 (command ticks) and of E1..E4 (ticks after the barrage).
    private static final Set<Integer> REFERENCE = Set.of(7, 30, 50, 58, 77, 108, 142, 180, 216, 247, 277, 302, 324, 362, 403, 436, 461, 475, 500);
    private static final Set<Integer> BARRAGE_MID = Set.of(7, 32, 66, 104, 143, 187), END_MID = Set.of(20, 60, 102, 142);
    private int ticks, stage, since, clickAt, frame, lastCmd = -1, lastBuild = -1, lastEnd = -1;
    private final long[] fpsSum = new long[4], fpsN = new long[4];
    private final int[] fpsMin = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE},
            firstFrame = {-1, -1, -1, -1}, lastFrame = {-1, -1, -1, -1};
    private long msptPeak, msptSeen = -1, setupPeak;
    private String msptAt = "-";
    private final Map<String, double[]> msptPhase = new TreeMap<>(); // phase -> {sumMs, n, maxMs}
    private boolean moved;
    private boolean click2, sawFrame, sawBarrage;
    private final int[] phTicks = new int[4]; private final long[] quadSum = new long[4]; private final int[] quadMax = new int[4];
    private int ground, solidBefore, solidAfter, total;
    private final List<UUID> targets = new ArrayList<>();
    private final String run = Long.toString(System.currentTimeMillis());
    private final Set<String> marks = new HashSet<>();

    public void onInitializeClient() {
        if (!Boolean.getBoolean("godwand.fuga.qa")) throw new IllegalStateException("QA flag required");
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private int solid(net.minecraft.server.level.ServerLevel level) {
        int n = 0;
        for (int x = FIELD_X0; x <= FIELD_X1; x++) for (int z = FIELD_Z0; z <= FIELD_Z1; z++)
            for (int y = ground - 12; y < ground; y++) if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) n++;
        return n;
    }

    private void tick(Minecraft c) {
        var server = c.getSingleplayerServer();
        if (c.player == null || c.level == null || server == null
                || !c.gameDirectory.toPath().toAbsolutePath().normalize().endsWith(Path.of("build", "fuga-qa", "run"))) return;
        c.options.pauseOnLostFocus = false;
        if (Files.exists(c.gameDirectory.toPath().getParent().resolve("stop"))) { c.stop(); return; }
        ticks++;
        if (stage == 0 && !moved && ticks > 80 && c.screen == null) {
            moved = true; since = ticks;
            server.submit(() -> {
                var p = server.getPlayerList().getPlayer(c.player.getUUID());
                p.setGameMode(GameType.CREATIVE);
                p.connection.teleport(OX + .5, 100, OZ + .5, 0, 0);
            }).join();
        }
        if (moved && stage <= 1) sampleMspt(server, "SETUP");
        if (stage == 0 && moved && ticks - since > 200 && Files.exists(c.gameDirectory.toPath().getParent().resolve("warmup"))) {
            System.out.println("GATE_QA WARMUP done (chunks generated)"); c.stop(); return;
        }
        if (stage == 0 && moved && ticks - since > 200) {
            // A/B switch: build/fuga-qa/nocrater turns terrain damage off to separate re-meshing from rendering cost.
            if (Files.exists(c.gameDirectory.toPath().getParent().resolve("nocrater"))) GodWandConfig.SERVER.gateTerrainDamage = false;
            c.getWindow().setWindowed(1280, 720);
            c.getWindow().setTitle("God Wand 1.5.0 - Silent Gate QA");
            // build/fuga-qa/rd8: the owner's render distance (8), where far shots leave the loaded chunks.
            c.options.framerateLimit().set(60); c.options.renderDistance().set(Files.exists(c.gameDirectory.toPath().getParent().resolve("rd8")) ? 8 : 16);
            c.options.guiScale().set(2); c.options.setCameraType(CameraType.FIRST_PERSON); c.options.broadcastOptions();
            GodWandConfig.CLIENT.gateCutscene = true;
            server.submit(() -> {
                var p = server.getPlayerList().getPlayer(c.player.getUUID());
                var level = p.serverLevel();
                p.setGameMode(GameType.CREATIVE);
                level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false, server);
                level.setDayTime(6000); level.setWeatherParameters(60000, 0, false, false);
                ground = level.getHeight(Heightmap.Types.WORLD_SURFACE, OX, OZ);
                var rnd = new Random(7);
                for (int i = 0; i < 14; i++) {
                    // 12 zombies + 2 skeletons, 20-130 blocks ahead (+Z), spread to the sides (scaled Silent Gate, target radius 160).
                    double z = 20 + rnd.nextDouble() * 110, x = (rnd.nextDouble() * 2 - 1) * (10 + z * 0.8);
                    x += OX; z += OZ;
                    // Target 0 is a vanilla ravager (100 HP): the server features it (highest max health) and spares it until the kill-shot volley.
                    Mob m = (Mob) (i == 0 ? EntityType.RAVAGER : i < 12 ? EntityType.ZOMBIE : EntityType.SKELETON).create(level);
                    if (i == 0) { x = OX + 8; z = OZ + 60; }
                    int gy = level.getHeight(Heightmap.Types.WORLD_SURFACE, (int) Math.floor(x), (int) Math.floor(z));
                    m.setPos(x, gy, z); m.setNoAi(true); m.setPersistenceRequired();
                    level.addFreshEntity(m); targets.add(m.getUUID());
                }
                total = targets.size();
                solidBefore = solid(level);
                var wand = new ItemStack(GodWandMod.GOD_WAND_ITEM); GodWandItem.setSkillMode(wand, GodWandItem.MODE_SILENT_GATE);
                p.getInventory().setItem(0, wand); p.getInventory().selected = 0;
                p.getInventory().setItem(40, ItemStack.EMPTY);
                p.getCooldowns().removeCooldown(GodWandMod.GOD_WAND_ITEM);
                p.connection.teleport(OX + .5, ground, OZ + .5, 0, 0); p.inventoryMenu.broadcastChanges();
            }).join();
            c.player.getInventory().selected = 0; c.player.connection.send(new ServerboundSetCarriedItemPacket(0));
            try { Files.createDirectories(c.gameDirectory.toPath().resolve("screenshots/gate-" + run)); }
            catch (java.io.IOException e) { throw new RuntimeException(e); }
            stage = 1; since = ticks;
            System.out.println("GATE_QA SETUP run=" + run + " Java=" + System.getProperty("java.version") + " ground=" + ground + " targets=" + total + " solidBefore=" + solidBefore);
        }
        if (stage == 1 && ticks - since > 80 && GodWandItem.getSkillMode(c.player.getMainHandItem()) == GodWandItem.MODE_SILENT_GATE) {
            stage = 2; since = ticks; clickAt = ticks;
            c.player.setYRot(0); c.player.setXRot(0);
            c.gameMode.useItem(c.player, InteractionHand.MAIN_HAND);
            System.out.println("GATE_QA CLICK1");
        }
        if (stage == 3) { aftermath(c, server); return; }
        if (stage != 2) return;
        int age = ticks - clickAt;
        if (age > 1200) throw new AssertionError("Gate QA timeout");
        var f = SilentGateRenderer.localFrame(0);
        if (f != null) sawFrame = true;
        if (f == null && !sawFrame) return;
        if (f != null && f.phase() <= 1 && f.commandAge() < 0) {
            // BUILD/WAIT: every fourth cast tick (contact sheet) plus the reference ticks, named by cast age.
            int fa = (int) Math.floor(f.age());
            if (fa != lastBuild && (fa % 2 == 0 || REFERENCE.contains(fa))) {
                lastBuild = fa;
                if (!Files.exists(c.gameDirectory.toPath().getParent().resolve("noshots"))) Screenshot.grab(c.gameDirectory, "gate-" + run + "/b-" + String.format(Locale.ROOT, "%03d", fa) + ".png", c.getMainRenderTarget(), m -> {});
            }
        }
        // Barrage: every third command tick (c-NNN) plus the shot middles; ending: every third tick after the barrage (e-NNN) plus the shot middles.
        if (f != null && f.phase() >= 2 && f.commandAge() >= 0) {
            int cmd = (int) f.commandAge(), endAt = 12 + f.barrageTicks();
            if (cmd < endAt && cmd != lastCmd && (cmd % 3 == 0 || BARRAGE_MID.contains(cmd) || cmd >= 138 && cmd <= 150)) {
                lastCmd = cmd;
                if (!Files.exists(c.gameDirectory.toPath().getParent().resolve("noshots"))) Screenshot.grab(c.gameDirectory, "gate-" + run + "/c-" + String.format(Locale.ROOT, "%03d", cmd) + ".png", c.getMainRenderTarget(), m -> {});
            } else if (cmd >= endAt && cmd - endAt != lastEnd && ((cmd - endAt) % 3 == 0 || END_MID.contains(cmd - endAt))) {
                lastEnd = cmd - endAt;
                if (!Files.exists(c.gameDirectory.toPath().getParent().resolve("noshots"))) Screenshot.grab(c.gameDirectory, "gate-" + run + "/e-" + String.format(Locale.ROOT, "%03d", cmd - endAt) + ".png", c.getMainRenderTarget(), m -> {});
            }
        }
        if (f == null) {
            if (click2 && sawBarrage) {
                stage = 3; since = ticks;
                summary();
            }
            return;
        }
        if (f.commandAge() >= 20 && Files.exists(c.gameDirectory.toPath().getParent().resolve("short"))) { summary(); System.out.println("GATE_QA SHORT stop"); c.stop(); return; }
        int ph = Math.min(3, f.phase());
        if (firstFrame[ph] < 0) { firstFrame[ph] = frame; System.out.println("GATE_QA PHASE " + ph + " " + PHASES[ph] + " age=" + age + " frame=" + frame); }
        lastFrame[ph] = frame++;
        int fps = c.getFps();
        int q = SilentGateRenderer.lastQuads; phTicks[ph]++; quadSum[ph] += q; quadMax[ph] = Math.max(quadMax[ph], q);
        if (ticks % 5 == 0 && fps > 0) { fpsSum[ph] += fps; fpsN[ph]++; fpsMin[ph] = Math.min(fpsMin[ph], fps); }
        sampleMspt(server, PHASES[ph] + " age=" + age);
        if (ph == 2) sawBarrage = true;
        if ((ph == 0 && age == 100 || ph == 1 && age == 520) && marks.add("lock" + ph)) {
            server.submit(() -> {
                var p = server.getPlayerList().getPlayer(c.player.getUUID());
                if (!SilentGateManager.movementLocked(p) || !SilentGateManager.invulnerable(p))
                    throw new AssertionError("Gate server lock/invulnerability missing in " + PHASES[ph]);
            }).join();
            System.out.println("GATE_QA PASS lock+invulnerable in " + PHASES[ph]);
        }
        if (age >= 540 && !click2 && ph == 1) {
            click2 = true;
            c.gameMode.useItem(c.player, InteractionHand.MAIN_HAND);
            System.out.println("GATE_QA CLICK2 age=" + age);
        }
    }

    private void summary() {
                System.out.printf(Locale.ROOT, "GATE_QA PASS frames=%d serverMsptPeak=%.2f ms at [%s] setupPeak=%.2f ms%n", frame, msptPeak / 1e6, msptAt, setupPeak / 1e6);
                for (int i = 0; i < 4; i++) if (firstFrame[i] >= 0)
                    System.out.printf(Locale.ROOT, "GATE_QA FPS %s avg=%.1f min=%d frames=%d..%d%n", PHASES[i],
                            fpsSum[i] / (double) Math.max(1, fpsN[i]), fpsMin[i], firstFrame[i], lastFrame[i]);
                msptPhase.forEach((k, a) -> System.out.printf(Locale.ROOT, "GATE_QA MSPT %s avg=%.2f max=%.2f ticks=%.0f%n", k, a[0] / a[1], a[2], a[1]));
                for (int i = 0; i < 4; i++) if (firstFrame[i] >= 0)
                    System.out.printf(Locale.ROOT, "GATE_QA QUADS %s avgPerTick=%.0f max=%d%n", PHASES[i], quadSum[i] / (double) Math.max(1, phTicks[i]), quadMax[i]);
    }

    /** Reads every server tick time since the last call; logs ticks over 50 ms with the Silent Gate phase/age. */
    private void sampleMspt(net.minecraft.server.MinecraftServer server, String label) {
        long[] times = server.getTickTimesNanos();
        int now = server.getTickCount();
        if (msptSeen < 0) msptSeen = now - 1;
        for (long t = Math.max(msptSeen + 1, now - times.length + 1); t < now; t++) {
            long ns = times[(int) (t % times.length)];
            boolean setup = label.startsWith("SETUP");
            if (setup) setupPeak = Math.max(setupPeak, ns);
            else {
                if (ns > msptPeak) { msptPeak = ns; msptAt = label; }
                var a = msptPhase.computeIfAbsent(label.split(" ")[0], k -> new double[3]);
                a[0] += ns / 1e6; a[1]++; a[2] = Math.max(a[2], ns / 1e6);
            }
            if (ns > 50_000_000L) System.out.printf(Locale.ROOT, "GATE_QA SLOWTICK %.1f ms server tick %d [%s]%n", ns / 1e6, t, label);
        }
        msptSeen = Math.max(msptSeen, now - 1);
    }

    private void aftermath(Minecraft c, net.minecraft.server.MinecraftServer server) {
        int t = ticks - since;
        if (t == 5) server.submit(() -> {
            var level = server.getPlayerList().getPlayer(c.player.getUUID()).serverLevel();
            solidAfter = solid(level);
            int dead = 0;
            var anchorPos = new net.minecraft.world.phys.Vec3(OX + .5, ground, OZ + .5);
            for (UUID u : targets) {
                var e = level.getEntity(u);
                if (e == null || !e.isAlive()) dead++;
                if (e != null && e.isAlive()) System.out.printf(Locale.ROOT, "GATE_QA ALIVE %s dist=%.0f health=%s pos=%.0f,%.0f chunkLoaded=%b%n", e.getType(), e.position().distanceTo(anchorPos),
                        e instanceof net.minecraft.world.entity.LivingEntity l ? l.getHealth() : "-", e.getX() - OX, e.getZ() - OZ, level.hasChunkAt(e.blockPosition()));
                else if (e == null) System.out.println("GATE_QA GONE (entity unloaded) " + u);
            }
            System.out.println("GATE_QA CONFIG gateTargetRadius=" + GodWandConfig.SERVER.gateTargetRadius + " beamDamage=" + GodWandConfig.SERVER.gateBeamDamage + " craterRadius=" + GodWandConfig.SERVER.gateCraterRadius);
            System.out.println("GATE_QA RESULT killed=" + dead + "/" + total + " blocksRemoved=" + (solidBefore - solidAfter)
                    + " (field x" + FIELD_X0 + ".." + FIELD_X1 + " z" + FIELD_Z0 + ".." + FIELD_Z1 + ")");
        }).join();
        if (t == 10 || t == 60) {
            boolean top = t == 10;
            server.submit(() -> {
                var p = server.getPlayerList().getPlayer(c.player.getUUID());
                p.setGameMode(GameType.SPECTATOR);
                p.connection.teleport(OX + (top ? 0 : -120), ground + (top ? 260 : 90), OZ + (top ? 80 : -50), top ? 0 : -45, top ? 90 : 28);
            }).join();
        }
        if (t == 40 || t == 90)
            if (!Files.exists(c.gameDirectory.toPath().getParent().resolve("noshots"))) Screenshot.grab(c.gameDirectory, "gate-" + run + "/aftermath-" + (t == 40 ? "top" : "oblique") + ".png", c.getMainRenderTarget(), m -> {});
        if (t > 100) { System.out.println("GATE_QA DONE"); c.stop(); }
    }

    private void grab(Minecraft c) {
        if (!Files.exists(c.gameDirectory.toPath().getParent().resolve("noshots"))) Screenshot.grab(c.gameDirectory, "gate-" + run + "/f-" + String.format(Locale.ROOT, "%04d", frame++) + ".png", c.getMainRenderTarget(), m -> {});
    }
}
