package net.godwand.qa;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.godwand.GodWandMod;
import net.godwand.client.*;
import net.godwand.config.GodWandConfig;
import net.godwand.item.GodWandItem;
import net.godwand.skill.FugaManager;
import net.minecraft.client.*;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.*;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import java.nio.file.*;
import java.util.*;

/** Owner-approved controlled native QA. Never included in the release JAR. */
public final class FugaRigProbe implements ClientModInitializer {
 public static int scenario=-1;
 public static final boolean POSE_ONLY=Boolean.getBoolean("godwand.fuga.poseOnly");
 public static PlayerSkin skin;
 private int ticks,stage,since,video;
 private final String run="fuga-native-bow-"+(POSE_ONLY?"pose-":"fire-")+System.currentTimeMillis();
 private final Set<String> captures=new HashSet<>();
 public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
 private void tick(Minecraft c){
  var server=c.getSingleplayerServer();
  if(c.player==null||c.level==null||server==null)return;
  if(!c.gameDirectory.toPath().toAbsolutePath().normalize().endsWith(Path.of("build","fuga-qa","run")))throw new IllegalStateException("Disposable world only");
  if(Files.exists(c.gameDirectory.toPath().getParent().resolve("stop"))){c.stop();return;}
  c.options.pauseOnLostFocus=false;ticks++;
  if(stage==0&&ticks>80){
   if(c.screen!=null)return;
   scenario++;if(scenario>5){System.out.println("FUGA_NATIVE_BOW ALL_PASS classic slim armor cancel Atomic-start");c.stop();return;}
   c.getWindow().setWindowed(1280,720);c.getWindow().setTitle("God Wand — Fuga hand rig QA (no VFX)");
   c.options.framerateLimit().set(60);c.options.renderDistance().set(10);c.options.guiScale().set(2);c.options.setCameraType(CameraType.FIRST_PERSON);
   GodWandConfig.CLIENT.fugaCutscene=true;c.options.keyUse.setDown(false);
   var desired=scenario==2||scenario==3?PlayerSkin.Model.SLIM:PlayerSkin.Model.WIDE;
   for(int i=0;i<1000;i++){var candidate=DefaultPlayerSkin.get(new UUID(0,i));if(candidate.model()==desired){skin=candidate;break;}}
   server.submit(()->{
    var p=server.getPlayerList().getPlayer(c.player.getUUID());FugaManager.cancelCharge(p);p.stopUsingItem();p.setGameMode(GameType.CREATIVE);
    p.serverLevel().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
    for(var e:p.serverLevel().getEntities(p,new AABB(-64,-64,-64,64,128,64)))if(!(e instanceof net.minecraft.world.entity.player.Player))e.discard();
    p.serverLevel().setDayTime(1000);p.serverLevel().setWeatherParameters(60000,0,false,false);
    p.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);
    p.setItemSlot(EquipmentSlot.CHEST,scenario==4?new ItemStack(Items.DIAMOND_CHESTPLATE):ItemStack.EMPTY);
    var wand=new ItemStack(GodWandMod.GOD_WAND_ITEM);GodWandItem.setSkillMode(wand,scenario==5?5:6);
    p.getInventory().setItem(0,wand);p.getInventory().selected=0;p.getCooldowns().removeCooldown(GodWandMod.GOD_WAND_ITEM);
    for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)p.serverLevel().setBlock(new net.minecraft.core.BlockPos(x,-5,z),net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(),2);
    p.connection.teleport(0,-4,0,0,0);p.inventoryMenu.broadcastChanges();
   }).join();
   c.player.getInventory().selected=0;c.player.connection.send(new ServerboundSetCarriedItemPacket(0));
   try{Files.createDirectories(c.gameDirectory.toPath().resolve("screenshots/"+run));}catch(Exception e){throw new RuntimeException(e);}
   stage=1;since=ticks;video=0;captures.clear();System.out.println("FUGA_NATIVE_BOW SETUP "+scenario+" "+skin.model());
  }
  if(stage==1&&ticks-since>50){if(!c.player.getOffhandItem().isEmpty())throw new AssertionError("QA offhand contamination");c.player.setYRot(0);c.player.setXRot(0);c.options.keyUse.setDown(true);c.gameMode.useItem(c.player,InteractionHand.MAIN_HAND);stage=2;since=ticks;}
  if(stage==2){
   c.options.keyUse.setDown(true);
   var f=FugaRenderer.localFrame(0);var atomic=AtomicRenderer.localFrame(0);
   double age=scenario==5?(atomic==null?-1:atomic.age()):(f==null?-1:f.age());
   if(age<0){if(ticks-since>100)throw new AssertionError("cast missing");return;}
   var renderer=c.getEntityRenderDispatcher().getRenderer(c.player);
   if(!(renderer instanceof PlayerRenderer playerRenderer))throw new AssertionError("unexpected renderer");
   if(scenario<4) {
    var cube=playerRenderer.getModel().rightArm.getRandomCube(net.minecraft.util.RandomSource.create(0));
    if(Math.abs(cube.maxX-cube.minX-(skin.model()==PlayerSkin.Model.SLIM?3:4))>.001)throw new AssertionError("wrong arm width");
   }
   if(scenario<5&&age>=205) {
    var m=playerRenderer.getModel();
    if(net.godwand.client.FugaAimRig.availableFor.test(c.player.getId()))throw new AssertionError("custom aim rig still enabled");
    if(Math.abs(m.body.yRot)>.02||Math.abs(m.rightArm.z)>.01||Math.abs(m.leftArm.z)>.01)throw new AssertionError("custom shoulder/torso transform leaked");
    if(Math.abs(m.rightArm.yRot-(m.head.yRot-.5))>.015||Math.abs(m.leftArm.yRot-(m.head.yRot+.1))>.015)throw new AssertionError("not native bow yaw");
    if(Math.abs(m.rightArm.xRot-(m.head.xRot-Math.PI/2))>.06||Math.abs(m.leftArm.xRot-(m.head.xRot-Math.PI/2))>.06)throw new AssertionError("not native bow pitch");
    if(captures.add("live-pose-check"))System.out.println("FUGA_NATIVE_BOW LIVE pose="+m.rightArm.xRot+","+m.rightArm.yRot+" / "+m.leftArm.xRot+","+m.leftArm.yRot+" body="+m.body.yRot);
   }
   if(!playerRenderer.getModel().rightArm.visible||!playerRenderer.getModel().leftArm.visible)throw new AssertionError("visibility leaked");
   if(scenario<4)Screenshot.grab(c.gameDirectory,run+"/s"+scenario+"-"+String.format(Locale.ROOT,"%04d",video++)+".png",c.getMainRenderTarget(),m->{});
   for(int beat:new int[]{12,28,56,88,128,144,156,172,188,205,225,255,295,335,375,415,450})if(age>=beat)capture(c,"s"+scenario+"-age"+beat);
   if(scenario<4 && (scenario==0||scenario==2) && age>230&&age<250){c.player.setYRot(-25);c.player.setXRot(-12);}
   if(scenario<4 && (scenario==0||scenario==2) && age>270&&age<290){c.player.setYRot(30);c.player.setXRot(15);}
   if((scenario==1||scenario==3)&&age>400&&age<420){c.player.setYRot(-25);c.player.setXRot(-12);}
   if((scenario==1||scenario==3)&&age>430&&age<450){c.player.setYRot(30);c.player.setXRot(15);}
   if(age>=(scenario==1||scenario==3?470:300)){
    c.player.getInventory().selected=1;c.player.connection.send(new ServerboundSetCarriedItemPacket(1));c.options.keyUse.setDown(false);
    if(scenario==5)c.gameMode.releaseUsingItem(c.player);
    stage=3;since=ticks;
   }
  }
  if(stage==3&&ticks-since>15){
   if(FugaRenderer.localFrame(0)!=null||FugaCinematic.active()||FugaCinematic.movementLocked()||AtomicCinematic.active())throw new AssertionError("cancel not restored");
   if(c.options.getCameraType()!=CameraType.FIRST_PERSON)throw new AssertionError("perspective preference changed");
   var restored=((PlayerRenderer)c.getEntityRenderDispatcher().getRenderer(c.player)).getModel();
   if(Math.abs(restored.rightArm.z)>.01||Math.abs(restored.leftArm.z)>.01)throw new AssertionError("shoulder pivots leaked");
   capture(c,"s"+scenario+"-restored");System.out.println("FUGA_NATIVE_BOW PASS scenario="+scenario+" model="+skin.model()+" restored");stage=0;
  }
 }
 private void capture(Minecraft c,String name){if(captures.add(name))Screenshot.grab(c.gameDirectory,run+"/"+name+".png",c.getMainRenderTarget(),m->{});}
}
