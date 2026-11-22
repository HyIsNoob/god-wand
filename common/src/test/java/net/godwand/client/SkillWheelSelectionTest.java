package net.godwand.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SkillWheelSelectionTest {
 @Test void eightCentersAndTheirBoundariesStaySelectable() {
  for(int mode=0;mode<9;mode++) {
   double a=-Math.PI/2+mode*Math.PI*2/9;
   assertEquals(mode,SkillWheelSelection.modeAt(Math.cos(a)*80,Math.sin(a)*80));
   assertEquals(mode,SkillWheelSelection.modeAt(Math.cos(a)*800,Math.sin(a)*800));
   for(double edge:new double[]{-.33,.33})assertEquals(mode,SkillWheelSelection.modeAt(Math.cos(a+edge)*80,Math.sin(a+edge)*80));
  }
 }
 @Test void centerKeepsCurrentSkillAndTopSeamStaysValid() {
  assertEquals(-1,SkillWheelSelection.modeAt(0,0));assertEquals(-1,SkillWheelSelection.modeAt(30,30));
  assertEquals(1,SkillWheelSelection.modeAt(80*Math.sin(Math.PI/4),-80*Math.cos(Math.PI/4)));
  assertEquals(0,SkillWheelSelection.modeAt(0,-48));assertEquals(0,SkillWheelSelection.modeAt(-.01,-80));
 }
 @Test void cycleWrapsAndSkipsDisabledSkills() {
  assertEquals(1,SkillWheelSelection.cycle(0,1,0));assertEquals(8,SkillWheelSelection.cycle(0,-1,0));
  assertEquals(0,SkillWheelSelection.cycle(8,1,0));assertEquals(2,SkillWheelSelection.cycle(0,2,0));
  assertEquals(3,SkillWheelSelection.cycle(0,1,0b110));assertEquals(0,SkillWheelSelection.cycle(3,-1,0b110));
  assertEquals(1,SkillWheelSelection.cycle(7,1,0b110000001));
  assertEquals(4,SkillWheelSelection.cycle(4,1,0b111101111));assertEquals(4,SkillWheelSelection.cycle(4,-3,0b111101111));
  assertEquals(5,SkillWheelSelection.cycle(4,1,0b111011111));assertEquals(5,SkillWheelSelection.cycle(4,-1,0b111011111));
 }
}
