package net.godwand.skill;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AtomicSequenceTest {
 @Test void holdingFreezesOnlyTheSceneClockAndReleaseResumesItWithoutSkippingTheBlast() {
  long started=1000;
  assertEquals(200,AtomicSequence.castAge(1200,started,-1));
  for(long now:new long[]{1414,1600,50000}) {
   assertEquals(AtomicSequence.WAIT_TICK,AtomicSequence.castAge(now,started,-1));
   assertEquals(0,AtomicSequence.beamRadius(AtomicSequence.castAge(now,started,-1)));
   assertTrue(AtomicSequence.protectedDuringCast(AtomicSequence.castAge(now,started,-1)));
  }
  long release=AtomicSequence.releaseTime(1600,started);
  assertEquals(1600,release);
  assertEquals(AtomicSequence.WAIT_TICK,AtomicSequence.castAge(release,started,release));
  assertEquals(AtomicSequence.DETONATION,AtomicSequence.castAge(release+8,started,release));
  assertEquals(AtomicSequence.END,AtomicSequence.castAge(release+AtomicSequence.END-AtomicSequence.WAIT_TICK,started,release));
  long buffered=AtomicSequence.releaseTime(1390,started);
  assertTrue(buffered>=started+AtomicSequence.WAIT_TICK+2);
  assertEquals(AtomicSequence.WAIT_TICK,AtomicSequence.castAge(buffered-1,started,buffered));
 }
 @Test void darkWaitHasIdleThenFlashImpactAndExplosionInThatOrder() {
  int wait=AtomicSequence.WAIT_TICK;
  assertEquals(1,AtomicSequence.compression(wait));
  assertTrue(AtomicSequence.blackout(wait)>.4);
  assertEquals(0,AtomicSequence.releaseFlash(wait));
  assertTrue(AtomicSequence.releaseFlash(wait+2)>.9);
  assertTrue(AtomicSequence.impact(wait+4));
  assertEquals(0,AtomicSequence.releaseFlash(AtomicSequence.DETONATION));
  assertTrue(Math.abs(AtomicSequence.holdIdle(45)-AtomicSequence.holdIdle(50))>.001);
  assertTrue(Math.abs(AtomicSequence.holdIdle(45))<.04);
 }
 @Test void traceCameraFallsBehindTheBranchesAndRisesIntoTheOverheadCut() {
  var early=AtomicSequence.shot(50,0);
  var late=AtomicSequence.shot(110,0);
  assertTrue(late.offset().y>early.offset().y+5 && late.offset().y<18);
  assertTrue(AtomicSequence.groundTracePoint(110,0).x-late.offset().x>5,"Camera must reveal the branches behind the head");
  assertTrue(late.focus().x>late.offset().x,"Trace camera must keep looking outward");
  for(int age:new int[]{120,150,190,200}) {
   var shot=AtomicSequence.shot(age,0);
   assertEquals(0,shot.offset().x-shot.focus().x,1e-5);
   assertEquals(0,shot.offset().z-shot.focus().z,1e-5);
  }
  assertTrue(AtomicSequence.shot(190,0).offset().y<AtomicSequence.shot(150,0).offset().y);
  assertTrue(AtomicSequence.circuitProgress(170,1)<1);
  assertEquals(1,AtomicSequence.circuitProgress(200,1));
 }
 @Test void buildupKeepsMovingAndAtomicDropsTheArmsBeforeTheQuietBeat() {
  assertEquals(0,AtomicSequence.armRaise(290));
  assertEquals(1,AtomicSequence.armRaise(350));
  assertEquals(0,AtomicSequence.armSpread(395));
  assertTrue(AtomicSequence.armSpread(406)>.5);
  assertEquals(1,AtomicSequence.armSpread(416));
  assertTrue(AtomicSequence.armPitch(416)>-.4,"Hands must be down before detonation");
  for(int age=1;age<AtomicSequence.DETONATION-3;age++) {
   var a=AtomicSequence.shot(age,0); var b=AtomicSequence.shot(age+.25,0);
   assertTrue(a.offset().distanceToSqr(b.offset())>1e-9,"Stationary buildup at "+age);
  }
  assertTrue(AtomicSequence.shot(245,0).offset().length()<55);
  assertTrue(AtomicSequence.shot(250,0).offset().y<AtomicSequence.shot(220,0).offset().y);
 }
 @Test void columnGrowsThenOnlyItsWidthContracts() {
  assertEquals(0,AtomicSequence.beamRadius(AtomicSequence.DETONATION-1));
  float previous=0;
  for(int age=414;age<=534;age++) {
   float radius=AtomicSequence.beamRadius(age);
   assertTrue(radius>=previous);
   assertEquals(radius,AtomicSequence.shockRadius(age),.001);
   previous=radius;
  }
  assertEquals(98,previous,.001);
  for(int age=574;age<=734;age++) {
   assertEquals(1024,AtomicSequence.beamHeight(age),.001,"Height changed during contraction");
   float radius=AtomicSequence.beamRadius(age);
   assertTrue(radius<=previous);
   previous=radius;
  }
  assertEquals(0,previous);
  assertTrue(AtomicSequence.scarOpacity(794)>.5);
  assertEquals(0,AtomicSequence.visibility(AtomicSequence.END));
 }
 @Test void camerasStayBoundedAndReturn() {
  assertTrue(AtomicSequence.shot(370,0).offset().length()<10);
  assertTrue(AtomicSequence.shot(494,0).offset().horizontalDistance()>150,"The blast needs one readable distant shot");
  assertTrue(AtomicSequence.shot(694,0).offset().horizontalDistance()<60,"Contraction gets the close shot");
  assertTrue(AtomicSequence.shot(434,0).offset().y<3);
  assertTrue(AtomicSequence.shot(434,0).focus().y>100);
  assertTrue(AtomicSequence.shot(205,0).offset().length()<75);
  for(int age=0;age<=AtomicSequence.END;age++) {
   var shot=AtomicSequence.shot(age,237);
   assertTrue(Double.isFinite(shot.offset().length()));
   assertTrue(shot.fov()>=30 && shot.fov()<=128);
  }
  assertTrue(AtomicSequence.shot(802,0).offset().z<0,"Return must approach from behind the shoulder");
  assertTrue(AtomicSequence.shot(802,0).focus().z>0);
  assertTrue(AtomicSequence.shot(754,0).focus().y<1,"Aftermath must frame the scarred ground");
  assertEquals(1,AtomicSequence.returnBlend(AtomicSequence.END));
  assertTrue(AtomicSequence.cameraBudget(4)<=63);
  assertTrue(AtomicSequence.cameraBudget(8)>106,"The default-distance camera must remain outside the full column and corona");
  assertEquals(AtomicSequence.cameraBudget(8),AtomicSequence.cameraBudget(12));
  assertEquals(AtomicSequence.cameraBudget(8),AtomicSequence.cameraBudget(32),"High render distance must not move the wide shot farther away");
  assertEquals(0,AtomicSequence.shake(AtomicSequence.DETONATION-2));
  assertTrue(AtomicSequence.shake(415)>.8);
 }
 @Test void onlyActivationAndAtomicHaveImpactFramesAndMovementUnlocks() {
  assertTrue(AtomicSequence.activation(12)>.9);
  assertTrue(AtomicSequence.shake(12)>0 && AtomicSequence.shake(12)<.4);
  assertEquals(0,AtomicSequence.shake(30));
  assertEquals(0,AtomicSequence.shake(90));
  assertEquals(0,AtomicSequence.activation(40));
  assertTrue(AtomicSequence.impact(12));
  assertTrue(AtomicSequence.impact(398));
  for(int age:new int[]{80,150,318,370,416,426,534,714,814})assertFalse(AtomicSequence.impact(age));
  assertTrue(AtomicSequence.movementLocked(0));
  assertTrue(AtomicSequence.movementLocked(784));
  assertFalse(AtomicSequence.movementLocked(826));
 }
 @Test void atomicReleaseAddsOneMildDecayingImpulseBeforeTheQuietBeat() {
  float previous=AtomicSequence.shake(396);
  assertTrue(previous>0 && previous<.3);
  for(int age=397;age<408;age++) {
   float next=AtomicSequence.shake(age);
   assertTrue(next>0 && next<previous); previous=next;
  }
  for(int age=408;age<AtomicSequence.DETONATION-2;age++)assertEquals(0,AtomicSequence.shake(age));
 }
 @Test void ribbonsConvergeOnTheMovingBladeAndMotesRiseFromTheGround() {
  for(double seed:new double[]{0,.25,.5,.875}) {
   var target=new net.minecraft.world.phys.Vec3(.6+seed,2+seed,1.2);
   var start=AtomicSequence.convergencePoint(320,seed,0,target);
   var end=AtomicSequence.convergencePoint(320,seed,1,target);
   if(seed<.5) { assertEquals(.12,start.y,.001); assertTrue(start.horizontalDistance()>=1.8&&start.horizontalDistance()<=2.3); }
   else assertTrue(start.y>=14,"Upper streams must flow down from above the blade");
   assertEquals(target,end);
   var ground=AtomicSequence.risingMote(0,seed); var high=AtomicSequence.risingMote(.8,seed);
   assertEquals(.12,ground.y,.001); assertTrue(high.y>10);
   assertEquals(ground.x,high.x,.001,"Motes must rise vertically without orbiting");
   assertEquals(ground.z,high.z,.001);
  }
 }
 @Test void bladeGatherHasAGentleShakeBeforeTheWindup() {
  assertTrue(AtomicSequence.shake(350)>0&&AtomicSequence.shake(350)<.15);
  assertEquals(0,AtomicSequence.shake(388));
 }
 @Test void releaseIsOneExpandingCircularWaveAndThenExpires() {
  assertEquals(0,AtomicSequence.releaseRadius(395));
  float previous=0;
  for(int age=396;age<=404;age++) {
   float radius=AtomicSequence.releaseRadius(age); assertTrue(radius>previous); previous=radius;
  }
  assertEquals(35,previous,.001);
  assertEquals(0,AtomicSequence.releaseRadius(428));
 }
 @Test void compressionKeepsShrinkingUntilTheWaitBegins() {
  assertEquals(0,AtomicSequence.compression(395),"Compression begins on the release cue");
  assertTrue(AtomicSequence.compression(402)>.7,"Compression must consume most of the fortress immediately");
  float previous=0;
  for(int age=397;age<=AtomicSequence.WAIT_TICK;age++) {
   float next=AtomicSequence.compression(age); assertTrue(next>previous); previous=next;
  }
  assertEquals(1,previous);
 }
 @Test void expandingAndContractingColumnNeverFlickers() {
  for(double age=414;age<=834;age+=.25)assertEquals(1,AtomicSequence.blastPulse(age));
 }
 @Test void atomicCameraKeepsTheHandsAndFeetInsideTheLetterbox() {
  var iam=AtomicSequence.shot(350,0); var atomic=AtomicSequence.shot(410,0);
  assertTrue(iam.offset().y>iam.focus().y,"I AM should look slightly down");
  assertTrue(atomic.offset().horizontalDistance()>iam.offset().horizontalDistance()+1);
  assertTrue(Math.abs(atomic.offset().y-atomic.focus().y)<.65,"ATOMIC must be near eye level");
  assertTrue(atomic.focus().y<1.8,"Do not aim over the head as hands come down");
  assertTrue(AtomicSequence.shot(524,0).offset().distanceTo(AtomicSequence.shot(525,0).offset())<1,"Distant blast must drift slowly");
 }
 @Test void theHeroDollyHasNoVelocityJumpsAtTheWordOrPoseChanges() {
  for(double boundary:new double[]{370,380,396,402,410}) {
   var before=AtomicSequence.shot(boundary-.01,0); var now=AtomicSequence.shot(boundary,0); var after=AtomicSequence.shot(boundary+.01,0);
   var in=now.offset().subtract(before.offset()).scale(100);
   var out=after.offset().subtract(now.offset()).scale(100);
   assertTrue(in.distanceTo(out)<.03,"Camera velocity snapped at "+boundary);
   assertTrue(Math.abs((after.fov()-now.fov())-(now.fov()-before.fov()))<.01,"Lens velocity snapped");
  }
  assertTrue(AtomicSequence.blackout(AtomicSequence.DETONATION-2)<.7,"The quiet beat must keep the actor visible");
 }
 @Test void atomicPushesTheCameraBackOnTheArmSnapAndFacesItSquarely() {
  var before=AtomicSequence.shot(395,0); var snap=AtomicSequence.shot(402,0);
  assertTrue(snap.offset().horizontalDistance()-before.offset().horizontalDistance()>1.8,"The release needs a visible camera push");
  for(int age:new int[]{396,400,406,413}) {
   var shot=AtomicSequence.shot(age,0);
   assertEquals(0,shot.offset().x,.001,"ATOMIC must face the player straight on");
   assertTrue(Math.abs(shot.offset().y-shot.focus().y)<.65);
  }
 }
 @Test void theColumnHidesFarSideDustButKeepsFrontDust() {
  var eye=new net.minecraft.world.phys.Vec3(0,8,160);
  assertTrue(AtomicSequence.columnOccludes(eye,new net.minecraft.world.phys.Vec3(0,5,-105),544));
  assertFalse(AtomicSequence.columnOccludes(eye,new net.minecraft.world.phys.Vec3(0,5,105),550));
  assertFalse(AtomicSequence.columnOccludes(new net.minecraft.world.phys.Vec3(0,1100,160),new net.minecraft.world.phys.Vec3(0,1100,-105),544));
  assertFalse(AtomicSequence.columnOccludes(eye,new net.minecraft.world.phys.Vec3(0,5,-105),AtomicSequence.DETONATION-1));
  assertTrue(AtomicSequence.protectedDuringCast(0));
  assertTrue(AtomicSequence.protectedDuringCast(833));
  assertFalse(AtomicSequence.protectedDuringCast(834));
 }
 @Test void buildupCutsFadeGentlyWithoutAddingBlastTransitions() {
  for(int cut:new int[]{115,205,285}) {
   assertTrue(AtomicSequence.transitionOpacity(cut)>.4);
   assertEquals(0,AtomicSequence.transitionOpacity(cut-6));
   assertEquals(0,AtomicSequence.transitionOpacity(cut+6));
   assertTrue(AtomicSequence.transitionOpacity(cut-2)>AtomicSequence.transitionOpacity(cut-4));
  }
  for(int age=396;age<=834;age++)assertEquals(0,AtomicSequence.transitionOpacity(age));
 }
 @Test void windupPrecedesTheFastArmSnapAndTheActorFacesForward() {
  assertEquals(0,AtomicSequence.armWindup(380));
  assertTrue(AtomicSequence.armWindup(394)>.99);
  assertTrue(AtomicSequence.armPitch(394)>-1.2);
  assertEquals(0,AtomicSequence.armSpread(396));
  assertEquals(1,AtomicSequence.armSpread(402));
  assertEquals(0,AtomicSequence.armWindup(402));
  assertEquals(1,AtomicSequence.facing(396));
  assertEquals(0,AtomicSequence.facing(802));
 }
 @Test void randomMotesFreezeBeforeAtomicThenFlyRadiallyOutward() {
  assertEquals(390,AtomicSequence.moteClock(394));
  assertEquals(390,AtomicSequence.moteClock(414));
  for(double seed:new double[]{.13,.37,.68,.91}) {
   var point=AtomicSequence.risingMote(.45,seed);
   assertEquals(point,AtomicSequence.releaseMote(point,395));
   var pushed=AtomicSequence.releaseMote(point,414);
   assertEquals(point.horizontalDistance()+12,pushed.horizontalDistance(),.001);
   assertEquals(0,point.x*pushed.z-point.z*pushed.x,.001,"Do not turn the random field into a spiral");
   assertTrue(pushed.y>point.y);
  }
  assertEquals(net.minecraft.world.phys.Vec3.ZERO,AtomicSequence.releaseMote(net.minecraft.world.phys.Vec3.ZERO,395));
 }
 @Test void powerWaveRisesFromTheWallsToTheSpireTipsAndRepeatsEveryThreeSeconds() {
  assertEquals(-1,AtomicSequence.fortressWaveHeight(169));
  assertEquals(-1,AtomicSequence.fortressWaveHeight(174),"The initial circular wave must still travel across the ground");
  double previous=-1;
  for(int age=178;age<220;age++) {
   double y=AtomicSequence.fortressWaveHeight(age);
   assertTrue(y>=previous); previous=y;
   assertEquals(y,AtomicSequence.fortressWaveHeight(age+60),.001);
  }
  assertTrue(previous>50,"The band must reach every spire tip");
  assertEquals(-1,AtomicSequence.fortressWaveHeight(222));
  assertEquals(-1,AtomicSequence.fortressWaveHeight(382));
 }
 @Test void letterboxStartsOnAtomicAndAftermathHoldsThirtySecondsBeforeFading() {
  for(int age:new int[]{0,65,150,320,395})assertEquals(0,AtomicSequence.letterbox(age));
  assertEquals(1,AtomicSequence.letterbox(396));
  assertEquals(1,AtomicSequence.letterbox(AtomicSequence.DETONATION-1));
  for(int age=414;age<=834;age++)assertEquals(0,AtomicSequence.letterbox(age),"The blast must open up to the full screen");
  assertEquals(0,AtomicSequence.letterbox(834));
  assertTrue(AtomicSequence.scarOpacity(784)>.5);
  assertTrue(AtomicSequence.scarOpacity(834)>.3);
  assertTrue(AtomicSequence.scarOpacity(894)>.3);
  assertTrue(AtomicSequence.scarOpacity(1434)>.3);
  assertEquals(0,AtomicSequence.scarOpacity(1494));
 }
 @Test void idleHeadGlancesAreSmoothRandomAndNeverBowAtTheGround() {
  java.util.Set<Integer> glances=new java.util.HashSet<>();
  for(double seed:new double[]{.13,4.7,19.6})for(int age=14;age<374;age++) {
   var look=AtomicSequence.headLook(age,seed); var next=AtomicSequence.headLook(age+.25,seed);
   assertTrue(look.x<-.01&&look.x>-.22,"The buildup gaze must stay slightly raised");
   assertTrue(Math.abs(look.y)<.36&&Math.abs(look.z)<.025);
   assertTrue(look.distanceTo(next)<.004,"The head must not snap between random targets");
   glances.add((int)(look.y*1000));
  }
  assertTrue(glances.size()>80,"The head must actually move during the buildup");
  assertNotEquals(AtomicSequence.headLook(180,.13),AtomicSequence.headLook(180,19.6));
 }
 @Test void wideFramingKeepsTheWholeFootprintInsideTheScreenWithRoomForRoll() {
  for(double radius:new double[]{112,160})for(double height:new double[]{8,60,120}) {
   var eye=new net.minecraft.world.phys.Vec3(0,height,radius);
   var focus=AtomicSequence.wideFocus(eye,128);
   var direction=focus.subtract(eye);
   float pitch=(float)-Math.atan2(direction.y,direction.horizontalDistance());
   var view=new org.joml.Matrix4f().rotateZ((float)Math.toRadians(5)).rotateX(pitch).translate(0,(float)-height,(float)-radius);
   var projection=new org.joml.Matrix4f().perspective((float)Math.toRadians(128),16f/9,.05f,1600);
   for(int i=0;i<64;i++) {
    double a=i*Math.PI/32;
    var p=new org.joml.Vector4f((float)(98*Math.cos(a)),-14,(float)(98*Math.sin(a)),1).mul(view).mul(projection);
    assertTrue(p.w>0);
    assertTrue(p.y/p.w>-.78&&p.y/p.w<.78,"The column footprint leaves the screen at distance "+radius+", height "+height);
    assertTrue(Math.abs(p.x/p.w)<1);
   }
  }
 }
 // V25 timeline expectations are already on the extended clock.
 @Test void traceCutIsDirectAndTheFortressOrbitGetsOneExtraSecond() {
  assertEquals(0,AtomicSequence.transitionOpacity(40));
  assertEquals(380,AtomicSequence.CHARGE_TICKS);
  assertEquals(396,AtomicSequence.ATOMIC_WORD);
  assertEquals(414,AtomicSequence.DETONATION);
  assertEquals(834,AtomicSequence.END);
  assertTrue(AtomicSequence.shot(284,0).offset().y>7);
  assertEquals(3.5,AtomicSequence.shot(285,0).offset().y,.001);
  var a=AtomicSequence.shot(235,0).offset(); var b=AtomicSequence.shot(236,0).offset();
  double turn=Math.abs(Math.atan2(a.x,a.z)-Math.atan2(b.x,b.z));
  assertTrue(turn<Math.toRadians(1.8),"The four-second castle sweep must actually move more slowly");
 }
 @Test void passingPressureRingsProduceSeparateMildCameraImpulses() {
  for(int i=0;i<4;i++) {
   double crossing=545.125+i*7.5;
   float peak=AtomicSequence.pressureShake(crossing,112);
   assertTrue(peak>.16&&peak<.3,"Missing pressure impulse when the rendered wave crosses the camera");
   assertTrue(AtomicSequence.pressureShake(crossing+3.75,112)<peak*.35,"Leave a quiet gap between fronts");
   assertEquals(0,AtomicSequence.pressureShake(crossing,160),.001,"Do not shake a camera the waves cannot reach");
   assertFalse(AtomicSequence.impact(crossing),"The pressure shakes must not add impact filters");
  }
  assertEquals(0,AtomicSequence.pressureShake(400,112));
 }
 // V26 expectations use the shorter release-to-blast interval.
 @Test void atomicBeginsCompressionOnTheArmAndWordCueAndBurstsWithoutTheLongWait() {
  assertEquals(0,AtomicSequence.compression(AtomicSequence.ATOMIC_WORD));
  assertTrue(AtomicSequence.compression(AtomicSequence.ATOMIC_WORD+.5)>.05);
  assertTrue(AtomicSequence.DETONATION-AtomicSequence.ATOMIC_WORD<=18);
  assertTrue(AtomicSequence.shake(AtomicSequence.DETONATION-1)>.1,"The initial blast jolt must lead the light front");
 }
 @Test void roofFinishesEarlyEnoughToWatchTheJunctionWaveBeforeTheCut() {
  assertEquals(0,AtomicSequence.circuitProgress(115,0));
  assertEquals(1,AtomicSequence.circuitProgress(180,1));
  assertEquals(0,AtomicSequence.shot(200,0).offset().horizontalDistance(),.001);
  assertTrue(AtomicSequence.junctionWaveRadius(195)>20);
 }
 @Test void pressureDamageOnlyFollowsOutgoingGroundFrontsWithoutHittingOnWraparound() {
  int hits=0;
  for(int age=414;age<650;age++)for(int wave=0;wave<4;wave++) {
   if(AtomicSequence.pressureCrosses(age,wave,112)) {
    hits++;
    assertTrue(AtomicSequence.pressure(age)>.15);
    assertTrue(AtomicSequence.pressureWaveRadius(age,wave)>=AtomicSequence.pressureWaveRadius(age-1,wave));
    assertTrue(Math.abs(AtomicSequence.pressureWaveRadius(age,wave)-112)<4);
   }
   assertFalse(AtomicSequence.pressureCrosses(age,wave,140));
   assertFalse(AtomicSequence.pressureCrosses(age,wave,50));
  }
  assertTrue(hits>10);
  assertFalse(AtomicSequence.pressureCrosses(400,0,112));
 }
 @Test void roofClosurePushesOneCircularWaveOutFromTheJunction() {
  assertEquals(0,AtomicSequence.junctionWaveRadius(179));
  assertTrue(AtomicSequence.junctionWaveRadius(185)>6);
  assertTrue(AtomicSequence.junctionWaveRadius(200)>25);
  assertEquals(0,AtomicSequence.junctionWaveRadius(203));
 }
 @Test void heartbeatIsSoftPeriodicAndSwordRaisesForwardInsteadOfBehindTheHead() {
  assertEquals(0,AtomicSequence.heartbeat(-1));
  assertEquals(1,AtomicSequence.heartbeat(0),.001);
  assertEquals(.6,AtomicSequence.heartbeat(5),.001);
  assertTrue(AtomicSequence.heartbeat(16)<.001);
  for(double t=0;t<28;t+=.25) {
   assertEquals(AtomicSequence.heartbeat(t),AtomicSequence.heartbeat(t+28),.0001);
   assertTrue(AtomicSequence.heartbeat(t)>=0&&AtomicSequence.heartbeat(t)<=1);
  }
  float pitch=AtomicSequence.armPitch(360);
  assertEquals(-2.2,pitch,.001);
  assertTrue(Math.sin(pitch)<-.75,"Raised hands must stay well in front of the head");
  assertTrue(Math.cos(pitch)<-.5,"Raised hands must be above the shoulders");
  assertEquals(0,AtomicSequence.armSpread(360));
  assertEquals(1,AtomicSequence.armSpread(402));
 }
}
