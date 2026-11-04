package net.godwand.qa;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.godwand.GodWandMod;
import net.godwand.client.AtomicCinematic;
import net.godwand.client.AtomicRenderer;
import net.godwand.client.FugaCinematic;
import net.godwand.client.FugaRenderer;
import net.godwand.config.GodWandConfig;
import net.godwand.item.GodWandItem;
import net.godwand.skill.FugaManager;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.util.*;

/** Optional disposable-world QA only; never included in a release JAR. */
public final class FugaPlan1Probe implements ClientModInitializer {
    private int ticks, stage, since, scenario;
    private Vec3 anchor;
    private final Set<String> captured = new HashSet<>();
    private final String run = Long.toString(System.currentTimeMillis());
    private boolean cancelling, menuOpened, menuClosed;
    private int videoFrame;

    public void onInitializeClient() {
        if (!Boolean.getBoolean("godwand.fuga.qa")) throw new IllegalStateException("QA flag required");
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    private void tick(Minecraft c) {
        var server = c.getSingleplayerServer();
        if (c.player == null || c.level == null || server == null
                || !c.gameDirectory.toPath().toAbsolutePath().normalize().endsWith(Path.of("build", "fuga-qa", "run"))) return;
        c.options.pauseOnLostFocus = false;
        if (Files.exists(c.gameDirectory.toPath().getParent().resolve("stop"))) { c.stop(); return; }
        ticks++;
        if (stage == 0 && ticks > 80) {
            if (c.screen != null) return;
            c.getWindow().setWindowed(1280,720);
            c.getWindow().setTitle("God Wand 1.4.0 - Fuga Plan 1 QA");
            c.options.framerateLimit().set(60); c.options.renderDistance().set(12);
            c.options.guiScale().set(2); c.options.setCameraType(CameraType.FIRST_PERSON); c.options.broadcastOptions();
            c.options.keyUse.setDown(false); c.options.keyUp.setDown(false);
            GodWandConfig.CLIENT.fugaCutscene = scenario != 1;
            server.submit(() -> {
                var p=server.getPlayerList().getPlayer(c.player.getUUID());
                FugaManager.cancelCharge(p);
                p.stopUsingItem(); p.setGameMode(GameType.CREATIVE);
                p.serverLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,server);
                for(var entity:p.serverLevel().getEntities(p,new net.minecraft.world.phys.AABB(-64,-64,-64,64,128,64)))
                    if(!(entity instanceof net.minecraft.world.entity.player.Player))entity.discard();
                p.serverLevel().setDayTime(1000); p.serverLevel().setWeatherParameters(60000,0,false,false);
                var wand=new ItemStack(GodWandMod.GOD_WAND_ITEM);
                GodWandItem.setSkillMode(wand,scenario==4?5:6);
                p.getInventory().setItem(0,wand);p.getInventory().selected=0;
                p.getCooldowns().removeCooldown(GodWandMod.GOD_WAND_ITEM);
                for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)
                    p.serverLevel().setBlock(new net.minecraft.core.BlockPos(x,-5,z),net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(),2);
                p.connection.teleport(0,-4,0,0,0);p.inventoryMenu.broadcastChanges();
            }).join();
            c.player.getInventory().selected=0;c.player.connection.send(new ServerboundSetCarriedItemPacket(0));
            stage=1;since=ticks;captured.clear();cancelling=false;menuOpened=menuClosed=false;
            if(scenario==0)try {Files.createDirectories(c.gameDirectory.toPath().resolve("screenshots/fuga-plan1-"+run));}
            catch(java.io.IOException e) {throw new RuntimeException(e);}
            System.out.println("FUGA_PLAN1 SETUP scenario="+scenario+" Java="+System.getProperty("java.version"));
        }
        if (stage==1 && ticks-since>60 && GodWandItem.getSkillMode(c.player.getMainHandItem())==(scenario==4?5:6)) {
            stage=2;since=ticks;anchor=c.player.position();
            c.player.setYRot(0);c.player.setXRot(0);c.options.keyUse.setDown(true);
            c.gameMode.useItem(c.player,InteractionHand.MAIN_HAND);
            System.out.println("FUGA_PLAN1 START scenario="+scenario);
        }
        if(stage==1&&ticks-since>250)throw new AssertionError("QA inventory setup timed out");
        if (stage==2) runScenario(c);
        if (stage==3 && ticks-since>12) {
            if (FugaRenderer.localFrame(0)!=null || FugaCinematic.active() || FugaCinematic.movementLocked())
                throw new AssertionError("Fuga failed to release camera/pose state, scenario="+scenario);
            if (AtomicRenderer.localFrame(0)!=null || AtomicCinematic.active())
                throw new AssertionError("Atomic failed to restore after cancellation");
            if(c.options.getCameraType()!=CameraType.FIRST_PERSON)throw new AssertionError("Perspective preference changed");
            c.options.keyUse.setDown(false);c.options.keyUp.setDown(false);
            capture(c,"restored");System.out.println("FUGA_PLAN1 PASS scenario="+scenario+" restored camera/input");
            if(++scenario>4) {stage=4;since=ticks;System.out.println("FUGA_PLAN1 ALL_PASS native timeline, aim, cinematic off, cancel, spectator, Atomic regression");}
            else {stage=0;}
        }
        if(stage==4 && ticks-since>30)c.stop();
        if(stage==2 && ticks-since>650)throw new AssertionError("QA timeout scenario="+scenario);
    }
    private void runScenario(Minecraft c) {
        if(!cancelling)c.options.keyUse.setDown(true);
        if(scenario==4) {
            var f=AtomicRenderer.localFrame(0);
            if(f==null)return;
            if(f.age()>=80)capture(c,"atomic-regression");
            if(f.age()>=100&&!cancelling) {
                c.options.keyUse.setDown(false);c.gameMode.releaseUsingItem(c.player);finish();
            }
            return;
        }
        var f=FugaRenderer.localFrame(0);
        if(f==null)return;
        if(f.phase()!=0)throw new AssertionError("Plan1 QA must not fire a projectile");
        if(scenario==0) {
            if(f.age()<=220)Screenshot.grab(c.gameDirectory,"fuga-plan1-"+run+"/ritual-"+String.format(java.util.Locale.ROOT,"%04d",videoFrame++)+".png",c.getMainRenderTarget(),m->{});
            for(int t:new int[]{12,24,36,48,65,82,100,128,150,172,190,199,205,230,270,320,380})
                if(f.age()>=t)capture(c,"charge-"+t);
            if(f.age()>235&&f.age()<260) {c.player.setYRot(-35);c.player.setXRot(-15);}
            if(f.age()>305&&f.age()<335) {c.player.setYRot(30);c.player.setXRot(20);}
            if(f.age()>345&&f.age()<375)c.options.keyUp.setDown(true);else c.options.keyUp.setDown(false);
            if(f.age()>=280&&!menuOpened) {
                menuOpened=true;c.setScreen(new Screen(Component.literal("Fuga QA menu suspension")) {public boolean isPauseScreen(){return false;}});
                c.options.keyUse.setDown(true);
                if(FugaCinematic.active())throw new AssertionError("Menu did not suspend cinematic");
            }
            if(f.age()>=292&&menuOpened&&!menuClosed) {
                menuClosed=true;c.setScreen(null);c.options.keyUse.setDown(true);
                if(!FugaCinematic.active())throw new AssertionError("Camera did not resume after menu");
            }
            if(f.age()>=395) {
                checkHeld(c);switchSlot(c);finish();
            }
        } else if(scenario==1) {
            for(int t:new int[]{20,80,160,205,260})if(f.age()>=t)capture(c,"no-cutscene-"+t);
            if(FugaCinematic.environmentGain()!=1)throw new AssertionError("Cinematic-off scene darkening");
            if(f.age()>220) {c.player.setYRot(45);c.player.setXRot(-20);}
            if(f.age()>=285) {checkHeld(c);switchSlot(c);finish();}
        } else if(scenario==2&&f.age()>=70) {
            capture(c,"early-cancel");c.options.keyUse.setDown(false);c.gameMode.releaseUsingItem(c.player);finish();
        } else if(scenario==3&&f.age()>=90) {
            capture(c,"before-spectator");c.getSingleplayerServer().submit(()->c.getSingleplayerServer().getPlayerList().getPlayer(c.player.getUUID()).setGameMode(GameType.SPECTATOR)).join();finish();
        }
    }
    private void checkHeld(Minecraft c) {
        if(!c.player.isUsingItem()||c.player.position().distanceToSqr(anchor)>.03)throw new AssertionError("Held aim lost movement lock");
        c.getSingleplayerServer().submit(()->{
            var p=c.getSingleplayerServer().getPlayerList().getPlayer(c.player.getUUID());
            if(!FugaManager.movementLocked(p)||!FugaManager.invulnerable(p))throw new AssertionError("Server hold guard missing");
        }).join();
        System.out.println("FUGA_PLAN1 PASS held aim scenario="+scenario+" camera="+c.gameRenderer.getMainCamera().getPosition());
    }
    private void switchSlot(Minecraft c) {
        c.player.getInventory().selected=1;c.player.connection.send(new ServerboundSetCarriedItemPacket(1));
        c.options.keyUse.setDown(false);c.options.keyUp.setDown(false);
    }
    private void finish() {cancelling=true;stage=3;since=ticks;}
    private void capture(Minecraft c,String name) {
        if(!captured.add(name))return;
        String filename="fuga-plan1-"+run+"-s"+scenario+"-"+name+".png";
        Screenshot.grab(c.gameDirectory,filename,c.getMainRenderTarget(),m->{});
        System.out.println("FUGA_PLAN1 CAPTURE "+filename+" fps="+c.getFps());
    }
}
