package net.godwand.skill;

import net.godwand.item.GodWandItem;
import net.godwand.network.SilentGatePayload;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.util.*;

import static net.godwand.skill.SilentGateSequence.Phase.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SilentGateManagerTest {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }

    // ---- pure transitions ----
    @Test void phasesFollowTheClocks() {
        assertEquals(BUILD, SilentGateManager.next(BUILD, 479, false, 300, 200));
        assertEquals(WAIT, SilentGateManager.next(BUILD, 480, false, 300, 200));
        assertEquals(WAIT, SilentGateManager.next(WAIT, 779, false, 300, 200));
        assertEquals(BARRAGE, SilentGateManager.next(WAIT, 780, false, 300, 200));
        assertEquals(BARRAGE, SilentGateManager.next(WAIT, 310, true, 300, 200));
        assertEquals(BARRAGE, SilentGateManager.next(BARRAGE, 211, true, 300, 200));
        assertEquals(END, SilentGateManager.next(BARRAGE, 212, true, 300, 200));
        int cleared = SilentGateSequence.endTick(200) + SilentGateSequence.RETURN_TICKS;
        assertEquals(END, SilentGateManager.next(END, cleared - 1, true, 300, 200));
        assertNull(SilentGateManager.next(END, cleared, true, 300, 200));
    }

    @Test void onlyWaitCancelStartsCooldown() {
        assertFalse(SilentGateManager.cooldownOnCancel(BUILD));
        assertTrue(SilentGateManager.cooldownOnCancel(WAIT));
    }

    // ---- manager with mocks ----
    private final List<Object> sent = new ArrayList<>();
    private final long[] now = {1000};
    private ServerLevel world;
    private MockedStatic<GodWandItem> modes;
    private MockedStatic<SkillCooldowns> cooldowns;

    @BeforeEach void setUp() {
        SilentGateManager.clearAllTasks();
        sent.clear();
        SilentGateManager.sender = (level, pos, payload) -> sent.add(payload);
        world = mock(ServerLevel.class);
        when(world.getGameTime()).thenAnswer(i -> now[0]);
        when(world.dimension()).thenReturn(Level.OVERWORLD);
        modes = mockStatic(GodWandItem.class);
        modes.when(() -> GodWandItem.getSkillMode(any())).thenReturn(GodWandItem.MODE_SILENT_GATE);
        cooldowns = mockStatic(SkillCooldowns.class);
    }
    @AfterEach void tearDown() { modes.close(); cooldowns.close(); SilentGateManager.clearAllTasks(); }

    private ServerPlayer player() {
        var p = mock(ServerPlayer.class);
        var wand = mock(ItemStack.class);
        when(wand.getItem()).thenReturn(mock(GodWandItem.class));
        when(p.getUUID()).thenReturn(UUID.randomUUID());
        when(p.serverLevel()).thenReturn(world);
        when(p.isAlive()).thenReturn(true);
        when(p.position()).thenReturn(Vec3.ZERO);
        when(p.getItemInHand(any())).thenReturn(wand);
        return p;
    }
    private int lastPhase() {
        return sent.stream().filter(SilentGatePayload.class::isInstance).map(SilentGatePayload.class::cast)
                .reduce((a, b) -> b).orElseThrow().phase();
    }
    private void tickTo(long t) { now[0] = t; SilentGateManager.tick(world); }

    @Test void secondClickDuringBuildAndAnyClickDuringBarrageAreRefused() {
        var p = player();
        assertTrue(SilentGateManager.click(p, InteractionHand.MAIN_HAND));
        assertFalse(SilentGateManager.click(p, InteractionHand.MAIN_HAND), "BUILD ignores click 2");
        assertEquals(BUILD.ordinal(), lastPhase());
        tickTo(1480);
        assertEquals(WAIT.ordinal(), lastPhase());
        assertTrue(SilentGateManager.click(p, InteractionHand.MAIN_HAND), "WAIT click commands");
        assertEquals(BARRAGE.ordinal(), lastPhase());
        assertFalse(SilentGateManager.click(p, InteractionHand.MAIN_HAND));
        assertEquals(BARRAGE.ordinal(), lastPhase());
    }

    @Test void lockAndProtectionFollowThePhases() {
        var p = player();
        SilentGateManager.click(p, InteractionHand.MAIN_HAND);
        assertTrue(SilentGateManager.movementLocked(p));
        assertTrue(SilentGateManager.invulnerable(p));
        tickTo(1480);
        assertTrue(SilentGateManager.movementLocked(p));
        SilentGateManager.click(p, InteractionHand.MAIN_HAND);
        assertFalse(SilentGateManager.movementLocked(p));
        assertTrue(SilentGateManager.invulnerable(p));
        tickTo(1480 + 12 + 360);
        assertEquals(END.ordinal(), lastPhase());
        assertTrue(SilentGateManager.invulnerable(p));
        tickTo(1480 + SilentGateSequence.endTick(360) + SilentGateSequence.RETURN_TICKS);
        assertFalse(SilentGateManager.invulnerable(p));
        cooldowns.verify(() -> SkillCooldowns.start(eq(p), eq(7), anyInt()));
    }

    @Test void worldAllowsFourTasksAndOnePerCaster() {
        var first = player();
        for (int i = 0; i < 4; i++) assertTrue(SilentGateManager.click(i == 0 ? first : player(), InteractionHand.MAIN_HAND));
        assertFalse(SilentGateManager.click(player(), InteractionHand.MAIN_HAND), "fifth in the same world");
        SilentGateManager.clearAllTasks();
        assertTrue(SilentGateManager.click(first, InteractionHand.MAIN_HAND));
        assertFalse(SilentGateManager.click(first, InteractionHand.MAIN_HAND));
    }

    @Test void invalidCasterCancelsAtOnceAndOnlyWaitPaysCooldown() {
        var building = player();
        SilentGateManager.click(building, InteractionHand.MAIN_HAND);
        when(building.isAlive()).thenReturn(false);
        tickTo(1001);
        assertEquals(SilentGatePayload.CANCEL, lastPhase());
        assertFalse(SilentGateManager.invulnerable(building));
        cooldowns.verifyNoInteractions();

        var waiting = player();
        sent.clear(); now[0] = 1000;
        SilentGateManager.click(waiting, InteractionHand.MAIN_HAND);
        tickTo(1480);
        var empty = mock(ItemStack.class);
        when(waiting.getItemInHand(any())).thenReturn(empty);
        modes.when(() -> GodWandItem.getSkillMode(empty)).thenReturn(0);
        tickTo(1481);
        assertEquals(SilentGatePayload.CANCEL, lastPhase());
        assertFalse(SilentGateManager.invulnerable(waiting));
        cooldowns.verify(() -> SkillCooldowns.start(eq(waiting), eq(7), anyInt()));
    }

    @Test void disconnectAndWorldChangeCancel() {
        var gone = player(); SilentGateManager.click(gone, InteractionHand.MAIN_HAND);
        when(gone.hasDisconnected()).thenReturn(true); tickTo(1001);
        assertFalse(SilentGateManager.invulnerable(gone));
        var moved = player(); SilentGateManager.click(moved, InteractionHand.MAIN_HAND);
        when(moved.serverLevel()).thenReturn(mock(ServerLevel.class)); tickTo(1002);
        assertFalse(SilentGateManager.invulnerable(moved));
    }

    @Test void clientCancelNeedsMatchingStartAndOnlyWorksBeforeTheGateFires() {
        var p = player();
        SilentGateManager.click(p, InteractionHand.MAIN_HAND);
        SilentGateManager.cancelFromClient(p, 999);
        assertTrue(SilentGateManager.invulnerable(p));
        SilentGateManager.cancelFromClient(p, 1000);
        assertFalse(SilentGateManager.invulnerable(p));
        assertEquals(SilentGatePayload.CANCEL, lastPhase());
        SilentGateManager.click(p, InteractionHand.MAIN_HAND);
        tickTo(1480); SilentGateManager.click(p, InteractionHand.MAIN_HAND);
        SilentGateManager.cancelFromClient(p, 1000);
        assertTrue(SilentGateManager.invulnerable(p), "once the gate fires it cannot be called back");
        assertNotEquals(SilentGatePayload.CANCEL, lastPhase());
    }

    @Test void craterBudgetIsSharedWithinATickAndResetsNextTick() {
        SilentGateManager.clearAllTasks();
        assertEquals(96, SilentGateManager.budget(5));
        assertEquals(96, SilentGateManager.budget(5), "same tick, nothing spent yet");
        SilentGateManager.spendBudget(90);
        assertEquals(6, SilentGateManager.budget(5), "a second world in the same tick sees what is left");
        assertEquals(96, SilentGateManager.budget(6));
    }

    @Test void craterQueueDedupesOverlapsAndScansAtMost768PerTick() {
        when(world.isInWorldBounds(any(net.minecraft.core.BlockPos.class))).thenReturn(true);
        // hasChunkAt stays false: every entry is skipped for free, which used to let one tick walk the whole queue
        var at = new Vec3(0, 64, 0);
        for (int i = 0; i < 4; i++) SilentGateManager.queueCrater(world, player(), at.add(i * 9, 0, 0), 4); // 4 disjoint spheres
        int once = SilentGateManager.pendingCraters(world);
        for (int i = 0; i < 4; i++) SilentGateManager.queueCrater(world, player(), at.add(i * 9, 0, 0), 4);
        assertEquals(once, SilentGateManager.pendingCraters(world), "overlapping craters are not queued twice");
        assertTrue(once > 768);
        SilentGateManager.budget(77);
        SilentGateManager.drain(world);
        assertEquals(once - 768, SilentGateManager.pendingCraters(world));
    }

    @Test void drainDigsWithQuietFlagsAndSkipsUnbreakable() {
        var stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        var bedrock = net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState();
        var rock = new net.minecraft.core.BlockPos(0, 64, 0);
        when(world.isInWorldBounds(any(net.minecraft.core.BlockPos.class))).thenReturn(true);
        when(world.hasChunkAt(any(net.minecraft.core.BlockPos.class))).thenReturn(true);
        when(world.getBlockState(any(net.minecraft.core.BlockPos.class))).thenReturn(bedrock);
        when(world.getBlockState(rock)).thenReturn(stone);
        SilentGateManager.queueCrater(world, player(), new Vec3(0.5, 64.5, 0.5), 1);
        SilentGateManager.budget(88);
        SilentGateManager.drain(world);
        int flags = net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE
                | net.minecraft.world.level.block.Block.UPDATE_SUPPRESS_DROPS;
        verify(world).setBlock(rock, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), flags);
        verify(world, times(1)).setBlock(any(net.minecraft.core.BlockPos.class), any(), anyInt());
        verify(world, never()).destroyBlock(any(net.minecraft.core.BlockPos.class), anyBoolean(), any());
        assertEquals(0, SilentGateManager.pendingCraters(world));
    }
}
