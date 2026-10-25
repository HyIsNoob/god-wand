package net.godwand.network;

import net.godwand.item.GodWandItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record SelectSkillPayload(int mode, int slot) implements GodWandPacket {
    public static final ResourceLocation ID = new ResourceLocation("godwand", "select_skill");

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(mode);
        buf.writeVarInt(slot);
    }

    public static SelectSkillPayload read(FriendlyByteBuf buf) {
        return new SelectSkillPayload(buf.readVarInt(), buf.readVarInt());
    }

    public boolean matchesHeldSlot(int selectedSlot) {
        return mode >= 0 && mode < GodWandItem.TOTAL_MODES
                && (slot == 40 || (slot >= 0 && slot < 9 && slot == selectedSlot));
    }
}
