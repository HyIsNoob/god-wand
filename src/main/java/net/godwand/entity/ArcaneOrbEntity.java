package net.godwand.entity;

import net.godwand.GodWandMod;
import net.godwand.skill.ShockwaveSlicer;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

public class ArcaneOrbEntity extends ProjectileEntity {
    private int chargeLevel = 1;

    public ArcaneOrbEntity(EntityType<? extends ProjectileEntity> entityType, World world) {
        super(entityType, world);
        this.noClip = false;
    }

    public ArcaneOrbEntity(World world, LivingEntity owner, int chargeLevel) {
        super(GodWandMod.ARCANE_ORB_ENTITY_TYPE, world);
        this.setOwner(owner);
        this.setChargeLevel(chargeLevel);
        this.noClip = false;
    }

    public void setChargeLevel(int level) {
        this.chargeLevel = level;
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

        if (this.getWorld().isClient()) {
            this.getWorld().addParticle(new DustParticleEffect(new Vector3f(0.0f, 0.94f, 1.0f), 1.3f),
                    this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
            this.getWorld().addParticle(ParticleTypes.REVERSE_PORTAL,
                    this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
        } else {
            ServerWorld sw = (ServerWorld) this.getWorld();
            // Cyan & Amethyst dual particles
            sw.spawnParticles(new DustParticleEffect(new Vector3f(0.0f, 0.94f, 1.0f), 1.2f),
                    this.getX(), this.getY(), this.getZ(), 4, 0.1, 0.1, 0.1, 0.0);
            sw.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                    this.getX(), this.getY(), this.getZ(), 3, 0.1, 0.1, 0.1, 0.02);

            if (this.age > 200) {
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
            double radius = switch (this.chargeLevel) {
                case 1 -> 5.0;
                case 2 -> 10.0;
                default -> 16.0; // Calamity Level 3
            };
            BlockPos center = hitResult instanceof BlockHitResult bhr ? bhr.getBlockPos() : this.getBlockPos();
            ServerPlayerEntity caster = (this.getOwner() instanceof ServerPlayerEntity spe) ? spe : null;
            ShockwaveSlicer.queueCalamityDetonation((ServerWorld) this.getWorld(), center, radius, caster);
            this.discard();
        }
    }
}
