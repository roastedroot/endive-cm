package io.roastedroot.typewire.abi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.roastedroot.typewire.types.DefValType;
import io.roastedroot.typewire.types.PointerType;
import io.roastedroot.typewire.types.PrimValType;
import io.roastedroot.typewire.types.TypeResolver;
import org.junit.jupiter.api.Test;
import run.endive.runtime.ByteArrayMemory;
import run.endive.runtime.Memory;
import run.endive.runtime.TrapException;
import run.endive.wasm.types.MemoryLimits;

class CanonicalAbiStringTests {

    private static final TypeResolver STUB_RESOLVER = index -> null;

    private static final AbiHelper ABI = new AbiHelper(STUB_RESOLVER);

    private static LiftLowerContext newContext(StringEncoding encoding) {
        Memory memory = new ByteArrayMemory(new MemoryLimits(4));
        int[] bumpPtr = {1000};
        Realloc realloc =
                (oldPtr, oldSize, align, newSize) -> {
                    int ptr = DefValType.alignTo(bumpPtr[0], align);
                    bumpPtr[0] = ptr + newSize;
                    return ptr;
                };
        return LiftLowerContext.builder()
                .withMemory(memory)
                .withPtrType(PointerType.I32)
                .withStringEncoding(encoding)
                .withRealloc(realloc)
                .build();
    }

    @Test
    void roundTripsUtf8AsciiAndNonAscii() {
        var ctx = newContext(StringEncoding.UTF8);
        ABI.store(ctx, "hello", PrimValType.STRING, 0);
        assertThat(ABI.load(ctx, 0, PrimValType.STRING)).isEqualTo("hello");

        // includes a codepoint outside the BMP (requires a Java surrogate pair)
        String s = "café 😀";
        ABI.store(ctx, s, PrimValType.STRING, 8);
        assertThat(ABI.load(ctx, 8, PrimValType.STRING)).isEqualTo(s);
    }

    @Test
    void roundTripsUtf16() {
        var ctx = newContext(StringEncoding.UTF16);
        String s = "café 😀";
        ABI.store(ctx, s, PrimValType.STRING, 0);
        assertThat(ABI.load(ctx, 0, PrimValType.STRING)).isEqualTo(s);
    }

    @Test
    void latin1Utf16StoresLatin1FittingContentUntagged() {
        var ctx = newContext(StringEncoding.LATIN1_UTF16);
        String s = "cafeé"; // all codepoints < 256
        ABI.store(ctx, s, PrimValType.STRING, 0);

        // tagged_code_units high bit must be clear (stored as plain latin1)
        long taggedCodeUnits = ctx.memory().readU32(4);
        long tag = 1L << 31;
        assertThat(taggedCodeUnits & tag).isZero();

        assertThat(ABI.load(ctx, 0, PrimValType.STRING)).isEqualTo(s);
    }

    @Test
    void latin1Utf16StoresNonLatin1ContentTaggedAsUtf16() {
        var ctx = newContext(StringEncoding.LATIN1_UTF16);
        String s = "hello 😀"; // contains a codepoint outside latin1
        ABI.store(ctx, s, PrimValType.STRING, 0);

        long taggedCodeUnits = ctx.memory().readU32(4);
        long tag = 1L << 31;
        assertThat(taggedCodeUnits & tag).isNotZero();

        assertThat(ABI.load(ctx, 0, PrimValType.STRING)).isEqualTo(s);
    }

    @Test
    void loadTrapsOnInvalidUtf8() {
        var ctx = newContext(StringEncoding.UTF8);
        ctx.memory().writeI32(0, 100); // begin
        ctx.memory().writeI32(4, 1); // byte length 1
        ctx.memory().writeByte(100, (byte) 0xFF); // not a valid UTF-8 lead byte
        assertThatThrownBy(() -> ABI.load(ctx, 0, PrimValType.STRING))
                .isInstanceOf(TrapException.class);
    }

    @Test
    void loadTrapsOnByteLengthExceedingMaximum() {
        var ctx = newContext(StringEncoding.UTF8);
        ctx.memory().writeI32(0, 0); // begin
        ctx.memory().writeI32(4, (1 << 28)); // one past MAX_STRING_BYTE_LENGTH
        assertThatThrownBy(() -> ABI.load(ctx, 0, PrimValType.STRING))
                .isInstanceOf(TrapException.class);
    }

    @Test
    void emptyStringRoundTrips() {
        var ctx = newContext(StringEncoding.UTF8);
        ABI.store(ctx, "", PrimValType.STRING, 0);
        assertThat(ABI.load(ctx, 0, PrimValType.STRING)).isEqualTo("");
    }
}
