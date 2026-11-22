package net.godwand.network;

import net.godwand.skill.BlockDominionRules;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;

/**
 * Lightweight server-to-client state sync for Block Dominion (mode 9).
 * Clients animate each orbiting/stowed/volley block locally from blockStateIds without spawning 32 display entities.
 */
public record BlockDominionStatePayload(
        int playerId,
        int mode,
        int flags,
        int volleyTicks,
        int[] blockStateIds,
        int eventKind,
        double eventX,
        double eventY,
        double eventZ,
        int eventBlockId
) implements CustomPacketPayload {
    public BlockDominionStatePayload {
        if (mode < 0 || mode > 2) throw new IllegalArgumentException("Invalid Block Dominion mode: " + mode);
        if (eventKind < 0 || eventKind > BlockDominionRules.EVENT_STOW) {
            throw new IllegalArgumentException("Invalid Block Dominion event: " + eventKind);
        }
        if (blockStateIds == null) blockStateIds = new int[0];
        if (blockStateIds.length > BlockDominionRules.MAX_CAPACITY) {
            throw new IllegalArgumentException("Too many Block Dominion states: " + blockStateIds.length);
        }
        blockStateIds = blockStateIds.clone();
        for (double c : new double[]{eventX, eventY, eventZ}) {
            if (!Double.isFinite(c) || Math.abs(c) > 30_000_000) {
                throw new IllegalArgumentException("Invalid Block Dominion coordinate");
            }
        }
    }

    @Override
    public int[] blockStateIds() {
        return blockStateIds.clone();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof BlockDominionStatePayload o)) return false;
        return playerId == o.playerId && mode == o.mode && flags == o.flags && volleyTicks == o.volleyTicks
                && Arrays.equals(blockStateIds, o.blockStateIds) && eventKind == o.eventKind
                && Double.compare(eventX, o.eventX) == 0 && Double.compare(eventY, o.eventY) == 0
                && Double.compare(eventZ, o.eventZ) == 0 && eventBlockId == o.eventBlockId;
    }

    @Override
    public int hashCode() {
        int h = Integer.hashCode(playerId);
        h = 31 * h + mode;
        h = 31 * h + flags;
        h = 31 * h + volleyTicks;
        h = 31 * h + Arrays.hashCode(blockStateIds);
        h = 31 * h + eventKind;
        h = 31 * h + Double.hashCode(eventX);
        h = 31 * h + Double.hashCode(eventY);
        h = 31 * h + Double.hashCode(eventZ);
        h = 31 * h + eventBlockId;
        return h;
    }

    public static final Type<BlockDominionStatePayload> ID =
            new Type<>(ResourceLocation.fromNamespaceAndPath("godwand", "block_dominion_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BlockDominionStatePayload> CODEC = new StreamCodec<>() {
        @Override
        public BlockDominionStatePayload decode(RegistryFriendlyByteBuf buf) {
            int playerId = buf.readVarInt();
            int mode = buf.readVarInt();
            int flags = buf.readVarInt();
            int volleyTicks = buf.readVarInt();
            int count = buf.readVarInt();
            if (count < 0 || count > BlockDominionRules.MAX_CAPACITY) {
                throw new IllegalArgumentException("Invalid Block Dominion state count: " + count);
            }
            int[] ids = new int[count];
            for (int i = 0; i < count; i++) ids[i] = buf.readVarInt();
            int eventKind = buf.readVarInt();
            double ex = buf.readDouble();
            double ey = buf.readDouble();
            double ez = buf.readDouble();
            int eventBlockId = buf.readVarInt();
            return new BlockDominionStatePayload(playerId, mode, flags, volleyTicks, ids, eventKind, ex, ey, ez, eventBlockId);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, BlockDominionStatePayload v) {
            buf.writeVarInt(v.playerId);
            buf.writeVarInt(v.mode);
            buf.writeVarInt(v.flags);
            buf.writeVarInt(v.volleyTicks);
            buf.writeVarInt(v.blockStateIds.length);
            for (int id : v.blockStateIds) buf.writeVarInt(id);
            buf.writeVarInt(v.eventKind);
            buf.writeDouble(v.eventX);
            buf.writeDouble(v.eventY);
            buf.writeDouble(v.eventZ);
            buf.writeVarInt(v.eventBlockId);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
