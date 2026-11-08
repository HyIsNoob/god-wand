package net.godwand.skill;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Silent Gate target filter and shot assignment. Pure apart from reading entity state. */
public final class SilentGateTargets {
    private SilentGateTargets() {}

    public static boolean eligible(LivingEntity e, ServerPlayer caster, String mode) {
        if (e == null || e == caster || !e.isAlive() || e.isSpectator()) return false;
        if ("all".equals(mode))
            return !(e instanceof TamableAnimal pet && pet.isOwnedBy(caster)) && !(e instanceof Player p && p.isCreative());
        return e instanceof Enemy;
    }

    /** One target per shot: biggest max health first (bosses), then nearest. Every candidate is hit once when there are
     *  enough shots; the rest are shared out by rank (largest remainder), then shuffled by the seed. */
    public static List<LivingEntity> pick(List<LivingEntity> candidates, Vec3 from, int count, long seed) {
        var ranked = new ArrayList<>(candidates.stream().filter(LivingEntity::isAlive).toList()); // refreshed only every 10 ticks
        if (ranked.isEmpty() || count <= 0) return List.of();
        ranked.sort(Comparator.<LivingEntity>comparingDouble(e -> -e.getMaxHealth())
                .thenComparingDouble(e -> e.position().distanceToSqr(from)));
        int n = Math.min(ranked.size(), count);
        int[] quota = new int[n];
        Arrays.fill(quota, 1);
        int extra = count - n, weights = n * (n + 1) / 2, given = 0;
        double[] rest = new double[n];
        for (int i = 0; i < n; i++) {
            double share = extra * (n - i) / (double) weights;
            quota[i] += (int) share;
            given += (int) share;
            rest[i] = share - (int) share;
        }
        for (; given < extra; given++) {
            int best = 0;
            for (int i = 1; i < n; i++) if (rest[i] > rest[best]) best = i;
            quota[best]++;
            rest[best] = -1;
        }
        var out = new ArrayList<LivingEntity>(count);
        for (int i = 0; i < n; i++) for (int q = 0; q < quota[i]; q++) out.add(ranked.get(i));
        Collections.shuffle(out, new Random(seed));
        return out;
    }

    /** The featured kill target: highest max health, ties nearest to {@code from}; candidates with sky access win over buried ones. Null when empty. */
    public static <T> T featured(List<T> candidates, java.util.function.ToDoubleFunction<T> maxHealth,
            java.util.function.ToDoubleFunction<T> distanceSq, java.util.function.Predicate<T> skyAccess) {
        var open = candidates.stream().filter(skyAccess).toList();
        return (open.isEmpty() ? candidates : open).stream()
                .min(Comparator.<T>comparingDouble(e -> -maxHealth.applyAsDouble(e)).thenComparingDouble(distanceSq)).orElse(null);
    }
}
