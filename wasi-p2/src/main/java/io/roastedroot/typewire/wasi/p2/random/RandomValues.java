package io.roastedroot.typewire.wasi.p2.random;

import io.roastedroot.typewire.abi.CanonicalAbi;
import java.math.BigInteger;
import java.util.Random;
import run.endive.runtime.TrapException;

/** The values every {@code wasi:random} interface hands out, drawn from a {@link Random}. */
public final class RandomValues {

    private static final BigInteger MAX_LENGTH =
            BigInteger.valueOf(CanonicalAbi.MAX_LIST_BYTE_LENGTH);

    private RandomValues() {}

    /**
     * Draws {@code len} bytes. Traps before allocating anything when the canonical ABI could not
     * return that many.
     *
     * @throws IllegalArgumentException when {@code len} is negative
     */
    public static byte[] bytes(Random random, BigInteger len) {
        if (len.signum() < 0) {
            throw new IllegalArgumentException("negative length " + len);
        }
        if (len.compareTo(MAX_LENGTH) > 0) {
            throw new TrapException(len + " random bytes exceed the list limit of " + MAX_LENGTH);
        }
        byte[] bytes = new byte[len.intValueExact()];
        random.nextBytes(bytes);
        return bytes;
    }

    /** Draws a {@code u64}. */
    public static BigInteger u64(Random random) {
        return CanonicalAbi.toUnsignedBigInteger(random.nextLong());
    }
}
