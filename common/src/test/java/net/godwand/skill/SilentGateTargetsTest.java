package net.godwand.skill;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SilentGateTargetsTest {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }

    private static <T extends LivingEntity> T live(Class<T> type) {
        var e = mock(type);
        when(e.isAlive()).thenReturn(true);
        when(e.position()).thenReturn(Vec3.ZERO);
        return e;
    }

    @Test void hostileModeOnlyAcceptsLivingEnemies() {
        var caster = mock(ServerPlayer.class);
        assertTrue(SilentGateTargets.eligible(live(Monster.class), caster, "hostile"));
        assertFalse(SilentGateTargets.eligible(live(Villager.class), caster, "hostile"));
        assertFalse(SilentGateTargets.eligible(live(net.minecraft.world.entity.animal.Cow.class), caster, "hostile"));
        assertFalse(SilentGateTargets.eligible(live(Player.class), caster, "hostile"));
        var spectator = live(Monster.class); when(spectator.isSpectator()).thenReturn(true);
        assertFalse(SilentGateTargets.eligible(spectator, caster, "hostile"));
    }

    @Test void allModeSparesCasterOwnPetsDeadAndSpectators() {
        var caster = mock(ServerPlayer.class);
        assertTrue(SilentGateTargets.eligible(live(Player.class), caster, "all"));
        assertTrue(SilentGateTargets.eligible(live(Villager.class), caster, "all"));
        assertFalse(SilentGateTargets.eligible(caster, caster, "all"));
        var pet = live(TamableAnimal.class); when(pet.isOwnedBy(caster)).thenReturn(true);
        assertFalse(SilentGateTargets.eligible(pet, caster, "all"));
        var stray = live(TamableAnimal.class);
        assertTrue(SilentGateTargets.eligible(stray, caster, "all"));
        var dead = live(Villager.class); when(dead.isAlive()).thenReturn(false);
        assertFalse(SilentGateTargets.eligible(dead, caster, "all"));
        var spectator = live(Player.class); when(spectator.isSpectator()).thenReturn(true);
        assertFalse(SilentGateTargets.eligible(spectator, caster, "all"));
    }

    @Test void pickFavoursTheBossButHitsEveryone() {
        var boss = live(Monster.class); when(boss.getMaxHealth()).thenReturn(300f);
        var a = live(Monster.class); when(a.getMaxHealth()).thenReturn(20f);
        var b = live(Monster.class); when(b.getMaxHealth()).thenReturn(20f);
        var picks = SilentGateTargets.pick(List.of(a, b, boss), Vec3.ZERO, 10, 42);
        assertEquals(10, picks.size());
        assertTrue(Collections.frequency(picks, boss) >= 3);
        assertTrue(Collections.frequency(picks, a) >= 1 && Collections.frequency(picks, b) >= 1);
        assertEquals(picks, SilentGateTargets.pick(List.of(a, b, boss), Vec3.ZERO, 10, 42), "same seed, same plan");
    }

    @Test void pickWithFewShotsTakesTopRankedAndEmptyStaysEmpty() {
        var boss = live(Monster.class); when(boss.getMaxHealth()).thenReturn(300f);
        var a = live(Monster.class); when(a.getMaxHealth()).thenReturn(20f);
        assertEquals(List.of(boss), SilentGateTargets.pick(List.of(a, boss), Vec3.ZERO, 1, 1));
        assertTrue(SilentGateTargets.pick(List.of(), Vec3.ZERO, 5, 1).isEmpty());
    }
}
