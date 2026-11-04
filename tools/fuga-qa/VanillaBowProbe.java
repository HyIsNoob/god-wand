package net.godwand.qa;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import java.nio.file.*;
import java.util.*;
public final class VanillaBowProbe implements ClientModInitializer {
 public static boolean active;public static int age;
 private int ticks,stage,since,video;
 private final String run="vanilla-bow-"+System.currentTimeMillis();private final Set<String> captured=new HashSet<>();
 public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
 private void tick(Minecraft c){
  var server=c.getSingleplayerServer();if(c.player==null||c.level==null||server==null)return;
  if(!c.gameDirectory.toPath().toAbsolutePath().normalize().endsWith(Path.of("build","fuga-qa","run")))throw new IllegalStateException("QA world only");
  if(Files.exists(c.gameDirectory.toPath().getParent().resolve("stop"))){c.stop();return;}
  c.options.pauseOnLostFocus=false;ticks++;
  if(stage==0&&ticks>80&&c.screen==null){
   c.getWindow().setWindowed(1280,720);c.getWindow().setTitle("Vanilla Minecraft 1.21.1 bow baseline — item hidden");c.options.framerateLimit().set(60);c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
   server.submit(()->{
    var p=server.getPlayerList().getPlayer(c.player.getUUID());p.stopUsingItem();p.setGameMode(GameType.CREATIVE);
    p.getInventory().clearContent();p.getInventory().selected=0;p.getInventory().setItem(40,new ItemStack(Items.BOW));
    p.serverLevel().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
    for(var e:p.serverLevel().getEntities(p,new AABB(-64,-64,-64,64,128,64)))if(!(e instanceof net.minecraft.world.entity.player.Player))e.discard();
    p.serverLevel().setDayTime(1000);p.serverLevel().setWeatherParameters(60000,0,false,false);
    p.connection.teleport(0,-4,0,0,0);p.inventoryMenu.broadcastChanges();
   }).join();
   try{Files.createDirectories(c.gameDirectory.toPath().resolve("screenshots/"+run));}catch(Exception e){throw new RuntimeException(e);}
   stage=1;since=ticks;
  }
  if(stage==1&&ticks-since>60){active=true;c.options.keyUse.setDown(true);c.gameMode.useItem(c.player,InteractionHand.OFF_HAND);stage=2;since=ticks;}
  if(stage==2){age=ticks-since;c.options.keyUse.setDown(true);
   if(age>10&&!c.player.isUsingItem())throw new AssertionError("vanilla bow not held");
   if(age>10){
    var m=((PlayerRenderer)c.getEntityRenderDispatcher().getRenderer(c.player)).getModel();
    if(m.leftArmPose!=net.minecraft.client.model.HumanoidModel.ArmPose.BOW_AND_ARROW)throw new AssertionError("not vanilla left bow");
    if(age==30)System.out.println("VANILLA_BOW left=BOW_AND_ARROW right="+m.rightArmPose+" rotations="+m.rightArm.xRot+","+m.rightArm.yRot+" / "+m.leftArm.xRot+","+m.leftArm.yRot);
   }
   Screenshot.grab(c.gameDirectory,run+"/frame-"+String.format(Locale.ROOT,"%04d",video++)+".png",c.getMainRenderTarget(),m->{});
   for(int t:new int[]{30,70,110,150,190,230,270})if(age>=t&&captured.add(""+t))Screenshot.grab(c.gameDirectory,run+"/age"+t+".png",c.getMainRenderTarget(),m->{});
   if(age>210&&age<225){c.player.setYRot(-25);c.player.setXRot(-12);}
   if(age>250&&age<265){c.player.setYRot(30);c.player.setXRot(15);}
   if(age>=290){c.options.keyUse.setDown(false);active=false;server.submit(()->server.getPlayerList().getPlayer(c.player.getUUID()).stopUsingItem()).join();System.out.println("VANILLA_BOW BASELINE_PASS");c.stop();}
  }
 }
 public static Vec3 cameraPosition(){var c=Minecraft.getInstance();var p=c.player.getPosition(0);double a=angle();var f=Vec3.directionFromRotation(0,c.player.getViewYRot(0));var r=f.cross(new Vec3(0,1,0));return p.add(f.scale(Math.cos(a)*1.95)).add(r.scale(Math.sin(a)*1.95)).add(0,1.50,0);}
 public static Vec3 focus(){return Minecraft.getInstance().player.position().add(0,1.3,0).add(Vec3.directionFromRotation(0,Minecraft.getInstance().player.getYRot()).scale(.3));}
 private static double angle(){return age<50?.6:age<90?Math.PI/2:age<130?-Math.PI/2:age<170?2.4:0;}
}
