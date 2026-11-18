package io.roastedroot.typewire.wasi.p2.random.insecure;

import io.roastedroot.typewire.wasi.p2.bindings.wasi.random.insecure.Host;
import io.roastedroot.typewire.wasi.p2.random.RandomValues;
import java.math.BigInteger;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The host side of {@code wasi:random/insecure}, drawing from any {@link Random}.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/random/wit/insecure.wit">wasi:random/insecure</a>
 */
public class HostInsecure implements Host {

    private final Random random;

    /** Draws from the {@link ThreadLocalRandom} of the thread running the guest. */
    public HostInsecure() {
        this.random = null;
    }

    public HostInsecure(Random random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    /** Traps when the canonical ABI could not return {@code len} bytes. */
    @Override
    public byte[] getInsecureRandomBytes(BigInteger len) {
        return RandomValues.bytes(random(), len);
    }

    @Override
    public BigInteger getInsecureRandomU64() {
        return RandomValues.u64(random());
    }

    /** A {@link ThreadLocalRandom} is only seeded for the thread that asks for it. */
    private Random random() {
        return random == null ? ThreadLocalRandom.current() : random;
    }
}
