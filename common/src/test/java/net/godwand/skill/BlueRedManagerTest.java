package net.godwand.skill;

import net.godwand.item.GodWandItem;
import net.godwand.network.BlueRedPayload;
import net.godwand.skill.BlueRedRules.BlueState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BlueRedManagerTest {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }

    final List<BlueRedPayload> sent = new ArrayList<>();
    final List<Entity> nearby = new ArrayList<>();
    final List<Entity> hurt = new ArrayList<>();
    final Map<Entity, float[]> health = new HashMap<>();
    boolean pvp = true;
    ServerLevel world;
    MockedStatic<GodWandItem> modes;
    MockedStatic<SkillCooldowns> cooldowns;

    net.godwand.config.GodWandConfig.Server previousConfig;

    @BeforeEach void setUp() {
        previousConfig = net.godwand.config.GodWandConfig.SERVER;
        net.godwand.config.GodWandConfig.SERVER = new net.godwand.config.GodWandConfig.Server();
        net.godwand.config.GodWandConfig.SERVER.blueTerrainDamage = false; // terrain is BlueEaterTest's job
        BlueRedManager.clearAll();
        BlueRedManager.sender = (level, pos, payload) -> sent.add((BlueRedPayload) payload);
        BlueRedManager.finder = (level, box) -> new ArrayList<>(nearby);
        BlueRedManager.hurter = (target, caster, amount) -> {
            hurt.add(target);
            if (target instanceof ServerPlayer && !pvp) return false;
            float[] h = health.get(target);
            if (h != null) h[0] = Math.max(0, h[0] - amount * 10);
            return true;
        };
        world = level();
        net.godwand.config.GodWandConfig.SERVER.redTerrainDamage = false;
        modes = mockStatic(GodWandItem.class);
        modes.when(() -> GodWandItem.getSkillMode(any())).thenReturn(GodWandItem.MODE_BLUE_RED);
        cooldowns = mockStatic(SkillCooldowns.class);
        cooldowns.when(() -> SkillCooldowns.ready(any(), anyInt())).thenReturn(true);
    }
    @AfterEach void tearDown() { modes.close(); cooldowns.close(); BlueRedManager.clearAll(); net.godwand.config.GodWandConfig.SERVER = previousConfig; }

    final Set<Long> unloaded = new HashSet<>();
    ServerLevel level() {
        var w = mock(ServerLevel.class);
        when(w.dimension()).thenReturn(Level.OVERWORLD);
        var chunks = mock(net.minecraft.server.level.ServerChunkCache.class);
        when(w.getChunkSource()).thenReturn(chunks);
        var chunk = mock(net.minecraft.world.level.chunk.LevelChunk.class);
        when(chunks.getChunkNow(anyInt(), anyInt())).thenAnswer(i -> unloaded.contains(net.minecraft.world.level.ChunkPos.asLong((int) i.getArgument(0), (int) i.getArgument(1))) ? null : chunk);
        return w;
    }
    ServerPlayer player() { return player(7, world, new Vec3(0, 65, 0), new Vec3(0, 0, 1)); }
    ServerPlayer player(int id, ServerLevel in, Vec3 eye, Vec3 look) {
        var p = mock(ServerPlayer.class);
        var wand = mock(ItemStack.class);
        when(wand.getItem()).thenReturn(mock(GodWandItem.class));
        when(p.getUUID()).thenReturn(UUID.randomUUID());
        when(p.getId()).thenReturn(id);
        when(p.serverLevel()).thenReturn(in);
        when(p.level()).thenReturn(in);
        when(p.isAlive()).thenReturn(true);
        when(p.getMainHandItem()).thenReturn(wand);
        when(p.getEyePosition()).thenReturn(eye);
        when(p.getLookAngle()).thenReturn(look);
        when(p.position()).thenReturn(eye.subtract(0, 1, 0));
        when(p.getDeltaMovement()).thenReturn(Vec3.ZERO);
        return p;
    }
    /** A 20-health mob standing in Blue's core (Blue sits at 0,65,8). */
    LivingEntity zombie() { return zombie(new Vec3(0, 64.5, 8)); }
    LivingEntity zombie(Vec3 at) {
        var z = mock(LivingEntity.class);
        float[] h = {20};
        health.put(z, h);
        when(z.getHealth()).thenAnswer(i -> h[0]);
        when(z.isAlive()).thenAnswer(i -> h[0] > 0);
        when(z.position()).thenReturn(at);
        when(z.getBbWidth()).thenReturn(0.6f);
        when(z.getBbHeight()).thenReturn(1.9f);
        when(z.getDeltaMovement()).thenReturn(Vec3.ZERO);
        when(z.getUUID()).thenReturn(UUID.randomUUID());
        nearby.add(z);
        return z;
    }
    void ticks(int n) { for (int i = 0; i < n; i++) BlueRedManager.tick(world); }
    BlueRedPayload last() { return sent.get(sent.size() - 1); }
    BlueRedManager.Blue only() { var b = BlueRedManager.blues(world); assertEquals(1, b.size()); return b.get(0); }

    @Test void createsOnPressWhenReady() {
        BlueRedManager.input(player(), true);
        assertEquals(BlueState.CHARGING, only().state);
        assertEquals(BlueRedPayload.BLUE, last().kind());
    }

    @Test void refusedOnCooldownOrWrongMode() {
        var p = player();
        cooldowns.when(() -> SkillCooldowns.ready(any(), anyInt())).thenReturn(false);
        BlueRedManager.input(p, true);
        assertTrue(BlueRedManager.blues(world).isEmpty());
        cooldowns.when(() -> SkillCooldowns.ready(any(), anyInt())).thenReturn(true);
        modes.when(() -> GodWandItem.getSkillMode(any())).thenReturn(GodWandItem.MODE_FUGA);
        BlueRedManager.input(p, true);
        assertTrue(BlueRedManager.blues(world).isEmpty());
    }

    @Test void heldBlueFollowsAim() {
        var p = player();
        BlueRedManager.input(p, true);
        when(p.getLookAngle()).thenReturn(new Vec3(1, 0, 0));
        ticks(1);
        assertEquals(new Vec3(8, 65, 0), only().pos);
    }

    @Test void unreadyReleaseVanishesAndStartsCooldown() {
        var p = player();
        BlueRedManager.input(p, true);
        BlueRedManager.input(p, false);
        assertTrue(BlueRedManager.blues(world).isEmpty());
        assertEquals(BlueRedPayload.BLUE_GONE, last().kind());
        cooldowns.verify(() -> SkillCooldowns.start(p, GodWandItem.MODE_BLUE_RED, 60));
    }

    @Test void chargingVanishesAfter8s() {
        BlueRedManager.input(player(), true);
        ticks(159);
        assertEquals(1, BlueRedManager.blues(world).size());
        ticks(1);
        assertTrue(BlueRedManager.blues(world).isEmpty());
    }

    @Test void thresholdFromThreeZombies() {
        zombie(); zombie();
        BlueRedManager.input(player(), true);
        ticks(BlueRedRules.CORE_PERIOD * 4);
        assertEquals(BlueState.CHARGING, only().state, "two zombies (40) are not enough");
        zombie();
        ticks(BlueRedRules.CORE_PERIOD * 2);
        assertEquals(BlueState.READY, only().state);
        assertEquals(60, only().credited, 1e-6);
        assertTrue(sent.stream().anyMatch(s -> s.kind() == BlueRedPayload.BLUE && s.state() == BlueState.READY.ordinal()));
    }

    @Test void readyReleaseAnchorsFor300Ticks() {
        zombie(); zombie(); zombie();
        var p = player();
        BlueRedManager.input(p, true);
        ticks(BlueRedRules.CORE_PERIOD);
        BlueRedManager.input(p, false);
        assertEquals(BlueState.ANCHORED, only().state);
        Vec3 at = only().pos;
        when(p.getLookAngle()).thenReturn(new Vec3(1, 0, 0));
        ticks(299);
        assertEquals(at, only().pos, "an anchored Blue stays where it was released");
        ticks(1);
        assertTrue(BlueRedManager.blues(world).isEmpty());
    }

    @Test void noBlueWhileAnchoredExists() {
        zombie(); zombie(); zombie();
        var p = player();
        BlueRedManager.input(p, true);
        ticks(BlueRedRules.CORE_PERIOD);
        BlueRedManager.input(p, false);
        var anchored = only();
        BlueRedManager.input(p, true);
        assertSame(anchored, only());
        assertEquals(BlueState.ANCHORED, anchored.state);
    }

    @Test void pvpOffPlayerNotCounted() {
        pvp = false;
        var victim = player();
        when(victim.position()).thenReturn(new Vec3(0, 64.5, 8));
        when(victim.getBbWidth()).thenReturn(0.6f);
        when(victim.getBbHeight()).thenReturn(1.8f);
        when(victim.getHealth()).thenReturn(20f);
        nearby.add(victim);
        BlueRedManager.input(player(), true);
        ticks(BlueRedRules.CORE_PERIOD * 3);
        assertEquals(0, only().credited, 1e-6);
        assertEquals(BlueState.CHARGING, only().state);
    }

    @Test void ownerLeaveRemovesBlue() {
        var p = player();
        BlueRedManager.input(p, true);
        BlueRedManager.onLeave(p);
        assertTrue(BlueRedManager.blues(world).isEmpty());
        assertDoesNotThrow(() -> ticks(5));
    }

    @Test void ownerNeverHurt() {
        var p = player();
        when(p.getEyePosition()).thenReturn(new Vec3(0, 65, 0));
        when(p.position()).thenReturn(new Vec3(0, 64.5, 8));
        when(p.getBbWidth()).thenReturn(0.6f);
        when(p.getBbHeight()).thenReturn(1.8f);
        nearby.add(p);
        BlueRedManager.input(p, true);
        ticks(BlueRedRules.CORE_PERIOD * 2);
        assertFalse(hurt.contains(p));
        verify(p, never()).setDeltaMovement(any(Vec3.class));
    }

    @Test void heldBlueReleasedWhenOwnerSwitchesAway() {
        var p = player();
        BlueRedManager.input(p, true);
        modes.when(() -> GodWandItem.getSkillMode(any())).thenReturn(GodWandItem.MODE_FUGA);
        ticks(1);
        assertTrue(BlueRedManager.blues(world).isEmpty());
    }

    // ---- Red ----
    long count(int kind) { return sent.stream().filter(x -> x.kind() == kind).count(); }
    /** Player B's Blue at (0,65,22) facing back towards A, on Red's path from A at the origin looking +z. */
    ServerPlayer blueOwner(boolean ready) {
        var b = player(9, world, new Vec3(0, 65, 30), new Vec3(0, 0, -1));
        if (ready) { zombie(new Vec3(0, 64.5, 22)); zombie(new Vec3(0, 64.5, 22)); zombie(new Vec3(0, 64.5, 22)); }
        BlueRedManager.input(b, true);
        if (ready) { ticks(BlueRedRules.CORE_PERIOD); assertEquals(BlueState.READY, only().state); nearby.clear(); }
        return b;
    }
    void fire(ServerPlayer p) {
        assertTrue(BlueRedManager.beginRed(p));
        BlueRedManager.chargeTick(p, net.godwand.config.GodWandConfig.SERVER.redChargeTicks);
    }

    @Test void firesExactlyAt50() {
        var p = player();
        assertTrue(BlueRedManager.beginRed(p));
        assertEquals(1, count(BlueRedPayload.RED_CHARGE));
        BlueRedManager.chargeTick(p, 49);
        assertEquals(0, count(BlueRedPayload.RED_FIRE));
        BlueRedManager.chargeTick(p, 50);
        assertEquals(1, count(BlueRedPayload.RED_FIRE));
        cooldowns.verify(() -> SkillCooldowns.start(p, net.godwand.skill.SkillCooldownState.RED_SLOT, 80));
        BlueRedManager.cancelRed(p);
        assertEquals(0, count(BlueRedPayload.RED_CANCEL), "letting go after the shot changes nothing");
    }

    @Test void releaseBeforeFireCancelsWithoutCooldown() {
        var p = player();
        BlueRedManager.beginRed(p);
        BlueRedManager.chargeTick(p, 30);
        BlueRedManager.cancelRed(p);
        assertEquals(1, count(BlueRedPayload.RED_CANCEL));
        BlueRedManager.chargeTick(p, 50);
        assertEquals(0, count(BlueRedPayload.RED_FIRE));
        cooldowns.verify(() -> SkillCooldowns.start(eq(p), eq(net.godwand.skill.SkillCooldownState.RED_SLOT), anyInt()), never());
    }

    @Test void noRedWhileHoldingBlue() {
        var p = player();
        BlueRedManager.input(p, true);
        assertFalse(BlueRedManager.beginRed(p));
    }

    @Test void hitsEachEntityOnce() {
        var z = zombie(new Vec3(0, 64.5, 10));
        fire(player());
        ticks(12);
        assertEquals(1, hurt.stream().filter(e -> e == z).count());
    }

    @Test void shooterNeverHurt() {
        var p = player();
        nearby.add(p);
        fire(p);
        ticks(40);
        assertFalse(hurt.contains(p));
    }

    @Test void reachesItsRangeAndEnds() {
        fire(player());
        ticks(40);
        assertEquals(1, count(BlueRedPayload.RED_END));
        var end = sent.stream().filter(x -> x.kind() == BlueRedPayload.RED_END).findFirst().orElseThrow();
        assertEquals(101.5, end.z(), 1e-6, "ends exactly at its 100-block range");
    }

    @Test void shatterUnreadyBlueAndContinue() {
        blueOwner(false);
        fire(player());
        ticks(8);
        assertEquals(1, count(BlueRedPayload.BLUE_SHATTER));
        assertTrue(BlueRedManager.blues(world).isEmpty());
        assertEquals(0, count(BlueRedPayload.PURPLE));
        assertEquals(0, count(BlueRedPayload.RED_END), "Red flies on after shattering");
        ticks(40);
        assertEquals(1, count(BlueRedPayload.RED_END));
    }

    @Test void readyBlueStartsPurple() {
        var b = blueOwner(true);
        var a = player();
        fire(a);
        ticks(8);
        assertEquals(1, count(BlueRedPayload.PURPLE));
        var purple = sent.stream().filter(x -> x.kind() == BlueRedPayload.PURPLE).findFirst().orElseThrow();
        assertEquals(9, purple.ownerId());
        assertEquals(7, purple.otherId());
        assertEquals(22, purple.z(), 1e-6);
        assertTrue(BlueRedManager.blues(world).isEmpty());
        ticks(40);
        assertEquals(0, count(BlueRedPayload.RED_END), "Red ends in Purple");
    }

    @Test void otherDimensionBlueIgnored() {
        var nether = level();
        var b = player(9, nether, new Vec3(0, 65, 30), new Vec3(0, 0, -1));
        BlueRedManager.input(b, true);
        fire(player());
        ticks(40);
        BlueRedManager.tick(nether);
        assertEquals(0, count(BlueRedPayload.BLUE_SHATTER));
        assertEquals(1, BlueRedManager.blues(nether).size());
    }

    @Test void stopsAtUnloadedChunk() {
        for (int x = -2; x <= 2; x++) for (int z = 1; z <= 8; z++) unloaded.add(net.minecraft.world.level.ChunkPos.asLong(x, z));
        blueOwner(true); // at z=22, inside the unloaded chunks
        fire(player());
        ticks(40);
        assertEquals(0, count(BlueRedPayload.PURPLE));
        var end = sent.stream().filter(x -> x.kind() == BlueRedPayload.RED_END).findFirst().orElseThrow();
        assertTrue(end.z() < 16, "stops at the edge of loaded terrain, z=" + end.z());
    }
}
