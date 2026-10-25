package net.godwand.util;

import com.mojang.math.Transformation;
import net.godwand.mixin.BlockDisplayAccessor;
import net.godwand.mixin.DisplayAccessor;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;

public class DisplayHelper {
    public static void setTransformation(Display display, Transformation transformation) {
        ((DisplayAccessor) display).godwand$setTransformation(transformation);
    }

    public static void setInterpolationDuration(Display display, int duration) {
        ((DisplayAccessor) display).godwand$setInterpolationDuration(duration);
    }

    public static void setInterpolationDelay(Display display, int delay) {
        ((DisplayAccessor) display).godwand$setInterpolationDelay(delay);
    }

    public static void setBrightnessOverride(Display display, Brightness brightness) {
        ((DisplayAccessor) display).godwand$setBrightnessOverride(brightness);
    }

    public static void setGlowColorOverride(Display display, int glowColorOverride) {
        ((DisplayAccessor) display).godwand$setGlowColorOverride(glowColorOverride);
    }

    public static void setBlockState(Display.BlockDisplay display, BlockState state) {
        ((BlockDisplayAccessor) display).godwand$setBlockState(state);
    }
}
