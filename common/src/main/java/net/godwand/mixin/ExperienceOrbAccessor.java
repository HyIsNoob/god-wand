package net.godwand.mixin;

import net.minecraft.world.entity.ExperienceOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** How many identical orbs vanilla already merged into this one, so pooling keeps every point of experience. */
@Mixin(ExperienceOrb.class)
public interface ExperienceOrbAccessor {
    @Accessor("count") int godwand$getCount();
}
