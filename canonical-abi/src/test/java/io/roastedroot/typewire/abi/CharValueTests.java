package io.roastedroot.typewire.abi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CharValueTests {

    @ParameterizedTest
    @ValueSource(ints = {0, 0x41, 0xD7FF, 0xE000, 0xFFFF, 0x10000, 0x1F370, 0x10FFFF})
    void acceptsEveryUnicodeScalarValue(int codePoint) {
        assertThat(CharValue.of(codePoint).codePoint()).isEqualTo(codePoint);
    }

    @ParameterizedTest
    @ValueSource(ints = {0xD800, 0xDBFF, 0xDC00, 0xDFFF})
    void rejectsSurrogates(int codePoint) {
        assertThatThrownBy(() -> CharValue.of(codePoint))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("surrogate");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0x110000, Integer.MAX_VALUE})
    void rejectsValuesOutsideTheUnicodeRange(int codePoint) {
        assertThatThrownBy(() -> CharValue.of(codePoint))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unicode range");
    }

    @Test
    void carriesScalarValuesNoJavaCharacterCanHold() {
        var cake = CharValue.of(0x1F370);

        assertThat(cake.codePoint()).isGreaterThan(Character.MAX_VALUE);
        assertThat(cake.stringValue()).isEqualTo("🍰");
        assertThat(cake.stringValue().length()).as("a surrogate pair in Java").isEqualTo(2);
    }

    @Test
    void buildsFromACharForTheValuesThatFitInOne() {
        assertThat(CharValue.of('x')).isEqualTo(CharValue.of(0x78));
    }

    @Test
    void buildsFromAStringOfExactlyOneScalarValue() {
        assertThat(CharValue.ofString("🍰")).isEqualTo(CharValue.of(0x1F370));
        assertThat(CharValue.ofString("x")).isEqualTo(CharValue.of('x'));

        assertThatThrownBy(() -> CharValue.ofString("ab"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CharValue.ofString(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void comparesByCodePoint() {
        assertThat(CharValue.of(0x41)).isEqualTo(CharValue.of(0x41));
        assertThat(CharValue.of(0x41)).hasSameHashCodeAs(CharValue.of(0x41));
        assertThat(CharValue.of(0x41)).isNotEqualTo(CharValue.of(0x42));
    }
}
