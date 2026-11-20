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

    /** A skill used (its cooldown started). Blue, Red and Purple are awarded where each happens. */
    public static void used(ServerPlayer player, int mode) {
        if (mode >= 0 && mode < GodWandItem.MODE_BLUE_RED) award(player, "use_" + mode);
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
