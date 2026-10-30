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
}
