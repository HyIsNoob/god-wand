package net.godwand.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** A restriction travels with the stack when traded, copied or saved. */
public final class WandSkillLock {
    private WandSkillLock() {}
    public static boolean isLocked(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.contains("GodWandLocked");
    }
    public static void lock(ItemStack stack, int mode) {
        if (!(stack.getItem() instanceof GodWandItem) || mode < 0 || mode >= GodWandItem.TOTAL_MODES)
            throw new IllegalArgumentException("Expected a wand and a valid skill");
        unlock(stack);
        GodWandItem.setSkillMode(stack, mode);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt("GodWandLocked", 1));
    }
    public static void unlock(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove("GodWandLocked"));
    }

    /** The server's disabled skills as last stamped on this wand (bit n: skill n); what the client's wheel reads. */
    public static int disabledMask(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null || !data.contains("GodWandDisabled") ? 0 : data.copyTag().getInt("GodWandDisabled");
    }

    /** Stamps the server's disabled skills on a wand, and moves it off a skill that has been turned off (unless an admin locked it there). */
    public static void stampDisabled(ItemStack stack, int mask) {
        if (!(stack.getItem() instanceof GodWandItem)) return;
        if (disabledMask(stack) != mask) {
            if (mask == 0) CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove("GodWandDisabled"));
            else CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt("GodWandDisabled", mask));
        }
        int mode = GodWandItem.getSkillMode(stack);
        if (mode >= 0 && (mask >> mode & 1) != 0 && !isLocked(stack))
            for (int m = 0; m < GodWandItem.TOTAL_MODES; m++) if ((mask >> m & 1) == 0) { GodWandItem.setSkillMode(stack, m); break; }
    }

    /** Every held wand in this level carries the server's current disabled skills (cheap: only when it changed). */
    public static void stampHeld(net.minecraft.server.level.ServerLevel level) {
        int mask = net.godwand.config.GodWandConfig.SERVER.disabledMask();
        for (var player : level.players()) {
            stampDisabled(player.getMainHandItem(), mask);
            stampDisabled(player.getOffhandItem(), mask);
        }
    }
}
