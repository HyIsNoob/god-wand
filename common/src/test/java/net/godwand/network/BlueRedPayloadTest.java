package net.godwand.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlueRedPayloadTest {
    private static <T> T roundTrip(net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try { codec.encode(buf, value); T back = codec.decode(buf); assertEquals(0, buf.readableBytes()); return back; }
        finally { buf.release(); }
    }

    @Test void roundTrip() {
        for (int kind = BlueRedPayload.BLUE; kind <= BlueRedPayload.PURPLE; kind++) {
            var p = new BlueRedPayload(kind, 12, kind == BlueRedPayload.PURPLE ? 34 : -1, 5000L + kind,
                    1.5, 64, -3.25, 0, 0.6, 0.8, kind == BlueRedPayload.BLUE ? 2 : 0);
            assertEquals(p, roundTrip(BlueRedPayload.CODEC, p));
        }
        assertEquals(new BlueInputPayload(true), roundTrip(BlueInputPayload.CODEC, new BlueInputPayload(true)));
        assertEquals(new BlueInputPayload(false), roundTrip(BlueInputPayload.CODEC, new BlueInputPayload(false)));
    }

    @Test void rejectsBadKind() {
        assertThrows(IllegalArgumentException.class, () -> new BlueRedPayload(8, 1, -1, 0, 0, 0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new BlueRedPayload(-1, 1, -1, 0, 0, 0, 0, 0, 0, 0, 0));
    }

    @Test void rejectsNaN() {
        assertThrows(IllegalArgumentException.class, () -> new BlueRedPayload(0, 1, -1, 0, Double.NaN, 0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new BlueRedPayload(5, 1, -1, 0, 0, 0, 0, 0, Double.POSITIVE_INFINITY, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new BlueRedPayload(0, 1, -1, 0, 4e7, 0, 0, 0, 0, 0, 0));
    }

    @Test void rejectsBadState() {
        assertThrows(IllegalArgumentException.class, () -> new BlueRedPayload(0, 1, -1, 0, 0, 0, 0, 0, 0, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> new BlueRedPayload(0, 1, -1, 0, 0, 0, 0, 0, 0, 0, -1));
    }
}
