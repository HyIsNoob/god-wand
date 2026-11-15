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

    @BeforeEach void setUp() {
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
        world = mock(ServerLevel.class);
        when(world.dimension()).thenReturn(Level.OVERWORLD);
        modes = mockStatic(GodWandItem.class);
        modes.when(() -> GodWandItem.getSkillMode(any())).thenReturn(GodWandItem.MODE_BLUE_RED);
        cooldowns = mockStatic(SkillCooldowns.class);
        cooldowns.when(() -> SkillCooldowns.ready(any(), anyInt())).thenReturn(true);
    }
    @AfterEach void tearDown() { modes.close(); cooldowns.close(); BlueRedManager.clearAll(); }

    ServerPlayer player() {
        var p = mock(ServerPlayer.class);
        var wand = mock(ItemStack.class);
        when(wand.getItem()).thenReturn(mock(GodWandItem.class));
        when(p.getUUID()).thenReturn(UUID.randomUUID());
        when(p.getId()).thenReturn(7);
        when(p.serverLevel()).thenReturn(world);
        when(p.level()).thenReturn(world);
        when(p.isAlive()).thenReturn(true);
        when(p.getMainHandItem()).thenReturn(wand);
        when(p.getEyePosition()).thenReturn(new Vec3(0, 65, 0));
        when(p.getLookAngle()).thenReturn(new Vec3(0, 0, 1));
        when(p.position()).thenReturn(new Vec3(0, 64, 0));
        when(p.getDeltaMovement()).thenReturn(Vec3.ZERO);
        return p;
    }
    /** A 20-health mob standing in Blue's core (Blue sits at 0,65,8). */
    LivingEntity zombie() {
        var z = mock(LivingEntity.class);
        float[] h = {20};
        health.put(z, h);
        when(z.getHealth()).thenAnswer(i -> h[0]);
        when(z.isAlive()).thenAnswer(i -> h[0] > 0);
        when(z.position()).thenReturn(new Vec3(0, 64.5, 8));
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
}
