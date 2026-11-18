package io.roastedroot.typewire.wasi.p2.random.random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import run.endive.runtime.TrapException;

class HostRandomTest {

    private static final BigInteger U64_LIMIT = BigInteger.ONE.shiftLeft(64);

    @Test
    void bytesComeFromTheGivenGenerator() {
        byte[] expected = new byte[32];
        new Random(42).nextBytes(expected);

        assertArrayEquals(
                expected, new HostRandom(new Random(42)).getRandomBytes(BigInteger.valueOf(32)));
    }

    @Test
    void aU64ComesFromTheGivenGenerator() {
        long expected = new Random(42).nextLong();

        assertEquals(expected, new HostRandom(new Random(42)).getRandomU64().longValue());
    }

    @Test
    void theDefaultGeneratorReturnsFreshData() {
        HostRandom random = new HostRandom();

        byte[] first = random.getRandomBytes(BigInteger.valueOf(32));
        byte[] second = random.getRandomBytes(BigInteger.valueOf(32));

        assertEquals(32, first.length);
        assertFalse(Arrays.equals(first, second));
        assertTrue(random.getRandomU64().signum() >= 0);
        assertTrue(random.getRandomU64().compareTo(U64_LIMIT) < 0);
    }

    /**
     * Built on one thread and called from fresh ones, the default still draws from each caller's
     * own seeded generator rather than a predictable unseeded one.
     */
    @Test
    void theDefaultGeneratorIsSeededForTheGuestThread() throws InterruptedException {
        HostRandom random = new HostRandom();

        assertNotEquals(onFreshThread(random), onFreshThread(random));
    }

    @Test
    void anImpossibleLengthTraps() {
        assertThrows(
                TrapException.class,
                () -> new HostRandom().getRandomBytes(U64_LIMIT.subtract(BigInteger.ONE)));
    }

    @Test
    void aMissingGeneratorIsRefused() {
        assertThrows(NullPointerException.class, () -> new HostRandom(null));
    }

    private static BigInteger onFreshThread(HostRandom random) throws InterruptedException {
        AtomicReference<BigInteger> value = new AtomicReference<>();
        Thread thread = new Thread(() -> value.set(random.getRandomU64()));
        thread.start();
        thread.join();
        return value.get();
    }
}
