package net.godwand.client;
import net.godwand.mixin.GameRendererMixin;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class IdleFovCompatibilityTest {
 @Test void inactiveEffectsDoNotCancelTheRemainingZoomHooks() throws Exception {
  var method=java.util.Arrays.stream(GameRendererMixin.class.getDeclaredMethods()).filter(m->m.getName().contains("Fov")||m.getName().contains("FOV")).findFirst().orElseThrow();
  method.setAccessible(true);
  try(var cinema=mockStatic(AtomicCinematic.class);var fuga=mockStatic(FugaCinematic.class);var engine=mockStatic(GodWandCinematicEngine.class)) {
   cinema.when(()->AtomicCinematic.fov(anyDouble(),anyFloat())).thenAnswer(c->c.getArgument(0));
   fuga.when(()->FugaCinematic.fov(anyDouble(),anyFloat())).thenAnswer(c->c.getArgument(0));
   engine.when(()->GodWandCinematicEngine.getFovMultiplier(anyFloat())).thenReturn(1d);
   var result=new CallbackInfoReturnable<Double>("getFov",true,30d);
   method.invoke(mock(GameRendererMixin.class),null,0f,true,result);
   assertFalse(result.isCancelled(),"A no-op FOV hook must leave other zoom handlers reachable");
   assertEquals(30d,result.getReturnValue());
  }
 }
}
