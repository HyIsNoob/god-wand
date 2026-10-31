package net.godwand.config;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CooldownSourceParityTest {
 private static List<String> calls(String source) {
  return Pattern.compile("SkillCooldowns\\.(?:ready|start|sync)\\([^;]*?\\)").matcher(source).results().map(m->m.group().replaceAll("\\s+","")).toList();
 }
 @Test void everyVersionUsesTheSameSkillTimersInsteadOfVanillaWholeWandCooldown() throws Exception {
  Path root=Path.of("..").toAbsolutePath().normalize();
  for(String profile:new String[]{"fabric-1.20.1","forge-1.20.1","mc-26.3","mc-26.1.2","mc-1.21.11"})for(String file:new String[]{"item/GodWandItem.java","skill/SpatialPathWalker.java","skill/ShockwaveSlicer.java"}) {
   String shared=Files.readString(root.resolve("common/src/main/java/net/godwand/"+file));
   String port=Files.readString(root.resolve("build/shared/"+profile+"/src/main/java/net/godwand/"+file));
   assertFalse(port.contains(".getCooldowns()"),profile+": "+file+" still uses a whole-item cooldown");
   assertEquals(calls(shared),calls(port),profile+": "+file+" changed shared skill cooldown behavior");
  }
 }
}
