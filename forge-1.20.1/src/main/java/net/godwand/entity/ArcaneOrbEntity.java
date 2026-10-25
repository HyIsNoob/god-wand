package net.godwand.entity;

import net.minecraft.util.Mth;

import net.godwand.GodWandMod;
import net.godwand.skill.ShockwaveSlicer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class ArcaneOrbEntity extends Projectile {
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> CHARGE_LEVEL =
            SynchedEntityData.defineId(ArcaneOrbEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);

    public ArcaneOrbEntity(EntityType<? extends Projectile> entityType, Level world) {
        super(entityType, world);
        this.noPhysics = false;
    }

    public ArcaneOrbEntity(Level world, LivingEntity owner, int chargeLevel) {
        super(GodWandMod.ARCANE_ORB_ENTITY_TYPE, world);
        this.setOwner(owner);
        this.setChargeLevel(chargeLevel);
        this.noPhysics = false;
    }

    public void setChargeLevel(int level) {
        this.entityData.set(CHARGE_LEVEL, Mth.clamp(level, 1, 3));
    }

    public int getChargeLevel() {
        return this.entityData.get(CHARGE_LEVEL);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(CHARGE_LEVEL, 1);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putInt("ChargeLevel", getChargeLevel());
        nbt.putInt("Age", this.tickCount);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        setChargeLevel(nbt.getInt("ChargeLevel"));
        this.tickCount = nbt.getInt("Age");
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {
        return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void tick() {
        super.tick();
        this.tickCount++;

        Vec3 velocity = this.getDeltaMovement();

        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS) {
            onHit(hit);
            return;
        }

        this.setPos(this.getX() + velocity.x, this.getY() + velocity.y, this.getZ() + velocity.z);

        if (this.level().isClientSide()) {
            this.level().addParticle(new DustParticleOptions(new Vector3f(0.0f, 0.94f, 1.0f), 1.3f),
                    this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
            this.level().addParticle(ParticleTypes.REVERSE_PORTAL,
                    this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
        } else {
            ServerLevel sw = (ServerLevel) this.level();
            // Cyan & Amethyst dual particles
            sw.sendParticles(new DustParticleOptions(new Vector3f(0.0f, 0.94f, 1.0f), 1.2f),
                    this.getX(), this.getY(), this.getZ(), 4, 0.1, 0.1, 0.1, 0.0);
            sw.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    this.getX(), this.getY(), this.getZ(), 3, 0.1, 0.1, 0.1, 0.02);

            if (this.tickCount > 200) {
                this.discard();
            }
        }
    }

    @Override
    protected boolean canHitEntity(net.minecraft.world.entity.Entity entity) {
        if (entity == this.getOwner() && this.tickCount < 5) {
            return false;
        }
        return super.canHitEntity(entity);
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (!this.level().isClientSide()) {
            double radius = switch (getChargeLevel()) {
                case 1 -> 5.0;
                case 2 -> 10.0;
                default -> 16.0; // Calamity Level 3
            };
            BlockPos center = hitResult instanceof BlockHitResult bhr ? bhr.getBlockPos() : this.blockPosition();
            ServerPlayer caster = (this.getOwner() instanceof ServerPlayer spe) ? spe : null;
            ShockwaveSlicer.queueCalamityDetonation((ServerLevel) this.level(), center, radius, caster);
            this.discard();
        }
    }
}
