package io.roastedroot.typewire.wasi.p2.random.insecure;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import run.endive.runtime.TrapException;

class HostInsecureTest {

    @Test
    void bytesComeFromTheGivenGenerator() {
        byte[] expected = new byte[32];
        new Random(42).nextBytes(expected);

        assertArrayEquals(
                expected,
                new HostInsecure(new Random(42)).getInsecureRandomBytes(BigInteger.valueOf(32)));
    }

    @Test
    void aU64ComesFromTheGivenGenerator() {
        long expected = new Random(42).nextLong();

        assertEquals(expected, new HostInsecure(new Random(42)).getInsecureRandomU64().longValue());
    }

    @Test
    void theDefaultGeneratorDrawsAnyLength() {
        HostInsecure insecure = new HostInsecure();

        assertEquals(100, insecure.getInsecureRandomBytes(BigInteger.valueOf(100)).length);
        assertTrue(insecure.getInsecureRandomU64().signum() >= 0);
    }

    /** Each thread draws its own sequence, even one that never used the generator before. */
    @Test
    void guestsOnNewThreadsDrawDifferentValues() throws InterruptedException {
        HostInsecure insecure = new HostInsecure();

        assertNotEquals(drawOnNewThread(insecure), drawOnNewThread(insecure));
    }

    @Test
    void anImpossibleLengthTraps() {
        assertThrows(
                TrapException.class,
                () -> new HostInsecure().getInsecureRandomBytes(BigInteger.ONE.shiftLeft(40)));
    }

    private static BigInteger drawOnNewThread(HostInsecure insecure) throws InterruptedException {
        AtomicReference<BigInteger> drawn = new AtomicReference<>();
        Thread thread = new Thread(() -> drawn.set(insecure.getInsecureRandomU64()));
        thread.start();
        thread.join();
        return drawn.get();
    }
}
