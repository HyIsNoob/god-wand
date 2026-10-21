package net.godwand.mixin;

import net.godwand.spatial.SpatialDisplacementManager;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FlowableFluid.class)
public abstract class FlowableFluidMixin {
    @Inject(method = "canFlow", at = @At("HEAD"), cancellable = true)
    private void godwand$preventFlowIntoDisplacedArea(
            BlockView world, BlockPos fluidPos, BlockState fluidBlockState,
            Direction flowDirection, BlockPos targetPos, BlockState targetBlockState,
            FluidState targetFluidState, Fluid fluid,
            CallbackInfoReturnable<Boolean> cir) {
        if (SpatialDisplacementManager.isPositionDisplaced(targetPos)) {
            cir.setReturnValue(false);
        }
    }
}
