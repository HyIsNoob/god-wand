package net.godwand.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtomicFxPayloadTest {
    @Test void skipRequestCarriesOnlyTheCastIdentity() {
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            var packet=new AtomicSkipPayload(123456L);
            AtomicSkipPayload.CODEC.encode(buf,packet);
            assertEquals(8,buf.readableBytes());
            assertEquals(packet,AtomicSkipPayload.CODEC.decode(buf));
            assertEquals(0,buf.readableBytes());
        } finally { buf.release(); }
    }

    @Test void codecPreservesStableCastIdentityAndTheIndependentReleaseClock() {
        for(int phase:new int[]{AtomicFxPayload.CHARGE,AtomicFxPayload.ARMED,AtomicFxPayload.RELEASE,AtomicFxPayload.BURST,AtomicFxPayload.CANCEL}) {
            var packet=new AtomicFxPayload(17,1000,1.5,-4,9,phase,phase==AtomicFxPayload.CHARGE||phase==AtomicFxPayload.ARMED?-1:1600);
            var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
            try {
                AtomicFxPayload.CODEC.encode(buf,packet);
                assertEquals(packet,AtomicFxPayload.CODEC.decode(buf));
                assertEquals(0,buf.readableBytes());
            } finally { buf.release(); }
        }
    }
}
