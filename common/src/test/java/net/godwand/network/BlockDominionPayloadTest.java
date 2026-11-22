package net.godwand.network;

import io.netty.buffer.Unpooled;
import net.godwand.skill.BlockDominionRules;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlockDominionPayloadTest {
    private static <T> T roundTrip(StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        codec.encode(buf, value);
        return codec.decode(buf);
    }

    @Test
    void inputAndStatePayloadsRoundTripCleanly() {
        var cycle = new BlockDominionInputPayload(BlockDominionInputPayload.ACTION_CYCLE_MODE, BlockPos.ZERO);
        assertEquals(cycle, roundTrip(BlockDominionInputPayload.CODEC, cycle));

        var pickup = new BlockDominionInputPayload(BlockDominionInputPayload.ACTION_PICKUP, new BlockPos(12, 64, -34));
        assertEquals(pickup, roundTrip(BlockDominionInputPayload.CODEC, pickup));

        var state = new BlockDominionStatePayload(
                42,
                BlockDominionRules.Mode.MANUAL.ordinal(),
                BlockDominionRules.FLAG_STOWED | BlockDominionRules.FLAG_VOLLEY_CHARGING,
                14,
                new int[]{1, 10, 42, 77},
                BlockDominionRules.EVENT_PICKUP,
                12.5, 64.5, -33.5,
                77
        );
        assertEquals(state, roundTrip(BlockDominionStatePayload.CODEC, state));
    }

    @Test
    void rejectsOutOfRangeActionsModesAndBlockCounts() {
        assertThrows(IllegalArgumentException.class, () -> new BlockDominionInputPayload(2, BlockPos.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new BlockDominionStatePayload(
                1, 3, 0, 0, new int[0], 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new BlockDominionStatePayload(
                1, 1, 0, 0, new int[65], 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new BlockDominionStatePayload(
                1, 1, 0, 0, new int[0], 0, Double.NaN, 0, 0, 0));
    }
}
