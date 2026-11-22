package net.godwand.entity;

import net.godwand.GodWandMod;
import net.godwand.skill.BlockDominionManager;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** High-velocity launched block for Block Dominion (single, rapid-fire, volley, or auto-counter). */
public class DominionBlockProjectileEntity extends Projectile {
    private static final EntityDataAccessor<BlockState> BLOCK_STATE =
            SynchedEntityData.defineId(DominionBlockProjectileEntity.class, EntityDataSerializers.BLOCK_STATE);
    private static final EntityDataAccessor<Boolean> VOLLEY =
            SynchedEntityData.defineId(DominionBlockProjectileEntity.class, EntityDataSerializers.BOOLEAN);
    private final Set<UUID> hitEntities = new HashSet<>();

    public DominionBlockProjectileEntity(EntityType<? extends Projectile> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = false;
    }

    public DominionBlockProjectileEntity(Level level, LivingEntity owner, BlockState state, boolean volley) {
        super(GodWandMod.DOMINION_BLOCK_ENTITY_TYPE, level);
        this.setOwner(owner);
        this.setBlockState(state);
        this.setVolley(volley);
        this.noPhysics = false;
    }

    public boolean markHit(LivingEntity entity) {
        return hitEntities.add(entity.getUUID());
    }

    public void setBlockState(BlockState state) {
        this.entityData.set(BLOCK_STATE, state == null || state.isAir() ? Blocks.STONE.defaultBlockState() : state);
    }

    public BlockState getBlockState() {
        BlockState state = this.entityData.get(BLOCK_STATE);
        return state == null || state.isAir() ? Blocks.STONE.defaultBlockState() : state;
    }

    public void setVolley(boolean volley) {
        this.entityData.set(VOLLEY, volley);
    }

    public boolean isVolley() {
        return this.entityData.get(VOLLEY);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(BLOCK_STATE, Blocks.STONE.defaultBlockState());
        builder.define(VOLLEY, false);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putInt("BlockStateId", Block.getId(getBlockState()));
        nbt.putBoolean("Volley", isVolley());
        nbt.putInt("Age", this.tickCount);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("BlockStateId")) {
            BlockState decoded = Block.stateById(nbt.getInt("BlockStateId"));
            setBlockState(decoded);
        }
        setVolley(nbt.getBoolean("Volley"));
        this.tickCount = nbt.getInt("Age");
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 velocity = this.getDeltaMovement();
        Vec3 start = position();
        Vec3 end = start.add(velocity);

        if (!level().isClientSide()) {
            if (tickCount > 120 || velocity.lengthSqr() < 0.001) {
                discard();
                return;
            }
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                setPos(hit.getLocation());
                BlockDominionManager.onProjectileImpact(this, hit);
                discard();
                return;
            }
            if (level() instanceof ServerLevel sw && tickCount % 2 == 0) {
                sw.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, getBlockState()),
                        getX(), getY(), getZ(), 2, 0.12, 0.12, 0.12, 0.04);
            }
        }
        setPos(end);
    }
}
