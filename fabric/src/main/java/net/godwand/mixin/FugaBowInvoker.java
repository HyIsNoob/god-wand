package net.godwand.mixin;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(HumanoidModel.class)
public interface FugaBowInvoker {
 @Invoker("poseLeftArm") void godwand$vanillaLeftBow(LivingEntity entity);
}
