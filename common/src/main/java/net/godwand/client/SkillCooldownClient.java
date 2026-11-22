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
    /** What the wheel and HUD show: Blue/Red show whichever of their two timers runs longer. */
    public static int shown(int mode) {return mode==GodWandItem.MODE_BLUE_RED?Math.max(remaining(mode),remaining(SkillCooldownState.RED_SLOT)):remaining(mode);}
    public static float fraction(int mode) {
        int slot=mode==GodWandItem.MODE_BLUE_RED&&remaining(SkillCooldownState.RED_SLOT)>remaining(mode)?SkillCooldownState.RED_SLOT:mode;
        return remaining(slot)==0?0:timers.fraction(slot,clock);
    }
    /** Gates right click; on Blue/Red right click is Red, on Block Dominion single/rapid fire has no cooldown. */
    public static boolean canUse(int mode) {
        if(mode==GodWandItem.MODE_BLUE_RED)return remaining(SkillCooldownState.RED_SLOT)==0;
        if(mode==GodWandItem.MODE_BLOCK_DOMINION)return true;
        return remaining(mode)==0||mode==1&&recall;
    }
    public static void render(GuiGraphics graphics) {
        var client=Minecraft.getInstance();observe(client);
        if(observer==null||client.screen!=null||client.options.hideGui||AtomicCinematic.active()||FugaCinematic.active()||SilentGateCinematic.active()||PurpleCinematic.active())return;
        var stack=observer.getMainHandItem();if(!(stack.getItem() instanceof GodWandItem))stack=observer.getOffhandItem();
        if(!(stack.getItem() instanceof GodWandItem))return;
        int mode=GodWandItem.getSkillMode(stack),left=shown(mode);if(left==0)return;
        int x=graphics.guiWidth()/2,y=graphics.guiHeight()-84;
        if(mode==GodWandItem.MODE_BLUE_RED) {
            // Blue and Red each have their own timer; a Purple sets both at once (owner, 2026-11-20: say which one is cooling down).
            int blue=remaining(mode),red=remaining(SkillCooldownState.RED_SLOT);
            if(blue==red){graphics.drawCenteredString(client.font,text("skill.godwand.8",blue),x,y,0xFFC89BFF);return;}
            var b=text("skill.godwand.blue",blue);var r=text("skill.godwand.red",red);
            if(red==0){graphics.drawCenteredString(client.font,b,x,y,0xFF7FC8FF);return;}
            if(blue==0){graphics.drawCenteredString(client.font,r,x,y,0xFFFF8080);return;}
            int wb=client.font.width(b),wr=client.font.width(r),start=x-(wb+12+wr)/2;
            graphics.drawCenteredString(client.font,b,start+wb/2,y,0xFF7FC8FF);
            graphics.drawCenteredString(client.font,r,start+wb+12+wr/2,y,0xFFFF8080);
            return;
        }
        graphics.drawCenteredString(client.font,text("skill.godwand."+mode,left),x,y,0xFFE0C998);
    }
    private static Component text(String name,int ticks) {return Component.translatable("message.godwand.skill_cooldown",Component.translatable(name),(ticks+19)/20);}
}
