package io.roastedroot.typewire.wasi.p2.random.insecureseed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.roastedroot.typewire.runtime.Tuple2;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class HostInsecureSeedTest {

    @Test
    void aGivenSeedIsReturned() {
        assertEquals(
                Tuple2.of(BigInteger.ONE, BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)),
                new HostInsecureSeed(1L, -1L).insecureSeed());
    }

    @Test
    void theSameSeedIsReturnedEveryTime() {
        HostInsecureSeed seed = new HostInsecureSeed();

        assertSame(seed.insecureSeed(), seed.insecureSeed());
    }
}
