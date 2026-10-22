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

        // Hiệu ứng luồng gió khí động học cuộn xoáy theo mũi khoan (Helical Wind Vortex)
        double speed = velocity.length();
        if (speed > 0.05) {
            Vec3d dir = velocity.normalize();
            Vec3d up = Math.abs(dir.y) < 0.99 ? new Vec3d(0, 1, 0) : new Vec3d(0, 0, 1);
            Vec3d tangent = dir.crossProduct(up).normalize();
            Vec3d binormal = dir.crossProduct(tangent).normalize();

            double baseRadius = 0.35 + this.chargeLevel * 0.16;
            int arms = 3;

            if (this.getWorld().isClient()) {
                for (int arm = 0; arm < arms; arm++) {
                    double theta = this.age * 0.75 + (arm * 2.0 * Math.PI / arms);
                    Vec3d offset = tangent.multiply(baseRadius * Math.cos(theta))
                            .add(binormal.multiply(baseRadius * Math.sin(theta)));
                    this.getWorld().addParticle(ParticleTypes.CLOUD,
                            this.getX() + offset.x, this.getY() + offset.y, this.getZ() + offset.z,
                            -dir.x * 0.1, -dir.y * 0.1, -dir.z * 0.1);
                }
            } else {
                ServerWorld sw = (ServerWorld) this.getWorld();
                for (int arm = 0; arm < arms; arm++) {
                    double theta = this.age * 0.75 + (arm * 2.0 * Math.PI / arms);
                    Vec3d offset = tangent.multiply(baseRadius * Math.cos(theta))
                            .add(binormal.multiply(baseRadius * Math.sin(theta)));
                    double px = this.getX() + offset.x;
                    double py = this.getY() + offset.y;
                    double pz = this.getZ() + offset.z;

                    sw.spawnParticles(ParticleTypes.CLOUD, px, py, pz, 1, -dir.x * 0.05, -dir.y * 0.05, -dir.z * 0.05, 0.02);
                    sw.spawnParticles(new DustParticleEffect(new Vector3f(0.85f, 0.80f, 0.65f), 1.1f),
                            px, py, pz, 1, 0, 0, 0, 0.0);

                    if (this.chargeLevel >= 3 && arm == 0) {
                        sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, px, py, pz, 1, 0.02, 0.02, 0.02, 0.02);
                    }
                }

                // Mach Cone vapor ring behind the bullet every 2 ticks
                if (this.age % 2 == 0) {
                    Vec3d tailPos = this.getPos().subtract(dir.multiply(0.6));
                    sw.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, tailPos.x, tailPos.y, tailPos.z,
                            1, -dir.x * 0.15, -dir.y * 0.15, -dir.z * 0.15, 0.01);
                }

                if (this.age > 240) {
                    this.discard();
                }
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
