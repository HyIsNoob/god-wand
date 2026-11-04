package net.godwand.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.godwand.item.GodWandItem;
import net.godwand.item.WandSkillLock;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class GodWandCommands {
    private static final String[] SKILLS = {"spatial_path", "singularity", "earth_drill", "supernova", "stone_cannon", "atomic"};
    private GodWandCommands() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("godwand").requires(source -> source.hasPermission(2));
        var lock = Commands.literal("lock");
        for (int mode = 0; mode < SKILLS.length; mode++) {
            final int skill = mode;
            lock.then(Commands.literal(SKILLS[mode]).executes(context -> change(context.getSource(), skill)));
        }
        root.then(lock).then(Commands.literal("unlock").executes(context -> change(context.getSource(), -1)));
        dispatcher.register(root);
    }
    private static int change(CommandSourceStack source, int mode) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        var stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof GodWandItem))
            throw new SimpleCommandExceptionType(Component.translatable("command.godwand.hold_wand")).create();
        if (player.isUsingItem())
            throw new SimpleCommandExceptionType(Component.translatable("command.godwand.stop_casting")).create();
        if (mode < 0) WandSkillLock.unlock(stack); else WandSkillLock.lock(stack, mode);
        player.containerMenu.broadcastChanges();
        source.sendSuccess(() -> mode < 0 ? Component.translatable("command.godwand.unlocked") :
                Component.translatable("message.godwand.skill_locked", Component.translatable("skill.godwand." + mode)), true);
        return 1;
    }
}
