package net.godwand.entity;

import net.godwand.GodWandMod;
import net.godwand.skill.StoneCannonManager;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

public class StoneCannonProjectileEntity extends ProjectileEntity {
    private static final net.minecraft.entity.data.TrackedData<Integer> CHARGE_LEVEL =
            DataTracker.registerData(StoneCannonProjectileEntity.class, net.minecraft.entity.data.TrackedDataHandlerRegistry.INTEGER);
    private final java.util.Set<java.util.UUID> hitEntities = new java.util.HashSet<>();

    public boolean markHit(LivingEntity entity) {
        return hitEntities.add(entity.getUuid());
    }

    public StoneCannonProjectileEntity(EntityType<? extends ProjectileEntity> entityType, World world) {
        super(entityType, world);
        this.noClip = false;
    }

    public StoneCannonProjectileEntity(World world, LivingEntity owner, int chargeLevel) {
        super(GodWandMod.STONE_CANNON_ENTITY_TYPE, world);
        this.setOwner(owner);
        this.setChargeLevel(chargeLevel);
        this.noClip = false;
    }

    public void setChargeLevel(int level) {
        this.dataTracker.set(CHARGE_LEVEL, Math.max(1, Math.min(4, level)));
    }

    public int getChargeLevel() {
        return this.dataTracker.get(CHARGE_LEVEL);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(CHARGE_LEVEL, 1);
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("ChargeLevel", getChargeLevel());
        nbt.putInt("Age", this.age);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        setChargeLevel(nbt.getInt("ChargeLevel"));
        this.age = nbt.getInt("Age");
    }

    @Override
    public void tick() {
        super.tick();

        Vec3d velocity = this.getVelocity();

        Vec3d start = getPos();
        Vec3d end = start.add(velocity);
        if (!getWorld().isClient()) {
            if (age > 240 || velocity.lengthSquared() < 0.001) {
                discard();
                return;
            }
            var hit = net.minecraft.entity.projectile.ProjectileUtil.getCollision(this, this::canHit);
            if (hit.getType() != net.minecraft.util.hit.HitResult.Type.MISS) {
                setPosition(hit.getPos());
                StoneCannonManager.onProjectileImpact(this, hit);
                discard();
                return;
            }
        }
        setPosition(end);

        // Luồng khí động học chính được hiển thị mượt mà bằng 3D Wind Ribbon trên Client (StoneCannonEntityRenderer).
        // Server chỉ tạo thêm tia lửa ma sát cao tần và bụi đá siêu mịn, loại bỏ hoàn toàn hạt khói/mây vanilla.
        double speed = velocity.length();
        if (speed > 0.05 && !this.getWorld().isClient()) {
            ServerWorld sw = (ServerWorld) this.getWorld();

            // Bụi đá mài mòn nén siêu âm
            sw.spawnParticles(new DustParticleEffect(new Vector3f(0.82f, 0.78f, 0.65f), 1.0f),
                    this.getX(), this.getY(), this.getZ(), 1, 0.05, 0.05, 0.05, 0.0);

            // Tia lửa điện ma sát cực hạn ở cấp độ cao
            if (getChargeLevel() >= 3 && this.age % 2 == 0) {
                sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY(), this.getZ(),
                        2, 0.08, 0.08, 0.08, 0.02);
            }

        }
    }

}
