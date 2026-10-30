package net.godwand.skill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpellDamageTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void zeroAndInvalidDamageNeverTriggersBossMechanics() {
        var mob=mock(Mob.class);var source=mock(DamageSource.class);
        assertFalse(SpellDamage.hurt(mob,source,0));
        assertFalse(SpellDamage.hurt(mob,source,-1));
        assertFalse(SpellDamage.hurt(mob,source,Float.NaN));
        assertFalse(SpellDamage.hurt(mob,source,Float.POSITIVE_INFINITY));
        verifyNoInteractions(mob,source);
    }
    @Test void playerDamageStillUsesTheOriginalProtectionAndDamageRules() {
        var player=mock(Player.class);var source=mock(DamageSource.class);
        var world=mock(ServerLevel.class);when(player.level()).thenReturn(world);
        when(player.hurtServer(world,source,25)).thenReturn(false);
        assertFalse(SpellDamage.hurt(player,source,25));
        verify(player).hurtServer(world,source,25);
    }

    @SuppressWarnings("unchecked")
    private static net.minecraft.core.Holder.Reference<net.minecraft.world.damagesource.DamageType> installType(ServerLevel world) {
        var access=mock(net.minecraft.core.RegistryAccess.class);
        net.minecraft.core.Registry<net.minecraft.world.damagesource.DamageType> registry=mock(net.minecraft.core.Registry.class);
        net.minecraft.core.Holder.Reference<net.minecraft.world.damagesource.DamageType> type=mock(net.minecraft.core.Holder.Reference.class);
        when(world.registryAccess()).thenReturn(access);
        when(access.lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)).thenReturn(registry);
        when(registry.getOrThrow(SpellDamage.TYPE)).thenReturn(type);
        when(type.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)).thenReturn(true);
        return type;
    }
    @Test void bossGetsTheConfiguredAmountWithBypassTagAndPlayerAttribution() {
        var world=mock(ServerLevel.class);var boss=mock(Mob.class);
        var caster=mock(Player.class);var original=mock(DamageSource.class);var type=installType(world);
        when(boss.level()).thenReturn(world);when(original.getDirectEntity()).thenReturn(caster);when(original.getEntity()).thenReturn(caster);
        when(boss.hurtServer(eq(world),any(),anyFloat())).thenAnswer(call -> {
            var source=call.getArgument(1,DamageSource.class);
            assertSame(type,source.typeHolder());assertSame(caster,source.getEntity());assertSame(caster,source.getDirectEntity());
            assertEquals(9999f,call.getArgument(2,Float.class));
            return source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY);
        });
        assertTrue(SpellDamage.hurt(boss,original,9999));
        assertFalse(SpellDamage.hurt(boss,original,0));
        verify(boss,times(1)).hurtServer(eq(world),any(),anyFloat());
    }
    @Test void enderDragonIsDamagedThroughItsPartInsteadOfItsRejectingParentHook() {
        var world=mock(ServerLevel.class);installType(world);
        var dragon=mock(net.minecraft.world.entity.boss.enderdragon.EnderDragon.class);when(dragon.level()).thenReturn(world);
        when(dragon.hurt(eq(world),isNull(net.minecraft.world.entity.boss.enderdragon.EnderDragonPart.class),any(),eq(70f))).thenReturn(true);
        assertTrue(SpellDamage.hurt(dragon,mock(DamageSource.class),70));
        verify(dragon,never()).hurtServer(any(),any(DamageSource.class),anyFloat());
    }
    public abstract static class AmountWorm extends net.minecraft.world.entity.Entity {
        protected AmountWorm(){super(null,null);}
        public abstract void blastHit(int amount);
    }
    public abstract static class LegacyWorm extends net.minecraft.world.entity.Entity {
        protected LegacyWorm(){super(null,null);}
        public abstract void blastHit();
    }
    @Test void nonLivingSandwormUsesEitherPublicBlastApiAndDeduplicatesBodySegments() {
        var world=mock(ServerLevel.class);var source=mock(DamageSource.class);
        var modern=mock(AmountWorm.class);when(modern.getUUID()).thenReturn(java.util.UUID.randomUUID());
        var first=mock(net.jelly.sandworm_mod.entity.IK.worm.WormSegment.class);
        var second=mock(net.jelly.sandworm_mod.entity.IK.worm.WormSegment.class);
        when(first.getOwner()).thenReturn(modern);when(second.getOwner()).thenReturn(modern);
        when(world.getEntities(isNull(net.minecraft.world.entity.Entity.class),any(net.minecraft.world.phys.AABB.class),any())).thenReturn(java.util.List.of(first,second));
        SpellDamage.hurtExtras(world,new net.minecraft.world.phys.AABB(0,0,0,10,10,10),source,e -> 9999);
        verify(modern,times(1)).blastHit(9999);
        var legacy=mock(LegacyWorm.class);when(first.getOwner()).thenReturn(legacy);
        assertTrue(SpellDamage.hurt(first,source,50));verify(legacy).blastHit();
        clearInvocations(legacy);assertFalse(SpellDamage.hurt(first,source,0));verifyNoInteractions(legacy);
    }
    @Test void releasePortsKeepTheBossDamageTypeAndTagsButDoNotRemoveVanillaHitCooldown() throws Exception {
        var root=java.nio.file.Path.of("../../..").toAbsolutePath().normalize();
        var common=root.resolve("common/src/main/resources/data");
        for(String port:new String[]{"fabric-1.20.1","forge-1.20.1"}) {
            var resources=root.resolve("build/shared/"+port+"/src/main/resources/data");
            for(String path:new String[]{"godwand/damage_type/spell.json","minecraft/tags/damage_type/bypasses_invulnerability.json","cataclysm/tags/damage_type/block_self_regen.json"})
                assertEquals(java.nio.file.Files.readString(common.resolve(path)),java.nio.file.Files.readString(resources.resolve(path)),port+path);
            assertFalse(java.nio.file.Files.exists(resources.resolve("minecraft/tags/damage_type/bypasses_cooldown.json")));
        }
        var json=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(common.resolve("godwand/damage_type/spell.json")));
        assertNotNull(net.minecraft.world.damagesource.DamageType.DIRECT_CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,json).getOrThrow());
    }

    @Test void atomicRetriesARejectedHitAndCountsOnlyAnAcceptedHit() throws Exception {
        var world=mock(ServerLevel.class);installType(world);
        var caster=mock(net.minecraft.server.level.ServerPlayer.class);
        var wand=mock(net.minecraft.world.item.ItemStack.class);
        var boss=mock(Mob.class);var original=mock(DamageSource.class);
        when(caster.getUUID()).thenReturn(java.util.UUID.randomUUID());when(boss.getUUID()).thenReturn(java.util.UUID.randomUUID());
        when(caster.level()).thenReturn(world);when(caster.isAlive()).thenReturn(true);when(caster.isUsingItem()).thenReturn(true);
        when(caster.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND)).thenReturn(wand);
        when(caster.position()).thenReturn(net.minecraft.world.phys.Vec3.ZERO);
        when(boss.level()).thenReturn(world);when(boss.position()).thenReturn(net.minecraft.world.phys.Vec3.ZERO);when(boss.isAlive()).thenReturn(true);
        var sources=mock(net.minecraft.world.damagesource.DamageSources.class);when(world.damageSources()).thenReturn(sources);
        when(sources.indirectMagic(caster,caster)).thenReturn(original);
        when(world.getEntitiesOfClass(eq(net.minecraft.world.entity.LivingEntity.class),any(net.minecraft.world.phys.AABB.class),any())).thenReturn(java.util.List.of(boss));
        when(boss.hurtServer(eq(world),any(),anyFloat())).thenReturn(false,true);
        var type=Class.forName("net.godwand.skill.AtomicManager$Task");
        var ctor=type.getDeclaredConstructor(net.minecraft.server.level.ServerPlayer.class,net.minecraft.world.InteractionHand.class);ctor.setAccessible(true);
        Object task=ctor.newInstance(caster,net.minecraft.world.InteractionHand.MAIN_HAND);
        for(String name:new String[]{"armed","exploded","movementReleased"}) {var field=type.getDeclaredField(name);field.setAccessible(true);field.setBoolean(task,true);}
        var release=type.getDeclaredField("releasedAt");release.setAccessible(true);release.setLong(task,0);
        var crater=type.getDeclaredField("crater");crater.setAccessible(true);crater.set(task,mock(AtomicCrater.class));
        var field=AtomicManager.class.getDeclaredField("TASKS");field.setAccessible(true);
        @SuppressWarnings("unchecked") var tasks=(java.util.Map<java.util.UUID,Object>)field.get(null);tasks.put(caster.getUUID(),task);
        try(var modes=mockStatic(net.godwand.item.GodWandItem.class)) {
            modes.when(() -> net.godwand.item.GodWandItem.getSkillMode(wand)).thenReturn(net.godwand.item.GodWandItem.MODE_ATOMIC);
            for(long tick:new long[]{14,15,16}) {when(world.getGameTime()).thenReturn(tick);AtomicManager.tick(world);}
            verify(boss,times(2)).hurtServer(eq(world),any(),anyFloat());
        } finally {tasks.clear();}
    }
}
