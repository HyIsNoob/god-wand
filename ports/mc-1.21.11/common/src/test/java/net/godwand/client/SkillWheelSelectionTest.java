package net.godwand.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SkillWheelSelectionTest {
 @Test void sevenCentersAndTheirBoundariesStaySelectable() {
  for(int mode=0;mode<7;mode++) {
   double a=-Math.PI/2+mode*Math.PI*2/7;
   assertEquals(mode,SkillWheelSelection.modeAt(Math.cos(a)*80,Math.sin(a)*80));
   assertEquals(mode,SkillWheelSelection.modeAt(Math.cos(a)*800,Math.sin(a)*800));
   for(double edge:new double[]{-.44,.44})assertEquals(mode,SkillWheelSelection.modeAt(Math.cos(a+edge)*80,Math.sin(a+edge)*80));
  }
 }
 @Test void centerKeepsCurrentSkillAndTopSeamStaysValid() {
  assertEquals(-1,SkillWheelSelection.modeAt(0,0));assertEquals(-1,SkillWheelSelection.modeAt(30,30));
  assertEquals(0,SkillWheelSelection.modeAt(0,-48));assertEquals(0,SkillWheelSelection.modeAt(-.01,-80));
 }
}
