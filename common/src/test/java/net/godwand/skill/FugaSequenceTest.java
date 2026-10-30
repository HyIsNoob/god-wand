package net.godwand.skill;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FugaSequenceTest {
 @Test void onlyHoldingAndAimingLocksMovement() {
  assertTrue(FugaSequence.movementLocked(0));
  for(int phase=1;phase<=4;phase++)assertFalse(FugaSequence.movementLocked(phase));
 }
 @Test void releaseRequiresSixSecondsAndImpactStopsBeforeRetraction() {
  assertFalse(FugaSequence.ready(1119,1000)); assertTrue(FugaSequence.ready(1120,1000));
  assertFalse(FugaSequence.terrainActive(39)); assertTrue(FugaSequence.terrainActive(40));
  assertTrue(FugaSequence.terrainActive(119)); assertFalse(FugaSequence.terrainActive(120));
  assertEquals(0,FugaSequence.columnRadius(39)); assertEquals(24,FugaSequence.columnRadius(40));
  assertEquals(24,FugaSequence.columnRadius(120)); assertEquals(0,FugaSequence.columnRadius(160));
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
