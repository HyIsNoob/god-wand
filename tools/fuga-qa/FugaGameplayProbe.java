package net.godwand.qa;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.godwand.GodWandMod;
import net.godwand.client.FugaRenderer;
import net.godwand.client.FugaCinematic;
import net.godwand.item.GodWandItem;
import net.godwand.skill.FugaManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.util.*;

/** Development-only real item/network capture; enabled only in its disposable QA directory. */
public final class FugaGameplayProbe implements ClientModInitializer {
    private int ticks,stage,started,run;
    private final Set<String> captured=new HashSet<>();
    private Vec3 anchor;
    private boolean released, checked;
    public void onInitializeClient() {
        if(!Boolean.getBoolean("godwand.fuga.qa"))throw new IllegalStateException("QA flag required");
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    private void tick(Minecraft c) {
        var server=c.getSingleplayerServer();
        if(c.player==null||c.level==null||server==null||!c.gameDirectory.toPath().toAbsolutePath().normalize().endsWith(Path.of("build","fuga-qa","run")))return;
        c.options.pauseOnLostFocus=false;
        if(c.screen!=null)return;
        ticks++;
        if(stage==0&&ticks>80) {
            stage=1;started=ticks;run++;captured.clear();released=false;checked=false;
            c.getWindow().setWindowed(1280,720);c.getWindow().setTitle("God Wand 1.4.0 - Fuga QA preview");
            c.options.renderDistance().set(16);c.options.framerateLimit().set(120);c.options.guiScale().set(2);c.options.broadcastOptions();
            net.godwand.config.GodWandConfig.CLIENT.fugaCutscene=true;
            server.submit(()->{
                var p=server.getPlayerList().getPlayer(c.player.getUUID());
                p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                p.serverLevel().setDayTime(1000);p.serverLevel().setWeatherParameters(60000,0,false,false);
                var wand=new ItemStack(GodWandMod.GOD_WAND_ITEM);GodWandItem.setSkillMode(wand,6);
                p.getInventory().setItem(0,wand);p.getInventory().selected=0;p.getCooldowns().removeCooldown(GodWandMod.GOD_WAND_ITEM);
                p.connection.teleport(0,-4,0,0,1);p.inventoryMenu.broadcastChanges();
            }).join();
            System.out.println("FUGA_QA SETUP: Java="+System.getProperty("java.version")+", isolated flat world; background capture enabled");
        }
        if(stage==1&&ticks-started>180&&GodWandItem.getSkillMode(c.player.getMainHandItem())==6) {
            stage=2;started=ticks;anchor=c.player.position();
            c.player.setYRot(0);c.player.setXRot(1);c.options.keyUse.setDown(true);
            c.gameMode.useItem(c.player,InteractionHand.MAIN_HAND);
            System.out.println("FUGA_QA START: real item use at "+anchor);
        }
        if(stage!=2) {
            if(stage==3&&Files.exists(c.gameDirectory.toPath().getParent().resolve("replay")))try {
                Files.delete(c.gameDirectory.toPath().getParent().resolve("replay"));stage=0;ticks=81;
            }catch(Exception e){throw new RuntimeException(e);}
            return;
        }
        var f=FugaRenderer.localFrame(0);
        if(!released)c.options.keyUse.setDown(true);
        if(f==null) {
            if(ticks-started>550)throw new AssertionError("Fuga never produced a client frame");
            if(released&&ticks-started>200) {
                capture(c,"finished");stage=3;c.options.keyUse.setDown(false);
                System.out.println("FUGA_QA PASS: charge, held aim, release, impact and return completed; FPS="+c.getFps());
            }
            return;
        }
        if(f.phase()==0) {
            for(int m:new int[]{6,18,32,48,64,80,96,110,120,140,175})if(f.age()>=m)capture(c,"charge-"+m);
            if(f.age()>=145&&!checked) {
                checked=true;
                if(!c.player.isUsingItem()||c.player.position().distanceToSqr(anchor)>.03)throw new AssertionError("Held aim lost use or movement lock");
                server.submit(()->{
                    var p=server.getPlayerList().getPlayer(c.player.getUUID());
                    if(!FugaManager.movementLocked(p)||!FugaManager.invulnerable(p))throw new AssertionError("Server charge lock/protection missing");
                }).join();
                System.out.println("FUGA_QA PASS: held beyond ready; real server movement lock and invulnerability");
            }
            if(f.age()>=180&&!released) {
                released=true;c.options.keyUse.setDown(false);c.gameMode.releaseUsingItem(c.player);
                System.out.println("FUGA_QA RELEASE");
            }
        } else if(f.phase()==1) {
            for(int m:new int[]{1,5,10,16,24,32})if(f.age()>=m)capture(c,"flight-"+m);
        } else if(f.phase()==2) {
            for(int m:new int[]{1,10,24,35,39,41,48,60,80,105,120,135,150,158})if(f.age()>=m)capture(c,"impact-"+m);
        }
    }
    private void capture(Minecraft c,String name) {
        if(!captured.add(name))return;
        String file="fuga-qa-"+run+"-"+name+".png";
        Screenshot.grab(c.gameDirectory,file,c.getMainRenderTarget(),m->{});
        System.out.println("FUGA_QA CAPTURE "+file+" fps="+c.getFps()+" camera="+c.gameRenderer.getMainCamera().getPosition()+" director="+FugaCinematic.active());
    }
}
