package net.godwand.mixin;

import net.godwand.spatial.SpatialDisplacementManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FlowingFluid.class)
public abstract class FlowableFluidMixin {
    @Inject(method = "canSpreadTo", at = @At("HEAD"), cancellable = true)
    private void godwand$preventFlowIntoDisplacedArea(
            BlockGetter world, BlockPos fluidPos, BlockState fluidBlockState,
            Direction flowDirection, BlockPos targetPos, BlockState targetBlockState,
            FluidState targetFluidState, Fluid fluid,
            CallbackInfoReturnable<Boolean> cir) {
        if (world instanceof ServerLevel serverWorld) {
            if (SpatialDisplacementManager.isPositionDisplaced(serverWorld.dimension(), targetPos)) {
                cir.setReturnValue(false);
            }
        }
    }
}
