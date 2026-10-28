package net.godwand.client;

import net.godwand.item.GodWandItem;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;

/** Immutable cast identity extracted alongside each humanoid render state. */
public final class AtomicPoseFrames {
    public record Pose(int entityId, boolean charging, boolean rightHand, double age, AtomicRenderer.Frame frame) {}
    private static final Map<HumanoidRenderState,Pose> POSES = Collections.synchronizedMap(new WeakHashMap<>());
    public static void capture(LivingEntity entity,HumanoidRenderState state,float delta) {
        var item = entity.getUseItem();
        var frame = AtomicRenderer.frameFor(entity.getId(),delta);
        boolean charging = item.getItem() instanceof GodWandItem && GodWandItem.getSkillMode(item)==GodWandItem.MODE_ATOMIC;
        if(!charging && frame==null) { POSES.remove(state); return; }
        boolean right = (entity.getUsedItemHand()==net.minecraft.world.InteractionHand.MAIN_HAND)
                ==(entity.getMainArm()==net.minecraft.world.entity.HumanoidArm.RIGHT);
        POSES.put(state,new Pose(entity.getId(),charging,right,
                frame==null?entity.getTicksUsingItem()+delta:frame.age(),frame));
    }
    public static Pose get(HumanoidRenderState state) { return POSES.get(state); }
}
