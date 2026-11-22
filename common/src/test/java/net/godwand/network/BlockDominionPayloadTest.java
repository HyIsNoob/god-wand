package net.godwand.network;

import io.netty.buffer.Unpooled;
import net.godwand.skill.BlockDominionBrain;
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

        var toggle = new BlockDominionInputPayload(BlockDominionInputPayload.ACTION_TOGGLE_FLIGHT, BlockPos.ZERO);
        assertEquals(toggle, roundTrip(BlockDominionInputPayload.CODEC, toggle));

        var flight = BlockDominionInputPayload.flightControl(1, -1, 1, true);
        var flightRt = roundTrip(BlockDominionInputPayload.CODEC, flight);
        assertEquals(flight, flightRt);
        assertEquals(1, flightRt.flightForward());
        assertEquals(-1, flightRt.flightStrafe());
        assertEquals(1, flightRt.flightVertical());
        assertTrue(flightRt.flightBoost());

        var flightSlow = BlockDominionInputPayload.flightControl(0, 1, -1, false);
        assertEquals(0, flightSlow.flightForward());
        assertEquals(1, flightSlow.flightStrafe());
        assertEquals(-1, flightSlow.flightVertical());
        assertFalse(flightSlow.flightBoost());

        var state = new BlockDominionStatePayload(
                42,
                BlockDominionRules.Mode.MANUAL.ordinal(),
                BlockDominionRules.FLAG_STOWED | BlockDominionRules.FLAG_VOLLEY_CHARGING,
                14,
                BlockDominionBrain.FormationKind.SHIELD_MULTI.ordinal(),
                1f, 0f, 0f,
                0f, 0f, -1f,
                new int[]{1, 10, 42, 77},
                BlockDominionRules.EVENT_PICKUP,
                12.5, 64.5, -33.5,
                77
        );
        assertEquals(state, roundTrip(BlockDominionStatePayload.CODEC, state));
    }

    @Test
    void rejectsOutOfRangeActionsModesFormationsAndBlockCounts() {
        assertThrows(IllegalArgumentException.class, () -> new BlockDominionInputPayload(4, BlockPos.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new BlockDominionStatePayload(
                1, 3, 0, 0, 0, 0, 0, 1, 0, 0, 0, new int[0], 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new BlockDominionStatePayload(
                1, 1, 0, 0, 99, 0, 0, 1, 0, 0, 0, new int[0], 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new BlockDominionStatePayload(
                1, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, new int[BlockDominionRules.MAX_CAPACITY + 1], 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new BlockDominionStatePayload(
                1, 1, 0, 0, 0, Float.NaN, 0, 1, 0, 0, 0, new int[0], 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new BlockDominionStatePayload(
                1, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, new int[0], 0, Double.NaN, 0, 0, 0));
    }
}
