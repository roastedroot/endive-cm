package io.roastedroot.typewire.wasi.p2.random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.roastedroot.typewire.abi.ByteList;
import io.roastedroot.typewire.abi.CanonicalAbi;
import io.roastedroot.typewire.runtime.ComponentFunction;
import io.roastedroot.typewire.runtime.ComponentStore;
import io.roastedroot.typewire.runtime.Tuple2;
import io.roastedroot.typewire.types.WasmComponent;
import io.roastedroot.typewire.wasi.p2.Guests;
import io.roastedroot.typewire.wasi.p2.WasiPreview2;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A guest handing back what {@code wasi:random} gave it, end to end through the generated world,
 * against generators seeded so that the values it should see are known.
 */
class RandomGuestTest {

    private static final long SEED = 42L;

    private static WasmComponent component;

    @BeforeAll
    static void buildComponent(@TempDir Path temp) {
        component = Guests.build("random", temp);
    }

    @Test
    void aGuestReceivesRandomBytes() {
        Random expected = new Random(SEED);
        byte[] first = new byte[16];
        expected.nextBytes(first);
        byte[] second = new byte[3];
        expected.nextBytes(second);

        try (var wasi = WasiPreview2.builder().withRandom(new Random(SEED)).build()) {
            ComponentFunction randomBytes = export(wasi, "random-bytes");

            assertArrayEquals(first, bytes(randomBytes.apply(BigInteger.valueOf(16))));
            assertArrayEquals(second, bytes(randomBytes.apply(BigInteger.valueOf(3))));
            assertArrayEquals(new byte[0], bytes(randomBytes.apply(BigInteger.ZERO)));
        }
    }

    @Test
    void aGuestReceivesARandomU64() {
        BigInteger expected = CanonicalAbi.toUnsignedBigInteger(new Random(SEED).nextLong());

        try (var wasi = WasiPreview2.builder().withRandom(new Random(SEED)).build()) {
            assertEquals(expected, export(wasi, "random-u64").apply()[0]);
        }
    }

    @Test
    void aGuestReceivesInsecureRandomBytes() {
        byte[] expected = new byte[16];
        new Random(SEED).nextBytes(expected);

        try (var wasi = WasiPreview2.builder().withInsecureRandom(new Random(SEED)).build()) {
            assertArrayEquals(
                    expected, bytes(export(wasi, "insecure-bytes").apply(BigInteger.valueOf(16))));
        }
    }

    /** A u64 above the range of a {@code long} crosses intact. */
    @Test
    void aGuestReceivesAnInsecureRandomU64() {
        BigInteger expected = CanonicalAbi.toUnsignedBigInteger(new Random(SEED).nextLong());

        try (var wasi = WasiPreview2.builder().withInsecureRandom(new Random(SEED)).build()) {
            assertEquals(expected, export(wasi, "insecure-u64").apply()[0]);
        }
    }

    @Test
    void aGuestReceivesTheSameInsecureSeedEveryTime() {
        Tuple2<BigInteger, BigInteger> expected =
                Tuple2.of(
                        BigInteger.valueOf(7),
                        BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE));

        try (var wasi = WasiPreview2.builder().withInsecureSeed(7L, -1L).build()) {
            ComponentFunction insecureSeed = export(wasi, "insecure-seed");

            assertEquals(expected, seed(insecureSeed.apply()));
            assertEquals(expected, seed(insecureSeed.apply()));
        }
    }

    private static ComponentFunction export(WasiPreview2 wasi, String name) {
        return wasi.instantiate(new ComponentStore(), component).instance().export(name);
    }

    private static byte[] bytes(Object[] result) {
        return ByteList.toBytes(result[0]);
    }

    private static Tuple2<BigInteger, BigInteger> seed(Object[] result) {
        return Tuple2.fromComponent(result[0], BigInteger.class, BigInteger.class);
    }
}
