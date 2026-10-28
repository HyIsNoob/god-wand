package net.godwand.entity;

import net.godwand.GodWandMod;
import net.godwand.skill.StoneCannonManager;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class StoneCannonProjectileEntity extends Projectile {
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> CHARGE_LEVEL =
            SynchedEntityData.defineId(StoneCannonProjectileEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    private final java.util.Set<java.util.UUID> hitEntities = new java.util.HashSet<>();

    public boolean markHit(LivingEntity entity) {
        return hitEntities.add(entity.getUUID());
    }

    public StoneCannonProjectileEntity(EntityType<? extends Projectile> entityType, Level world) {
        super(entityType, world);
        this.noPhysics = false;
    }

    public StoneCannonProjectileEntity(Level world, LivingEntity owner, int chargeLevel) {
        super(GodWandMod.STONE_CANNON_ENTITY_TYPE, world);
        this.setOwner(owner);
        this.setChargeLevel(chargeLevel);
        this.noPhysics = false;
    }

    public void setChargeLevel(int level) {
        this.entityData.set(CHARGE_LEVEL, Math.max(1, Math.min(4, level)));
    }

    public int getChargeLevel() {
        return this.entityData.get(CHARGE_LEVEL);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(CHARGE_LEVEL, 1);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putInt("ChargeLevel", getChargeLevel());
        nbt.putInt("Age", this.tickCount);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput nbt) {
        super.readAdditionalSaveData(nbt);
        setChargeLevel(nbt.getIntOr("ChargeLevel", 1));
        this.tickCount = nbt.getIntOr("Age", 0);
    }

    @Override
    public void tick() {
        super.tick();

        Vec3 velocity = this.getDeltaMovement();

        Vec3 start = position();
        Vec3 end = start.add(velocity);
        if (!level().isClientSide()) {
            if (tickCount > 240 || velocity.lengthSqr() < 0.001) {
                discard();
                return;
            }
            var hit = net.minecraft.world.entity.projectile.ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                setPos(hit.getLocation());
                StoneCannonManager.onProjectileImpact(this, hit);
                discard();
                return;
            }
        }
        setPos(end);

        // Luồng khí động học chính được hiển thị mượt mà bằng 3D Wind Ribbon trên Client (StoneCannonEntityRenderer).
        // Server chỉ tạo thêm tia lửa ma sát cao tần và bụi đá siêu mịn, loại bỏ hoàn toàn hạt khói/mây vanilla.
        double speed = velocity.length();
        if (speed > 0.05 && !this.level().isClientSide()) {
            ServerLevel sw = (ServerLevel) this.level();

            // Bụi đá mài mòn nén siêu âm
            sw.sendParticles(new DustParticleOptions(net.godwand.GodWandMod.colorFromVector3f(new Vector3f(0.82f, 0.78f, 0.65f)), 1.0f),
                    this.getX(), this.getY(), this.getZ(), 1, 0.05, 0.05, 0.05, 0.0);

            // Tia lửa điện ma sát cực hạn ở cấp độ cao
            if (getChargeLevel() >= 3 && this.tickCount % 2 == 0) {
                sw.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY(), this.getZ(),
                        2, 0.08, 0.08, 0.08, 0.02);
            }

        }
    }

}

