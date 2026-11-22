package net.godwand.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client-to-server left-click input for Block Dominion (mode 9): cycle mode or lift targeted block. */
public record BlockDominionInputPayload(int action, BlockPos targetPos) implements CustomPacketPayload {
    public static final int ACTION_CYCLE_MODE = 0;
    public static final int ACTION_PICKUP = 1;

    public BlockDominionInputPayload {
        if (action < ACTION_CYCLE_MODE || action > ACTION_PICKUP) {
            throw new IllegalArgumentException("Invalid Block Dominion input action: " + action);
        }
        if (targetPos == null) targetPos = BlockPos.ZERO;
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
