package net.godwand.client;

import net.godwand.item.GodWandItem;
import net.godwand.network.SkillCooldownPayload;
import net.godwand.skill.SkillCooldownState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public final class SkillCooldownClient {
    private static Player observer;
    private static Object session;
    private static long clock;
    private static SkillCooldownState timers=new SkillCooldownState();
    private static boolean recall;
    private SkillCooldownClient() {}
    private static void observe(Minecraft client) {
        var connection=client.getConnection();
        boolean died=observer!=null&&client.player!=observer&&observer.getHealth()<=0;
        if(connection!=session||died) {session=connection;timers=new SkillCooldownState();clock=0;recall=false;}
        if(client.player!=observer)recall=false;
        observer=client.player;
    }
    public static void tick(Minecraft client) {
        observe(client);
        if(observer!=null&&client.level!=null&&!client.isPaused())clock++;
    }
    public static void receive(SkillCooldownPayload payload) {
        var client=Minecraft.getInstance();observe(client);if(observer==null)return;
        timers.set(payload.mode(),payload.durationTicks(),payload.remainingTicks(),clock);
        if(payload.mode()==1)recall=payload.recall();
    }
    public static int remaining(int mode) {observe(Minecraft.getInstance());return observer==null?0:timers.remaining(mode,clock);}
    public static float fraction(int mode) {return remaining(mode)==0?0:timers.fraction(mode,clock);}
    public static boolean canUse(int mode) {return remaining(mode)==0||mode==1&&recall;}
    public static void render(GuiGraphics graphics) {
        var client=Minecraft.getInstance();observe(client);
        if(observer==null||client.screen!=null||client.options.hideGui||AtomicCinematic.active())return;
        var stack=observer.getMainHandItem();if(!(stack.getItem() instanceof GodWandItem))stack=observer.getOffhandItem();
        if(!(stack.getItem() instanceof GodWandItem))return;
        int mode=GodWandItem.getSkillMode(stack),left=remaining(mode);if(left==0)return;
        var text=Component.translatable("message.godwand.skill_cooldown",Component.translatable("skill.godwand."+mode),(left+19)/20);
        graphics.drawCenteredString(client.font,text,graphics.guiWidth()/2,graphics.guiHeight()-84,0xFFE0C998);
    }
}
