package net.godwand.skill;

import net.godwand.item.GodWandItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** The God Wand advancements (data/godwand/advancement, owner 2026-11-20): all but the root are granted from here. */
public final class GodWandAdvancements {
    private GodWandAdvancements() {}

    public static void award(Entity who, String id) {
        if (!(who instanceof ServerPlayer player) || player.getServer() == null) return;
        var advancement = player.getServer().getAdvancements().get(ResourceLocation.fromNamespaceAndPath("godwand", id));
        if (advancement != null) player.getAdvancements().award(advancement, "done");
    }

    /** A skill used (its cooldown started): its advancement pops once the skill has played out, after its cutscene (owner, 2026-11-20),
     *  not the moment it is cast. Blue, Red and Purple are awarded where each happens. */
    public static void used(ServerPlayer player, int mode) {
        if (mode < 0 || mode >= GodWandItem.MODE_BLUE_RED || player.getServer() == null) return;
        PENDING.add(new Pending(player, "use_" + mode, player.getServer().getTickCount() + playsFor(mode)));
    }

    /** Roughly how long a skill plays on after its cooldown starts, in ticks. */
    static int playsFor(int mode) {
        return switch (mode) {
            case GodWandItem.MODE_FUGA -> FugaSequence.END_TICK - FugaSequence.CHARGE_TICKS + 10;
            case GodWandItem.MODE_ATOMIC -> AtomicSequence.END - AtomicSequence.WAIT_TICK + 10;
            case GodWandItem.MODE_SUPERNOVA -> SupernovaSequence.SKY_CLOSE_START + 40;
            case GodWandItem.MODE_ARCANE_ORB -> 100;
            case GodWandItem.MODE_RATIO_COLLAPSE -> 80;
            case GodWandItem.MODE_STONE_CANNON -> 40;
            default -> 20;
        };
    }

    private record Pending(ServerPlayer player, String id, int due) {}
    private static final java.util.List<Pending> PENDING = new java.util.ArrayList<>();

    /** Once a server tick: hands out the advancements whose skill has finished playing. */
    public static void tick(net.minecraft.server.MinecraftServer server) {
        if (server == null || PENDING.isEmpty()) return;
        int now = server.getTickCount();
        PENDING.removeIf(p -> {
            if (p.player().hasDisconnected()) return true;
            if (now < p.due()) return false;
            award(p.player(), p.id());
            return true;
        });
    }

    /** A spell killed something: Singularity's and the Earth Drill's kills have their own advancements (the mode the killer holds). */
    public static void spellKill(Entity killer, LivingEntity dead) {
        if (!(killer instanceof ServerPlayer player)) return;
        var stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof GodWandItem)) return;
        int mode = GodWandItem.getSkillMode(stack);
        if (mode == GodWandItem.MODE_ARCANE_ORB || mode == GodWandItem.MODE_RATIO_COLLAPSE) award(player, "kill_" + mode);
    }
}
