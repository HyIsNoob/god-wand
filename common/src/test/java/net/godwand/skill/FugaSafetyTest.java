package net.godwand.skill;

import net.godwand.config.GodWandConfig;
import net.godwand.item.GodWandItem;
import net.godwand.network.FugaFxPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FugaSafetyTest {
    @TempDir Path directory;
    private GodWandConfig.Server previous;
    private ServerLevel world;
    private ServerPlayer caster;
    private ItemStack wand;
    private Object task;
    private Class<?> taskType;
    private final Vec3 impact = new Vec3(101, 70, -99);

    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @BeforeEach void setUp() throws Exception {
        previous = GodWandConfig.SERVER;
        Files.writeString(directory.resolve("godwand-server.json"),
                "{\"fugaTerrainDamage\":false,\"fugaTerrainRadius\":23}");
        GodWandConfig.loadServer(directory);
        assertFalse(GodWandConfig.SERVER.fugaTerrainDamage);
        assertEquals(23, GodWandConfig.SERVER.fugaTerrainRadius);
        world = mock(ServerLevel.class);
        caster = mock(ServerPlayer.class);
        wand = mock(ItemStack.class);
        when(caster.serverLevel()).thenReturn(world);
        when(caster.position()).thenReturn(impact);
        when(caster.getEyePosition()).thenReturn(impact);
        when(caster.getLookAngle()).thenReturn(new Vec3(1, 0, 0));
        when(caster.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(wand);
        when(caster.isAlive()).thenReturn(true);
        when(caster.getUUID()).thenReturn(UUID.randomUUID());
        when(world.getMinBuildHeight()).thenReturn(-64);
        taskType = Class.forName("net.godwand.skill.FugaManager$Task");
        var constructor = taskType.getDeclaredConstructor(ServerPlayer.class, InteractionHand.class);
        constructor.setAccessible(true);
        task = constructor.newInstance(caster, InteractionHand.MAIN_HAND);
        setField("phase", FugaFxPayload.IMPACT);
    }

    @AfterEach void restoreConfig() { GodWandConfig.SERVER = previous; }

    private void setField(String name, Object value) throws Exception {
        var field = taskType.getDeclaredField(name);
        field.setAccessible(true);
        field.set(task, value);
    }

    private void invoke(String method, Long age) throws Exception {
        var action = age == null ? FugaManager.class.getDeclaredMethod(method, taskType)
                : FugaManager.class.getDeclaredMethod(method, taskType, long.class);
        action.setAccessible(true);
        if (age == null) action.invoke(null, task);
        else action.invoke(null, task, age);
    }

    @SuppressWarnings("unchecked")
    private Mob pet(double x, double z) {
        var pet = mock(Mob.class);
        Vec3 position = impact.add(x, 0, z);
        when(pet.position()).thenReturn(position);
        when(pet.getX()).thenReturn(position.x);
        when(pet.getZ()).thenReturn(position.z);
        when(pet.isAlive()).thenReturn(true);
        when(pet.getUUID()).thenReturn(UUID.randomUUID());
        when(pet.level()).thenReturn(world);
        when(pet.getDeltaMovement()).thenReturn(Vec3.ZERO);
        var access = mock(RegistryAccess.class);
        Registry<DamageType> registry = mock(Registry.class);
        Holder.Reference<DamageType> type = mock(Holder.Reference.class);
        when(type.value()).thenReturn(mock(DamageType.class));
        when(world.registryAccess()).thenReturn(access);
        when(access.registryOrThrow(Registries.DAMAGE_TYPE)).thenReturn(registry);
        when(registry.getHolderOrThrow(SpellDamage.TYPE)).thenReturn(type);
        when(world.damageSources()).thenReturn(mock(DamageSources.class));
        when(world.damageSources().indirectMagic(caster, caster)).thenReturn(mock(net.minecraft.world.damagesource.DamageSource.class));
        return pet;
    }

    private void targets(List<LivingEntity> pets) {
        when(world.getEntitiesOfClass(eq(LivingEntity.class), any(AABB.class), any())).thenAnswer(call -> {
            AABB area = call.getArgument(1);
            Predicate<LivingEntity> filter = call.getArgument(2);
            return pets.stream().filter(pet -> area.contains(pet.position()) && filter.test(pet)).toList();
        });
    }

    @Test void blastOutflowAndSlownessRespectTheConfiguredCircle() throws Exception {
        var inside = pet(22.9, 0);
        var outside = pet(23.01, 0);
        var diagonal = pet(20, 20);
        var home = pet(110, 0);
        targets(List.of(inside, outside, diagonal, home));
        invoke("damage", 80L);
        verify(inside).hurt(any(), eq(9999f));
        invoke("outflow", 120L);
        verify(inside).setRemainingFireTicks(100);
        invoke("slow", null);
        verify(inside).addEffect(any());
        for (var safe : List.of(outside, diagonal, home)) {
            verify(safe, never()).hurt(any(), anyFloat());
            verify(safe, never()).setRemainingFireTicks(anyInt());
            verify(safe, never()).setDeltaMovement(any(Vec3.class));
            verify(safe, never()).addEffect(any());
        }
    }

    @Test void zeroRadiusDisablesAllHarmfulEffects() throws Exception {
        GodWandConfig.SERVER.fugaTerrainRadius = 0;
        var pet = pet(0, 0);
        targets(List.of(pet));
        invoke("damage", 80L);
        invoke("outflow", 120L);
        invoke("slow", null);
        verify(pet, never()).hurt(any(), anyFloat());
        verify(pet, never()).setRemainingFireTicks(anyInt());
        verify(pet, never()).addEffect(any());
    }

    @Test void serverCanTuneShockwaveDamageAndDisableBurningKnockbackAndSlowness() throws Exception {
        Files.writeString(directory.resolve("godwand-server.json"),
                "{\"fugaTerrainDamage\":false,\"fugaTerrainRadius\":23,\"fugaDamage\":200,"
                + "\"damageMultiplier\":0.5,\"fugaShockwaveDamageMultiplier\":0.25,"
                + "\"fugaIgniteEntities\":false,\"fugaShockwaveKnockback\":false,\"fugaSlowness\":false}");
        GodWandConfig.loadServer(directory);
        var pet = pet(22, 0);
        targets(List.of(pet));
        invoke("outflow", 120L);
        verify(pet).hurt(any(), eq(25f));
        verify(pet, never()).setRemainingFireTicks(anyInt());
        verify(pet, never()).setDeltaMovement(any(Vec3.class));
        invoke("slow", null);
        verify(pet, never()).addEffect(any());
    }

    @Test void zeroShockwaveDamageAndDisabledFireKeepTargetsUnharmed() throws Exception {
        Files.writeString(directory.resolve("godwand-server.json"),
                "{\"fugaTerrainDamage\":false,\"fugaTerrainRadius\":23,"
                + "\"fugaShockwaveDamageMultiplier\":0,\"fugaIgniteEntities\":false}");
        GodWandConfig.loadServer(directory);
        var pet = pet(22, 0);
        targets(List.of(pet));
        invoke("outflow", 120L);
        verify(pet, never()).hurt(any(), anyFloat());
        verify(pet, never()).setRemainingFireTicks(anyInt());
        verify(pet).setDeltaMovement(any(Vec3.class));
    }

    @Test void outflowDoesNotPlaceFireThatCanSpreadPastTheBoundary() throws Exception {
        GodWandConfig.SERVER.fugaTerrainDamage = true;
        var chunks = mock(ServerChunkCache.class);
        var chunk = mock(LevelChunk.class);
        when(world.getChunkSource()).thenReturn(chunks);
        when(chunks.getChunkNow(anyInt(), anyInt())).thenReturn(chunk);
        when(chunk.getHeight(any(), anyInt(), anyInt())).thenReturn(70);
        when(chunk.getBlockState(any())).thenReturn(Blocks.FERN.defaultBlockState());
        when(world.getBlockState(any())).thenReturn(Blocks.DIRT.defaultBlockState());
        when(world.getRandom()).thenReturn(mock(RandomSource.class));
        invoke("outflow", 120L);
        verify(world, never()).setBlock(any(), any(), anyInt());
    }

    @Test @SuppressWarnings("unchecked")
    void disabledTerrainPreventsEveryImpactPhaseFromChangingBlocks() throws Exception {
        var crater = mock(FugaCrater.class);
        setField("crater", crater);
        var field = FugaManager.class.getDeclaredField("TASKS");
        field.setAccessible(true);
        var tasks = (Map<UUID, Object>) field.get(null);
        tasks.put(caster.getUUID(), task);
        try (var modes = mockStatic(GodWandItem.class)) {
            modes.when(() -> GodWandItem.getSkillMode(wand)).thenReturn(GodWandItem.MODE_FUGA);
            for (long age : new long[]{8, 60, 75, 80, 120, 224, 304}) {
                when(world.getGameTime()).thenReturn(age);
                FugaManager.tick(world);
            }
            verifyNoInteractions(crater);
            verify(world, never()).setBlock(any(), any(), anyInt());
            verify(world, never()).setChunkForced(anyInt(), anyInt(), anyBoolean());
        } finally { tasks.remove(caster.getUUID()); }
    }

    @Test void craterAndSpiresStayInsideTheConfiguredRadius() throws Exception {
        var chunks = mock(ServerChunkCache.class);
        var chunk = mock(LevelChunk.class);
        when(world.getChunkSource()).thenReturn(chunks);
        when(world.getMaxBuildHeight()).thenReturn(320);
        when(chunks.getChunkNow(anyInt(), anyInt())).thenReturn(chunk);
        when(chunk.getHeight(any(), anyInt(), anyInt())).thenReturn(70);
        when(chunk.getBlockState(any())).thenAnswer(call -> ((BlockPos) call.getArgument(0)).getY() > 70
                ? Blocks.FERN.defaultBlockState() : Blocks.STONE.defaultBlockState());
        BlockPos center = BlockPos.containing(impact);
        for (int radius : new int[]{8, 23}) {
            doAnswer(call -> {
                BlockPos position = call.getArgument(0);
                int dx = position.getX() - center.getX(), dz = position.getZ() - center.getZ();
                assertTrue(dx * dx + dz * dz <= radius * radius, "write outside radius " + radius + " at " + position);
                return true;
            }).when(world).setBlock(any(), any(), anyInt());
            var crater = new FugaCrater(center, radius);
            var pending = FugaCrater.class.getDeclaredField("pending");
            pending.setAccessible(true);
            var job = (java.util.concurrent.CompletableFuture<?>) pending.get(crater);
            if (job != null) job.get(5, java.util.concurrent.TimeUnit.SECONDS);
            crater.upheave(world, 32768, 60);
            crater.press(world, 32768, 76);
            for (int slice = 0; slice < 100 && !crater.finished(); slice++)
                crater.carve(world, 32768, Long.MAX_VALUE, 80);
            assertTrue(crater.finished());
        }
        verify(world, atLeastOnce()).setBlock(any(), any(), anyInt());
    }
}
