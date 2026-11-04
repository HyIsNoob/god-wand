package net.godwand.config;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class CooldownMigrationTest {
 @TempDir Path dir;
 @Test void oldDefaultsUpgradeWhileCustomCooldownsDamageAndUnknownKeysStay() throws Exception {
  Path file=dir.resolve("server.json");Files.writeString(file,"{\"atomicCooldownTicks\":600,\"supernovaCooldownTicks\":123,\"singularityCooldownTicks\":[20,20,20],\"earthDrillCooldownTicks\":[1,2,3,4],\"atomicDamage\":8888,\"packOption\":true}");
  var first=GodWandConfig.read(file,GodWandConfig.Server.class,new GodWandConfig.Server());
  assertEquals(1200,first.atomicCooldownTicks);assertEquals(123,first.supernovaCooldownTicks);assertArrayEquals(new int[]{60,120,200},first.singularityCooldownTicks);assertArrayEquals(new int[]{1,2,3,4},first.earthDrillCooldownTicks);assertEquals(8888,first.atomicDamage);
  String saved=Files.readString(file);assertTrue(saved.contains("packOption"));
  GodWandConfig.read(file,GodWandConfig.Server.class,new GodWandConfig.Server());assertEquals(saved,Files.readString(file),"Migration runs once");
 }
 @Test void fullChargeDefaultsHaveLongerCooldownsAndDamageStaysUnchanged() {
  var s=new GodWandConfig.Server();for(int[] tiers:new int[][]{s.singularityCooldownTicks,s.earthDrillCooldownTicks,s.stoneCannonCooldownTicks})for(int i=1;i<tiers.length;i++)assertTrue(tiers[i]>tiers[i-1]);
  assertEquals(9999,s.atomicDamage);assertEquals(15,s.atomicShockwaveDamage);assertEquals(900,s.supernovaCooldownTicks);assertEquals(1200,s.atomicCooldownTicks);
 }
}
