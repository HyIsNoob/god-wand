package net.godwand.qa;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.godwand.client.BlueRedShowcase;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.godwand.GodWandMod;
import net.godwand.config.GodWandConfig;
import net.godwand.item.GodWandItem;
import net.godwand.skill.BlueRedManager;
import net.godwand.skill.BlueRedRules.BlueState;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.nio.file.*;
import java.util.*;

/**
 * Development-only: drives Blue (real left-click hold through the attack mixin) and Red (real right-click hold) in the Fabric 1.21.1 client and captures frames.
 * Scene from build/fuga-qa/gojo-scene: combo (3 husks, Blue to READY, anchor, Red into it), shatter (Red through another player's unready Blue),
 * terrain (combo inside a stone hill, to see eating and MSPT), hit (Red straight into one husk 12 blocks ahead: impact and knockback), look (Blue, ready Blue, Red at merge size and at flight size, one after another at the
 * same spot, Blue's gameplay distance ahead, same camera, HUD and hand hidden). build/fuga-qa/rd8 sets render distance 8; build/fuga-qa/stop quits.
 */
public final class BlueRedProbe implements ClientModInitializer {
    private static final int OX = 6000, OZ = 6000;
    private String scene = "combo";
    private final String run = Long.toString(System.currentTimeMillis());
    private int ticks, stage, since, frame, ground, readyAt = -1;
    private long msptPeak, msptSeen = -1;
    private double msptSum; private int msptN;
    private boolean moved;
    private java.util.UUID hitMob;
    private boolean sideMoved;

    public void onInitializeClient() {
        if (!Boolean.getBoolean("godwand.fuga.qa")) throw new IllegalStateException("QA flag required");
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(BlueRedShowcase::render);
    }

    private Path flags(Minecraft c) { return c.gameDirectory.toPath().getParent(); }

    private void tick(Minecraft c) {
        var server = c.getSingleplayerServer();
        if (c.player == null || c.level == null || server == null
                || !c.gameDirectory.toPath().toAbsolutePath().normalize().endsWith(Path.of("build", "fuga-qa", "run"))) return;
        c.options.pauseOnLostFocus = false;
        if (Files.exists(flags(c).resolve("stop"))) { c.stop(); return; }
        ticks++;
        if (stage == 0 && !moved && ticks > 80 && c.screen == null) {
            moved = true; since = ticks;
            try { scene = Files.readString(flags(c).resolve("gojo-scene")).trim(); } catch (Exception e) { scene = "combo"; }
            server.submit(() -> {
                var p = me(server, c);
                p.setGameMode(GameType.CREATIVE);
                p.connection.teleport(OX + .5, 100, OZ + .5, 0, 0);
            }).join();
        }
        if (stage == 0 && moved && ticks - since > 200) setup(c, server);
        if (stage >= 2) sampleMspt(server);
        switch (stage) {
            case 2 -> blue(c, server);
            case 3 -> red(c, server);
            case 4 -> aftermath(c, server);
            case 5 -> look(c);
            default -> {}
        }
    }

    private ServerPlayer me(MinecraftServer server, Minecraft c) { return server.getPlayerList().getPlayer(c.player.getUUID()); }

    private void setup(Minecraft c, MinecraftServer server) {
        c.getWindow().setWindowed(1280, 720);
        c.getWindow().setTitle("God Wand 1.6.0 - Blue/Red QA (" + scene + ")");
        c.options.framerateLimit().set(60);
        c.options.renderDistance().set(Files.exists(flags(c).resolve("rd8")) ? 8 : 16);
        c.options.guiScale().set(2); c.options.setCameraType(CameraType.FIRST_PERSON); c.options.broadcastOptions();
        server.submit(() -> {
            var p = me(server, c);
            ServerLevel level = p.serverLevel();
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false, server);
            level.setDayTime(6000); level.setWeatherParameters(60000, 0, false, false);
            ground = level.getHeight(Heightmap.Types.WORLD_SURFACE, OX, OZ);
            if (scene.equals("terrain")) hill(level);
            int stand = level.getHeight(Heightmap.Types.WORLD_SURFACE, OX, OZ);
            var wand = new ItemStack(GodWandMod.GOD_WAND_ITEM); GodWandItem.setSkillMode(wand, GodWandItem.MODE_BLUE_RED);
            p.getInventory().setItem(0, wand); p.getInventory().selected = 0; p.getInventory().setItem(40, ItemStack.EMPTY);
            p.connection.teleport(OX + .5, stand, OZ + .5, 0, 0); p.inventoryMenu.broadcastChanges();
            if (scene.equals("shatter")) {
                // Another "player" holds an unready Blue on Red's path: 22 blocks ahead.
                var other = FakePlayer.get(level);
                var otherWand = new ItemStack(GodWandMod.GOD_WAND_ITEM); GodWandItem.setSkillMode(otherWand, GodWandItem.MODE_BLUE_RED);
                other.setItemInHand(InteractionHand.MAIN_HAND, otherWand);
                other.moveTo(OX + .5, stand, OZ + 30.5, 180, 0);
                BlueRedManager.input(other, true);
            } else if (scene.startsWith("hit")) {
                Mob m = (Mob) EntityType.HUSK.create(level);
                // Survives the hit (Red deals 200), so its reaction can be watched.
                m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(400); m.setHealth(400);
                // Held in place until the shot (it wandered out of Red's range); knockback is velocity, so it still flies.
                m.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 80, 255, false, false));
                m.setPos(OX + .5, level.getHeight(Heightmap.Types.WORLD_SURFACE, OX, OZ + 12), OZ + 12.5);
                m.setPersistenceRequired();
                level.addFreshEntity(m);
                hitMob = m.getUUID();
            } else if (!scene.equals("look")) for (int i = 0; i < 3; i++) {
                Mob m = (Mob) EntityType.HUSK.create(level);
                double x = OX + .5 + (i - 1) * 1.6, z = OZ + 8.5;
                m.setPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, (int) Math.floor(x), (int) Math.floor(z)), z);
                m.setPersistenceRequired();
                level.addFreshEntity(m);
            }
        }).join();
        c.player.getInventory().selected = 0; c.player.connection.send(new ServerboundSetCarriedItemPacket(0));
        try { Files.createDirectories(c.gameDirectory.toPath().resolve("screenshots/gojo-" + scene + "-" + run)); }
        catch (java.io.IOException e) { throw new RuntimeException(e); }
        stage = scene.equals("shatter") || scene.startsWith("hit") ? 3 : scene.equals("look") ? 5 : 2; since = ticks;
        System.out.println("GOJO_QA SETUP scene=" + scene + " run=" + run + " ground=" + ground);
    }

    /** A smooth stone hill with a grass skin round the Blue's spot, so eating and re-meshing show up. */
    private void hill(ServerLevel level) {
        for (int dx = -26; dx <= 26; dx++) for (int dz = -26; dz <= 26; dz++) {
            int h = (int) Math.round(11 * Math.exp(-(dx * dx + (dz - 10) * (dz - 10)) / 220.0));
            for (int y = 0; y < h; y++)
                level.setBlock(new BlockPos(OX + dx, ground + y, OZ + dz), (y == h - 1 ? Blocks.GRASS_BLOCK : y > h - 4 ? Blocks.DIRT : Blocks.STONE).defaultBlockState(), 2);
        }
    }

    private BlueState myBlue(MinecraftServer server, Minecraft c) {
        return server.submit(() -> {
            var p = me(server, c);
            for (var b : BlueRedManager.blues(p.serverLevel())) if (b.pos().distanceTo(p.getEyePosition()) < 9 || b.state() == BlueState.ANCHORED) return b.state();
            return null;
        }).join();
    }

    private void blue(Minecraft c, MinecraftServer server) {
        int t = ticks - since;
        if (t == 40) { c.options.keyAttack.setDown(true); System.out.println("GOJO_QA BLUE hold"); }
        if (t < 40) return;
        BlueState s = myBlue(server, c);
        if (readyAt < 0 && s != null && s != BlueState.CHARGING) { readyAt = t; System.out.println("GOJO_QA BLUE ready after " + (t - 40) + " ticks"); }
        boolean nearReady = readyAt >= 0 && t - readyAt < 6;
        if (nearReady || t % 2 == 0) grab(c);
        if (readyAt >= 0 && t - readyAt == 20 || t - 40 > 170) {
            c.options.keyAttack.setDown(false);
            System.out.println("GOJO_QA BLUE release state=" + myBlue(server, c));
        }
        if (readyAt >= 0 && t - readyAt == 40 || t - 40 > 190) {
            // Step back and to the side so Red flies across the frame into the anchored Blue.
            server.submit(() -> me(server, c).connection.teleport(OX - 5.5, c.player.getY(), OZ - 6.5, -20, 4)).join();
            stage = 3; since = ticks;
        }
    }

    private void red(Minecraft c, MinecraftServer server) {
        int t = ticks - since, charge = GodWandConfig.SERVER.redChargeTicks;
        if (t == 20) { c.options.keyUse.setDown(true); System.out.println("GOJO_QA RED hold"); }
        int fired = t - 20 - charge;
        boolean hit = scene.startsWith("hit");
        // Side view: once Red is away, the camera moves out to the side of the target's line (Red keeps its own path).
        // Moved as soon as the client sees the shot leave (the server can fire a tick or two off the probe's own count when it lags).
        if (scene.equals("hitside") && !sideMoved && BlueRedShowcase.redFired()) { sideMoved = true; server.submit(() -> me(server, c).connection.teleport(OX - 9.5, c.player.getY() + 1.5, OZ + 13.5, -90, 8)).join(); }
        if (t >= 20 && (hit ? fired >= -12 && fired < 40 : fired < -8 || fired > 12 ? t % 3 == 0 : true)) grab(c);
        if (hit && fired >= -2 && fired < 14) {
            var mob = server.submit(() -> me(server, c).serverLevel().getEntity(hitMob)).join();
            if (mob != null) System.out.printf(Locale.ROOT, "GOJO_QA HIT fired=%d mob=%.2f,%.2f,%.2f hp=%.1f%n", fired, mob.getX(), mob.getY(), mob.getZ(),
                    mob instanceof net.minecraft.world.entity.LivingEntity l ? l.getHealth() : -1f);
        }
        if (hit && fired > 45) { stage = 4; since = ticks; return; }
        if (fired == 4) c.options.keyUse.setDown(false);
        if (fired == 30 || fired == 100) System.out.println("GOJO_QA PURPLE fired+" + fired + " cutscene=" + net.godwand.client.PurpleCinematic.active()
                + " purples=" + server.submit(() -> BlueRedManager.purples(me(server, c).serverLevel()).size()).join());
        if (fired > 330) { stage = 4; since = ticks; System.out.println("GOJO_QA RED done blues=" + server.submit(() -> BlueRedManager.blues(me(server, c).serverLevel()).size()).join()); }
    }

    /** Each subject for 60 ticks, a frame every 10 (and two back to back, to check nothing pops between frames). */
    /** Ticks per subject: Blue idle, Blue moved then flicked, Blue ready circling, Red charging, Red release and flight, Red held. */
    private static final int[] LOOK = {40, 60, 40, 50, 30, 60, 30};

    /** Each subject filmed every tick (videos of the real motion); the FPS with an unlimited frame rate is logged at the end of each. */
    private void look(Minecraft c) {
        int t = ticks - since;
        if (t == 1) { c.options.hideGui = true; c.options.framerateLimit().set(260); c.options.enableVsync().set(false); }
        // Anchored just before filming, once the client has caught up with the teleport (at t == 1 it can still be at the spawn height).
        if (t == 19) {
            Vec3 eye = c.player.getEyePosition(), view = c.player.getViewVector(1);
            // Raised so the whole sphere and its bands stay clear of the ground (at eye height its lower half sank into the grass).
            BlueRedShowcase.at = eye.add(view.scale(net.godwand.skill.BlueRedRules.BLUE_DISTANCE)).add(0, 1.5, 0);
            BlueRedShowcase.right = view.cross(new Vec3(0, 1, 0)).normalize();
        }
        if (t < 20) return;
        int k = t - 20, s = 0;
        while (s < LOOK.length && k >= LOOK[s]) { k -= LOOK[s]; s++; }
        if (s >= LOOK.length) { BlueRedShowcase.what = 0; System.out.println("GOJO_QA LOOK done frames=" + frame); c.stop(); return; }
        if (k == LOOK[s] - 1) System.out.println("GOJO_QA LOOK fps subject=" + (s + 1) + " fps=" + c.getFps());
        BlueRedShowcase.step(s + 1, k);
        grab(c);
    }

    private void aftermath(Minecraft c, MinecraftServer server) {
        int t = ticks - since;
        if (t == 5) server.submit(() -> {
            var p = me(server, c);
            p.setGameMode(GameType.SPECTATOR);
            p.connection.teleport(OX - 14, ground + 14, OZ - 6, -60, 35);
        }).join();
        if (t == 40) grab(c);
        if (t > 50) {
            System.out.printf(Locale.ROOT, "GOJO_QA DONE frames=%d msptAvg=%.2f msptPeak=%.2f%n", frame, msptSum / Math.max(1, msptN), msptPeak / 1e6);
            c.stop();
        }
    }

    private void sampleMspt(MinecraftServer server) {
        long[] times = server.getTickTimesNanos();
        int now = server.getTickCount();
        if (msptSeen < 0) msptSeen = now - 1;
        for (long t = Math.max(msptSeen + 1, now - times.length + 1); t < now; t++) {
            long ns = times[(int) (t % times.length)];
            msptPeak = Math.max(msptPeak, ns); msptSum += ns / 1e6; msptN++;
            if (ns > 50_000_000L) System.out.printf(Locale.ROOT, "GOJO_QA SLOWTICK %.1f ms stage %d%n", ns / 1e6, stage);
        }
        msptSeen = Math.max(msptSeen, now - 1);
    }

    private void grab(Minecraft c) {
        Screenshot.grab(c.gameDirectory, "gojo-" + scene + "-" + run + "/f-" + String.format(Locale.ROOT, "%04d", frame++) + ".png", c.getMainRenderTarget(), m -> {});
    }
}
