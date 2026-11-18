package io.roastedroot.typewire.abi;

import static io.roastedroot.typewire.abi.TransferTestSupport.assertMemoryEquals;
import static io.roastedroot.typewire.abi.TransferTestSupport.newContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.roastedroot.typewire.types.PrimValType;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import run.endive.runtime.TrapException;

class CanonicalAbiTransferStringTests {

    private static final List<String> CORPUS =
            List.of(
                    "",
                    "hello",
                    "café", // Latin-1 representable, not ASCII
                    "日本語", // BMP, not Latin-1
                    "😀", // astral, a surrogate pair in UTF-16
                    "mixed café 日本 😀 tail");

    static List<StringEncoding> encodings() {
        return List.of(StringEncoding.UTF8, StringEncoding.UTF16, StringEncoding.LATIN1_UTF16);
    }

    static List<org.junit.jupiter.params.provider.Arguments> encodingPairs() {
        var pairs = new java.util.ArrayList<org.junit.jupiter.params.provider.Arguments>();
        for (StringEncoding src : encodings()) {
            for (StringEncoding dst : encodings()) {
                pairs.add(org.junit.jupiter.params.provider.Arguments.of(src, dst));
            }
        }
        return pairs;
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("encodingPairs")
    void transfersEveryEncodingPairIdenticallyToLiftLower(
            StringEncoding srcEncoding, StringEncoding dstEncoding) {
        for (String s : CORPUS) {
            var types = new TransferTestSupport.Types();
            var src = newContext(types, srcEncoding);
            var dst = newContext(types, dstEncoding);
            var reference = newContext(types, dstEncoding);

            new AbiHelper(types).store(src, s, PrimValType.STRING, 0);
            new AbiHelper(types).transfer(src, dst, 0, 0, PrimValType.STRING);
            new AbiHelper(types)
                    .store(
                            reference,
                            new AbiHelper(types).load(src, 0, PrimValType.STRING),
                            PrimValType.STRING,
                            0);

            assertThat(new AbiHelper(types).load(dst, 0, PrimValType.STRING))
                    .as("%s -> %s for \"%s\"", srcEncoding, dstEncoding, s)
                    .isEqualTo(s);
            assertMemoryEquals(dst.memory(), reference.memory());
        }
    }

    @Test
    void narrowsATaggedUtf16SourceThatFitsLatin1() {
        // A guest may hand over a latin1+utf16 string tagged as UTF-16 even when its content
        // fits Latin-1, so store_string would have chosen it and the transfer must too.
        var types = new TransferTestSupport.Types();
        var src = newContext(types, StringEncoding.LATIN1_UTF16);
        var dst = newContext(types, StringEncoding.LATIN1_UTF16);
        byte[] utf16 = "café".getBytes(StandardCharsets.UTF_16LE);
        src.memory().write(200, utf16);
        src.memory().writeI32(0, 200);
        src.memory().writeI32(4, (utf16.length / 2) | (1 << 31));

        new AbiHelper(types).transfer(src, dst, 0, 0, PrimValType.STRING);

        long taggedCodeUnits = dst.memory().readU32(4);
        assertThat(taggedCodeUnits & (1L << 31)).as("re-tagged as UTF-16").isZero();
        assertThat(taggedCodeUnits).isEqualTo(4);
        assertThat(new AbiHelper(types).load(dst, 0, PrimValType.STRING)).isEqualTo("café");
    }

    @Test
    void keepsMatchingUtf8BytesWithoutTranscoding() {
        var types = new TransferTestSupport.Types();
        var src = newContext(types, StringEncoding.UTF8);
        var dst = newContext(types, StringEncoding.UTF8);
        byte[] utf8 = "日本語".getBytes(StandardCharsets.UTF_8);
        src.memory().write(200, utf8);
        src.memory().writeI32(0, 200);
        src.memory().writeI32(4, utf8.length);

        new AbiHelper(types).transfer(src, dst, 0, 0, PrimValType.STRING);

        int dstBegin = dst.memory().readInt(0);
        assertThat(dst.memory().readU32(4)).isEqualTo(utf8.length);
        assertThat(dst.memory().readBytes(dstBegin, utf8.length)).isEqualTo(utf8);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "FF", // not a valid lead byte
                "80", // stray continuation byte
                "C080", // overlong two-byte encoding of NUL
                "C1BF", // overlong two-byte encoding
                "E08080", // overlong three-byte encoding
                "EDA080", // surrogate U+D800
                "EDBFBF", // surrogate U+DFFF
                "F0808080", // overlong four-byte encoding
                "F4908080", // above U+10FFFF
                "F5808080", // lead byte out of range
                "C2", // truncated two-byte sequence
                "E282", // truncated three-byte sequence
                "F09F98", // truncated four-byte sequence
                "E228A1", // bad continuation byte
                "41C2" // truncated sequence after valid ASCII
            })
    void utf8ValidatorRejectsExactlyWhatTheStrictDecoderRejects(String hex) {
        byte[] bytes = fromHex(hex);
        assertThat(catchThrowable(() -> CanonicalAbi.validateUtf8(bytes)))
                .as("validator verdict for %s", hex)
                .isNotNull();
        assertThat(catchThrowable(() -> decode(bytes, StandardCharsets.UTF_8)))
                .as("strict decoder verdict for %s", hex)
                .isNotNull();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "", // empty
                "41", // "A"
                "C3A9", // "é"
                "E697A5", // "日"
                "F09F9880", // "😀"
                "41C3A9E697A5F09F9880", // all of the above
                "F48FBFBF", // U+10FFFF, the highest scalar value
                "EE8080" // U+E000, just above the surrogate range
            })
    void utf8ValidatorAcceptsExactlyWhatTheStrictDecoderAccepts(String hex) {
        byte[] bytes = fromHex(hex);
        CanonicalAbi.validateUtf8(bytes);
        decode(bytes, StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "00D8", // lone high surrogate
                "00DC", // lone low surrogate
                "00D84100", // high surrogate followed by a non-surrogate
                "410000D8", // trailing lone high surrogate
                "00DC00D8" // low surrogate before high surrogate
            })
    void utf16ValidatorRejectsExactlyWhatTheStrictDecoderRejects(String hex) {
        byte[] bytes = fromHex(hex);
        assertThat(catchThrowable(() -> CanonicalAbi.validateUtf16Le(bytes)))
                .as("validator verdict for %s", hex)
                .isNotNull();
        assertThat(catchThrowable(() -> decode(bytes, StandardCharsets.UTF_16LE)))
                .as("strict decoder verdict for %s", hex)
                .isNotNull();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "", // empty
                "4100", // "A"
                "E900", // "é"
                "E565", // "日"
                "3DD800DE", // "😀"
                "41003DD800DE4200" // surrogate pair between plain code units
            })
    void utf16ValidatorAcceptsExactlyWhatTheStrictDecoderAccepts(String hex) {
        byte[] bytes = fromHex(hex);
        CanonicalAbi.validateUtf16Le(bytes);
        decode(bytes, StandardCharsets.UTF_16LE);
    }

    @Test
    void trapsOnLoneSurrogateInAUtf16Source() {
        var types = new TransferTestSupport.Types();
        var src = newContext(types, StringEncoding.UTF16);
        var dst = newContext(types, StringEncoding.UTF8);
        src.memory().writeShort(200, (short) 0xD800);
        src.memory().writeI32(0, 200);
        src.memory().writeI32(4, 1);

        assertThatThrownBy(() -> new AbiHelper(types).transfer(src, dst, 0, 0, PrimValType.STRING))
                .isInstanceOf(TrapException.class);
        assertThatThrownBy(() -> new AbiHelper(types).load(src, 0, PrimValType.STRING))
                .isInstanceOf(TrapException.class);
    }

    @Test
    void trapsOnStringByteLengthAboveTheMaximum() {
        var types = new TransferTestSupport.Types();
        var src = newContext(types, StringEncoding.UTF8);
        var dst = newContext(types, StringEncoding.UTF8);
        src.memory().writeI32(0, 0);
        src.memory().writeI32(4, 1 << 28);

        assertThatThrownBy(() -> new AbiHelper(types).transfer(src, dst, 0, 0, PrimValType.STRING))
                .isInstanceOf(TrapException.class);
    }

    private static void decode(byte[] bytes, Charset charset) {
        CanonicalAbi.decodeStrictToChars(bytes, charset);
    }

    private static byte[] fromHex(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }
}
