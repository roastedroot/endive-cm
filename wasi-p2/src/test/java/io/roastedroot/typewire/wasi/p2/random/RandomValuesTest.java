package io.roastedroot.typewire.wasi.p2.random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.roastedroot.typewire.abi.CanonicalAbi;
import java.math.BigInteger;
import java.util.Random;
import org.junit.jupiter.api.Test;
import run.endive.runtime.TrapException;

class RandomValuesTest {

    @Test
    void bytesAreDrawnFromTheGenerator() {
        byte[] expected = new byte[16];
        new Random(7).nextBytes(expected);

        assertArrayEquals(expected, RandomValues.bytes(new Random(7), BigInteger.valueOf(16)));
    }

    @Test
    void noBytesAreAnEmptyList() {
        assertEquals(0, RandomValues.bytes(new Random(), BigInteger.ZERO).length);
    }

    /** A length the canonical ABI could not return traps before anything is allocated. */
    @Test
    void moreBytesThanAListCanHoldTrap() {
        Random random = new Random();

        assertThrows(
                TrapException.class,
                () ->
                        RandomValues.bytes(
                                random,
                                BigInteger.valueOf(CanonicalAbi.MAX_LIST_BYTE_LENGTH + 1L)));
        assertThrows(
                TrapException.class,
                () -> RandomValues.bytes(random, CanonicalAbi.toUnsignedBigInteger(-1L)));
    }

    @Test
    void aNegativeLengthIsRefused() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RandomValues.bytes(new Random(), BigInteger.valueOf(-1)));
    }

    @Test
    void aU64HoldsTheBitsOfALong() {
        assertEquals(new Random(7).nextLong(), RandomValues.u64(new Random(7)).longValue());
        assertTrue(RandomValues.u64(new Random(7)).signum() >= 0);
    }
}
