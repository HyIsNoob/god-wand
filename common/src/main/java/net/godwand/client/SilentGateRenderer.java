package net.godwand.client;

import net.godwand.network.SilentGatePayload;
import net.godwand.skill.SilentGateSequence;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Client state of every visible Silent Gate cast. Geometry is drawn on top of {@link #localFrame} / {@link #shots}. */
public final class SilentGateRenderer {
    /** {@code age}: ticks since the first click; {@code commandAge}: ticks since the second click (-1 before it). */
    public record Frame(int casterId, long startedAt, int phase, double age, double commandAge,
            Vec3 anchor, float yaw, long seed, int skyCircles, int barrageTicks) {}
    public record Shot(int circle, Vec3 hit, long tick) {}

    static final int SHOT_MEMORY_TICKS = 10;
    private static final class Cast {
        long startedAt, phaseAt, commandAt = -1;
        int phase, skyCircles, barrageTicks;
        Vec3 anchor;
        float yaw;
        long seed;
        final ArrayDeque<Shot> shots = new ArrayDeque<>();
    }
    private static final Map<Integer, Cast> CASTS = new LinkedHashMap<>();
    // Newest cast and the cast last cancelled per caster, so late or reordered packets cannot resurrect anything.
    private static final Map<Integer, Long> LATEST = new LinkedHashMap<>(), CLOSED = new LinkedHashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private SilentGateRenderer() {}

    public static void receive(SilentGatePayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.level != world) reset();
        world = client.level;
        if (world != null) accept(payload, world.getGameTime());
    }

    static void accept(SilentGatePayload p, long now) {
        long latest = LATEST.getOrDefault(p.casterId(), Long.MIN_VALUE);
        if (p.startedAt() < latest || p.startedAt() == CLOSED.getOrDefault(p.casterId(), Long.MIN_VALUE)) return;
        LATEST.put(p.casterId(), p.startedAt());
        if (p.phase() == SilentGatePayload.CANCEL) {
            CLOSED.put(p.casterId(), p.startedAt());
            CASTS.remove(p.casterId());
            return;
        }
        Cast cast = CASTS.get(p.casterId());
        if (cast == null || cast.startedAt != p.startedAt()) {
            CASTS.put(p.casterId(), cast = new Cast());
            cast.startedAt = p.startedAt();
            cast.phase = -1;
        }
        if (p.phase() > cast.phase) { cast.phase = p.phase(); cast.phaseAt = p.phaseAt(); }
        if (p.phase() == SilentGateSequence.Phase.BARRAGE.ordinal() && cast.commandAt < 0) cast.commandAt = p.phaseAt();
        cast.anchor = new Vec3(p.x(), p.y(), p.z());
        cast.yaw = p.yaw(); cast.seed = p.seed(); cast.skyCircles = p.skyCircles(); cast.barrageTicks = p.barrageTicks();
        for (int i = 0; i < p.shots().length; i++)
            cast.shots.add(new Shot(p.shots()[i], new Vec3(p.hits()[3 * i], p.hits()[3 * i + 1], p.hits()[3 * i + 2]), now));
        trim(cast, now);
        if (CASTS.size() > 16) CASTS.remove(CASTS.keySet().iterator().next());
    }

    private static void trim(Cast cast, long now) { cast.shots.removeIf(s -> now - s.tick() > SHOT_MEMORY_TICKS); }

    /** Past this the camera is released and the cast is forgotten. */
    private static boolean over(Cast cast, double now) {
        int release = SilentGateSequence.endTick(cast.barrageTicks) + SilentGateSequence.RETURN_TICKS;
        if (cast.commandAt >= 0) return now - cast.commandAt >= release;
        return cast.phase == SilentGateSequence.Phase.END.ordinal()
                && now - cast.phaseAt >= SilentGateSequence.ENDING_TICKS + SilentGateSequence.RETURN_TICKS;
    }

    static Frame frameAt(int casterId, double now) {
        Cast c = CASTS.get(casterId);
        if (c == null || over(c, now)) return null;
        return new Frame(casterId, c.startedAt, c.phase, now - c.startedAt, c.commandAt < 0 ? -1 : now - c.commandAt,
                c.anchor, c.yaw, c.seed, c.skyCircles, c.barrageTicks);
    }
    public static Frame frame(int casterId, float partial) {
        return world == null ? null : frameAt(casterId, world.getGameTime() + partial);
    }
    public static Frame localFrame(float partial) {
        var player = Minecraft.getInstance().player;
        return player == null ? null : frame(player.getId(), partial);
    }
    /** Shots of the last {@value #SHOT_MEMORY_TICKS} ticks, oldest first. */
    public static List<Shot> shots(int casterId) {
        Cast c = CASTS.get(casterId);
        return c == null ? List.of() : List.copyOf(c.shots);
    }

    public static void tick(Minecraft client) {
        if (client.level != world || client.player == null) { reset(); return; }
        long now = world.getGameTime();
        CASTS.entrySet().removeIf(e -> {
            boolean gone = over(e.getValue(), now)
                    || world.getEntity(e.getKey()) instanceof net.minecraft.world.entity.LivingEntity living && !living.isAlive();
            if (gone) CLOSED.put(e.getKey(), e.getValue().startedAt);
            return gone;
        });
        CASTS.values().forEach(c -> trim(c, now));
    }
    public static void reset() { CASTS.clear(); LATEST.clear(); CLOSED.clear(); world = null; }
}
