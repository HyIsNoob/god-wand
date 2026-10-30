package net.godwand.skill;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FugaSequenceTest {
 @Test void defaultTerrainRadiusMatchesColumn() { assertEquals(FugaSequence.RADIUS,new net.godwand.config.GodWandConfig.Server().fugaTerrainRadius); }
 @Test void onlyHoldingAndAimingLocksMovement() {
  assertTrue(FugaSequence.movementLocked(0));
  for(int phase=1;phase<=4;phase++)assertFalse(FugaSequence.movementLocked(phase));
 }
 @Test void releaseRequiresTenSecondsAndImpactStopsBeforeRetraction() {
  assertFalse(FugaSequence.ready(1199,1000)); assertTrue(FugaSequence.ready(1200,1000));
  assertFalse(FugaSequence.terrainActive(79)); assertTrue(FugaSequence.terrainActive(80));
  assertTrue(FugaSequence.terrainActive(179)); assertFalse(FugaSequence.terrainActive(180));
  assertEquals(0,FugaSequence.columnRadius(79)); assertEquals(48,FugaSequence.columnRadius(80));
  assertEquals(48,FugaSequence.columnRadius(180)); assertEquals(0,FugaSequence.columnRadius(230));
 }
 @Test void visualClockMatchesExplosionRetractionAndFinish() {
  assertEquals(40,FugaSequence.visualAge(80));assertEquals(120,FugaSequence.visualAge(180));assertEquals(160,FugaSequence.visualAge(230));
 }
 @Test void aimRemainsStraightAndFiniteAtTheWorldEdge() {
  Vec3 from=new Vec3(29_999_990,80,0),look=new Vec3(1,0,1).normalize();
  Vec3 target=FugaSequence.launchTarget(from,look);
  assertEquals(29_999_999,target.x,.000001);assertEquals(9,target.z,.000001);
  assertTrue(target.distanceTo(from)<=FugaSequence.MAX_RANGE);
 }
 @Test void straightFlightClampsToEndpointAndNeverExtrapolates() {
  Vec3 origin=new Vec3(5,8,9), target=origin.add(160,0,0);
  assertEquals(origin,FugaSequence.arrowPosition(origin,target,-2));
  assertEquals(origin.add(4,0,0),FugaSequence.arrowPosition(origin,target,1));
  assertEquals(target,FugaSequence.arrowPosition(origin,target,100));
 }
}
