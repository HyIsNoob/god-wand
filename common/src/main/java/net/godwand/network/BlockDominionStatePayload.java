package net.godwand.network;

import net.godwand.skill.BlockDominionBrain;
import net.godwand.skill.BlockDominionRules;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;

/**
 * Lightweight server-to-client state sync for Block Dominion (mode 9).
 * Carries reserve BlockState IDs, current brain formation, threat vectors and event triggers.
 */
public record BlockDominionStatePayload(
        int playerId,
        int mode,
        int flags,
        int volleyTicks,
        int formationKind,
        float threat1X, float threat1Y, float threat1Z,
        float threat2X, float threat2Y, float threat2Z,
        int[] blockStateIds,
        int eventKind,
        double eventX,
        double eventY,
        double eventZ,
        int eventBlockId
) implements CustomPacketPayload {
    public BlockDominionStatePayload {
        if (mode < 0 || mode > 2) throw new IllegalArgumentException("Invalid Block Dominion mode: " + mode);
        if (formationKind < 0 || formationKind >= BlockDominionBrain.FormationKind.values().length) {
            throw new IllegalArgumentException("Invalid Block Dominion formation: " + formationKind);
        }
        if (eventKind < 0 || eventKind > BlockDominionRules.EVENT_STOW) {
            throw new IllegalArgumentException("Invalid Block Dominion event: " + eventKind);
        }
        if (blockStateIds == null) blockStateIds = new int[0];
        if (blockStateIds.length > BlockDominionRules.MAX_CAPACITY) {
            throw new IllegalArgumentException("Too many Block Dominion states: " + blockStateIds.length);
        }
        blockStateIds = blockStateIds.clone();
        for (float f : new float[]{threat1X, threat1Y, threat1Z, threat2X, threat2Y, threat2Z}) {
            if (!Float.isFinite(f) || Math.abs(f) > 2.0f) {
                throw new IllegalArgumentException("Invalid Block Dominion threat vector");
            }
        }
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
                && formationKind == o.formationKind
                && Float.compare(threat1X, o.threat1X) == 0 && Float.compare(threat1Y, o.threat1Y) == 0 && Float.compare(threat1Z, o.threat1Z) == 0
                && Float.compare(threat2X, o.threat2X) == 0 && Float.compare(threat2Y, o.threat2Y) == 0 && Float.compare(threat2Z, o.threat2Z) == 0
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
        h = 31 * h + formationKind;
        h = 31 * h + Float.hashCode(threat1X);
        h = 31 * h + Float.hashCode(threat1Y);
        h = 31 * h + Float.hashCode(threat1Z);
        h = 31 * h + Float.hashCode(threat2X);
        h = 31 * h + Float.hashCode(threat2Y);
        h = 31 * h + Float.hashCode(threat2Z);
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
            int formationKind = buf.readVarInt();
            float t1x = buf.readFloat(), t1y = buf.readFloat(), t1z = buf.readFloat();
            float t2x = buf.readFloat(), t2y = buf.readFloat(), t2z = buf.readFloat();
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
            return new BlockDominionStatePayload(playerId, mode, flags, volleyTicks, formationKind,
                    t1x, t1y, t1z, t2x, t2y, t2z, ids, eventKind, ex, ey, ez, eventBlockId);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, BlockDominionStatePayload v) {
            buf.writeVarInt(v.playerId);
            buf.writeVarInt(v.mode);
            buf.writeVarInt(v.flags);
            buf.writeVarInt(v.volleyTicks);
            buf.writeVarInt(v.formationKind);
            buf.writeFloat(v.threat1X); buf.writeFloat(v.threat1Y); buf.writeFloat(v.threat1Z);
            buf.writeFloat(v.threat2X); buf.writeFloat(v.threat2Y); buf.writeFloat(v.threat2Z);
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
