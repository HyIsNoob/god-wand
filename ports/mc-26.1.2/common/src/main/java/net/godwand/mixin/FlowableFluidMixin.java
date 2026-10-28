package net.godwand.mixin;
import net.godwand.spatial.SpatialDisplacementManager;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value=FlowingFluid.class,remap=false)
public abstract class FlowableFluidMixin {
 private static boolean protectedPosition(BlockGetter world,BlockPos pos) {
  return world instanceof ServerLevel server&&SpatialDisplacementManager.isPositionDisplaced(server.dimension(),pos);
 }
 @Inject(method="canPassThrough",at=@At("HEAD"),cancellable=true)
 private void godwand$flow(BlockGetter world,Fluid fluid,BlockPos from,BlockState old,Direction dir,BlockPos to,BlockState state,FluidState liquid,CallbackInfoReturnable<Boolean> ci) {
  if(protectedPosition(world,to))ci.setReturnValue(false);
 }
 @Inject(method="canMaybePassThrough",at=@At("HEAD"),cancellable=true)
 private void godwand$down(BlockGetter world,BlockPos from,BlockState old,Direction dir,BlockPos to,BlockState state,FluidState liquid,CallbackInfoReturnable<Boolean> ci) {
  if(protectedPosition(world,to))ci.setReturnValue(false);
 }
 @Inject(method="spreadTo",at=@At("HEAD"),cancellable=true)
 private void godwand$destination(LevelAccessor world,BlockPos to,BlockState state,Direction dir,FluidState liquid,CallbackInfo ci) {
  if(protectedPosition(world,to))ci.cancel();
 }
}
