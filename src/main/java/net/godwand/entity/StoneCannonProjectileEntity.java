package net.godwand.entity;

import net.godwand.GodWandMod;
import net.godwand.skill.StoneCannonManager;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

public class StoneCannonProjectileEntity extends ProjectileEntity {
    private int chargeLevel = 1;

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
        this.chargeLevel = Math.max(1, Math.min(4, level));
    }

    public int getChargeLevel() {
        return this.chargeLevel;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("ChargeLevel", this.chargeLevel);
        nbt.putInt("Age", this.age);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.chargeLevel = nbt.getInt("ChargeLevel");
        this.age = nbt.getInt("Age");
    }

    @Override
    public void tick() {
        super.tick();
        this.age++;

        Vec3d velocity = this.getVelocity();

        HitResult hit = ProjectileUtil.getCollision(this, this::canHit);
        if (hit.getType() != HitResult.Type.MISS) {
            onCollision(hit);
            return;
        }

        this.setPosition(this.getX() + velocity.x, this.getY() + velocity.y, this.getZ() + velocity.z);

        // Luồng khí động học chính được hiển thị mượt mà bằng 3D Wind Ribbon trên Client (StoneCannonEntityRenderer).
        // Server chỉ tạo thêm tia lửa ma sát cao tần và bụi đá siêu mịn, loại bỏ hoàn toàn hạt khói/mây vanilla.
        double speed = velocity.length();
        if (speed > 0.05 && !this.getWorld().isClient()) {
            ServerWorld sw = (ServerWorld) this.getWorld();
            Vec3d dir = velocity.normalize();

            // Bụi đá mài mòn nén siêu âm
            sw.spawnParticles(new DustParticleEffect(new Vector3f(0.82f, 0.78f, 0.65f), 1.0f),
                    this.getX(), this.getY(), this.getZ(), 1, 0.05, 0.05, 0.05, 0.0);

            // Tia lửa điện ma sát cực hạn ở cấp độ cao
            if (this.chargeLevel >= 3 && this.age % 2 == 0) {
                sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY(), this.getZ(),
                        2, 0.08, 0.08, 0.08, 0.02);
            }

            if (this.age > 240) {
                this.discard();
            }
        }
    }

    @Override
    protected boolean canHit(net.minecraft.entity.Entity entity) {
        if (entity == this.getOwner() && this.age < 5) {
            return false;
        }
        return super.canHit(entity);
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);
        if (!this.getWorld().isClient()) {
            StoneCannonManager.onProjectileImpact(this, hitResult);
            this.discard();
        }
    }
}
