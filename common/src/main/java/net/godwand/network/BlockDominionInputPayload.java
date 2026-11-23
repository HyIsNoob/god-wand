package net.godwand.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-server input for Block Dominion (mode 9):
 * cycle mode, lift targeted block, toggle wing flight, or stream 3D flight controls.
 */
public record BlockDominionInputPayload(int action, BlockPos targetPos) implements CustomPacketPayload {
    public static final int ACTION_CYCLE_MODE = 0;
    public static final int ACTION_PICKUP = 1;
    public static final int ACTION_TOGGLE_FLIGHT = 2;
    public static final int ACTION_FLIGHT_CONTROL = 3;
    public static final int ACTION_EARTHEN_SURGE = 4;
    public static final int ACTION_TOGGLE_FLIGHT_STYLE = 5;
    public static final int ACTION_STAIR_INTENT = 6;
    public static final int ACTION_CAGE_ENTITY = 7;

    public BlockDominionInputPayload {
        if (action < ACTION_CYCLE_MODE || action > ACTION_CAGE_ENTITY) {
            throw new IllegalArgumentException("Invalid Block Dominion input action: " + action);
        }
        if (targetPos == null) targetPos = BlockPos.ZERO;
    }

    /** Sends Monolith Cage lock request targeting living entity {@code entityId}. */
    public static BlockDominionInputPayload cageEntity(int entityId) {
        return new BlockDominionInputPayload(ACTION_CAGE_ENTITY, new BlockPos(entityId, 0, 0));
    }

    public int cageEntityId() {
        return targetPos.getX();
    }

    /** Sends stair climbing intent (ascending=true when holding Space while walking). */
    public static BlockDominionInputPayload stairIntent(boolean ascending) {
        return new BlockDominionInputPayload(ACTION_STAIR_INTENT, new BlockPos(0, ascending ? 1 : 0, 0));
    }

    public boolean stairAscending() {
        return targetPos.getY() > 0;
    }

    /** Packs 3D wing-flight steering (-1..1 axes + boost flag) cleanly into targetPos. */
    public static BlockDominionInputPayload flightControl(int forward, int strafe, int vertical, boolean boost) {
        int fx = Math.max(-1, Math.min(1, forward));
        int fz = Math.max(-1, Math.min(1, strafe));
        int fy = Math.max(-1, Math.min(1, vertical)) + (boost ? 10 : 0);
        return new BlockDominionInputPayload(ACTION_FLIGHT_CONTROL, new BlockPos(fx, fy, fz));
    }

    public int flightForward() {
        return Math.max(-1, Math.min(1, targetPos.getX()));
    }

    public int flightStrafe() {
        return Math.max(-1, Math.min(1, targetPos.getZ()));
    }

    public boolean flightBoost() {
        return targetPos.getY() >= 5;
    }

    public int flightVertical() {
        int raw = flightBoost() ? targetPos.getY() - 10 : targetPos.getY();
        return Math.max(-1, Math.min(1, raw));
    }

    public static final Type<BlockDominionInputPayload> ID =
            new Type<>(ResourceLocation.fromNamespaceAndPath("godwand", "block_dominion_input"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BlockDominionInputPayload> CODEC = new StreamCodec<>() {
        @Override
        public BlockDominionInputPayload decode(RegistryFriendlyByteBuf buf) {
            return new BlockDominionInputPayload(buf.readVarInt(), buf.readBlockPos());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, BlockDominionInputPayload value) {
            buf.writeVarInt(value.action());
            buf.writeBlockPos(value.targetPos());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
