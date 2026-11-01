package net.godwand.skill;

import net.godwand.config.GodWandConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainOptionsTest {
    @Test void liftingAndDestructionAreIndependentAndCapturedForEachCast() throws Exception {
        var previous=GodWandConfig.SERVER;
        try {
            GodWandConfig.SERVER=new GodWandConfig.Server();
            GodWandConfig.SERVER.earthDrillTerrainDamage=false;GodWandConfig.SERVER.earthDrillBlockLift=true;
            GodWandConfig.SERVER.supernovaTerrainDamage=false;GodWandConfig.SERVER.supernovaBlockLift=false;
            var drill=new RatioCollapseManager.RatioCollapseTask(null,null,BlockPos.ZERO,1,1,1,1,1,1);
            var nova=new SupernovaManager.SupernovaTask(null,null,BlockPos.ZERO,Vec3.ZERO);
            assertFalse(drill.terrainDamage);assertTrue(drill.blockLift);
            assertFalse(nova.terrainDamage);assertFalse(nova.blockLift);
            var crater=RatioCollapseManager.class.getDeclaredMethod("carveCrater",RatioCollapseManager.RatioCollapseTask.class);
            crater.setAccessible(true);crater.invoke(null,drill);
            var prepare=SupernovaManager.class.getDeclaredMethod("prepareTerrain",SupernovaManager.SupernovaTask.class);
            prepare.setAccessible(true);prepare.invoke(null,nova);
            var blast=SupernovaManager.class.getDeclaredMethod("carveSphericalCraterShell",SupernovaManager.SupernovaTask.class,int.class,int.class);
            blast.setAccessible(true);blast.invoke(null,nova,1,60);
            GodWandConfig.SERVER.earthDrillTerrainDamage=true;GodWandConfig.SERVER.supernovaBlockLift=true;
            assertFalse(drill.terrainDamage);assertFalse(nova.blockLift);
            GodWandConfig.SERVER.stoneCannonTerrainDamage=false;
            var cannon=StoneCannonManager.class.getDeclaredMethod("excavateImpact",net.godwand.entity.StoneCannonProjectileEntity.class,Vec3.class,Vec3.class);
            cannon.setAccessible(true);assertEquals(Vec3.ZERO,cannon.invoke(null,null,Vec3.ZERO,new Vec3(10,0,0)));
        } finally {GodWandConfig.SERVER=previous;}
    }
}
