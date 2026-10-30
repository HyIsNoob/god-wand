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
    private static final Map<ServerPlayer,SkillCooldownState> STATES=new WeakHashMap<>();
    private SkillCooldowns() {}
    private static SkillCooldownState state(ServerPlayer player) {return STATES.computeIfAbsent(player,p->new SkillCooldownState());}
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
