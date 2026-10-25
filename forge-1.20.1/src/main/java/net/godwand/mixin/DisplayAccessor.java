package net.godwand.mixin;

import com.mojang.math.Transformation;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.class)
public interface DisplayAccessor {
    @Invoker("setTransformation")
    void godwand$setTransformation(Transformation transformation);

    @Invoker("setInterpolationDuration")
    void godwand$setInterpolationDuration(int duration);

    @Invoker("setInterpolationDelay")
    void godwand$setInterpolationDelay(int delay);

    @Invoker("setBrightnessOverride")
    void godwand$setBrightnessOverride(Brightness brightness);

    @Invoker("setGlowColorOverride")
    void godwand$setGlowColorOverride(int glowColorOverride);
}
