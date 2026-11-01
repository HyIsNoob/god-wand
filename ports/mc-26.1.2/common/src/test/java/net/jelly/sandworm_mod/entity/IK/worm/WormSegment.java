package net.jelly.sandworm_mod.entity.IK.worm;
public abstract class WormSegment extends net.minecraft.world.entity.Entity {
    protected WormSegment() { super(null,null); }
    public abstract net.minecraft.world.entity.Entity getOwner();
}
