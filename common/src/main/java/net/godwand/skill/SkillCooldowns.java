package net.godwand.skill;

import net.godwand.network.SkillCooldownPayload;
import net.godwand.platform.Services;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import java.util.Map;
import java.util.WeakHashMap;

/** Player identity, not the wand stack, owns cooldowns; switching or copying wands cannot bypass them. */
public final class SkillCooldowns {
    private record Entry(java.lang.ref.WeakReference<ServerPlayer> owner, SkillCooldownState timers) {}
    private static final Map<ServerPlayer,Entry> STATES=new WeakHashMap<>();
    private SkillCooldowns() {}
    private static SkillCooldownState state(ServerPlayer player) {
        var entry=STATES.get(player);
        // Entity.equals uses the reused network ID; respawn must not inherit the old tick clock.
        if(entry==null || entry.owner().get()!=player) {
            STATES.remove(player);
            entry=new Entry(new java.lang.ref.WeakReference<>(player),new SkillCooldownState());
            STATES.put(player,entry);
        }
        return entry.timers();
    }
    public static void reset(ServerPlayer player) {
        STATES.remove(player);
        for(int mode=0;mode<net.godwand.item.GodWandItem.TOTAL_MODES;mode++)sync(player,mode);
    }
    public static boolean ready(Player user,int mode) {
        if(!(user instanceof ServerPlayer player))return true;
        int left=state(player).remaining(mode,player.tickCount);
        if(left==0)return true;
        sync(player,mode);
        player.displayClientMessage(Component.translatable("message.godwand.skill_cooldown",Component.translatable("skill.godwand."+mode),(left+19)/20),true);
        return false;
    }
    public static void start(Player user,int mode,int ticks) {
        if(!(user instanceof ServerPlayer player))return;
        state(player).set(mode,ticks,ticks,player.tickCount);sync(player,mode);
    }
    public static void sync(ServerPlayer player,int mode) {
        if(player==null||player.hasDisconnected())return;
        var state=state(player);
        Services.NETWORK.sendToPlayer(player,new SkillCooldownPayload(mode,state.duration(mode),state.remaining(mode,player.tickCount),mode==1&&ShockwaveSlicer.hasActiveTask(player)));
    }
}
