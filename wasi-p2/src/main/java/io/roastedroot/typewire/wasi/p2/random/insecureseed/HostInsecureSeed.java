package io.roastedroot.typewire.wasi.p2.random.insecureseed;

import io.roastedroot.typewire.abi.CanonicalAbi;
import io.roastedroot.typewire.runtime.Tuple2;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.random.insecureseed.Host;
import java.math.BigInteger;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The host side of {@code wasi:random/insecure-seed}, which returns the same seed every time.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/random/wit/insecure-seed.wit">wasi:random/insecure-seed</a>
 */
public class HostInsecureSeed implements Host {

    private final Tuple2<BigInteger, BigInteger> seed;

    /** A seed drawn once from {@link ThreadLocalRandom}. */
    public HostInsecureSeed() {
        this(ThreadLocalRandom.current().nextLong(), ThreadLocalRandom.current().nextLong());
    }

    /** The seed whose halves hold the bits of {@code first} and {@code second}. */
    public HostInsecureSeed(long first, long second) {
        this.seed =
                Tuple2.of(
                        CanonicalAbi.toUnsignedBigInteger(first),
                        CanonicalAbi.toUnsignedBigInteger(second));
    }

    @Override
    public Tuple2<BigInteger, BigInteger> insecureSeed() {
        return seed;
    }
}
