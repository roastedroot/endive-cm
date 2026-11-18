package io.roastedroot.typewire.abi;

/**
 * A component {@code char}: a single Unicode scalar value.
 *
 * <p>This wrapper exists because no Java character type can hold the full range of possible values.
 * Wrapping the code point instead makes the invalid values unrepresentable: {@link #of}
 * applies the same range and surrogate checks the Canonical ABI applies when lifting.
 */
public final class CharValue {

    private final int codePoint;

    private CharValue(int codePoint) {
        this.codePoint = codePoint;
    }

    /**
     * Wraps a Unicode scalar value.
     *
     * @param codePoint the scalar value, which must not be a surrogate or exceed {@code
     *     U+10FFFF}
     * @return the wrapped scalar value
     * @throws IllegalArgumentException if {@code codePoint} is not a Unicode scalar value
     */
    public static CharValue of(int codePoint) {
        if (codePoint < 0 || codePoint > MAX_CODE_POINT) {
            throw new IllegalArgumentException(
                    "code point U+"
                            + Integer.toHexString(codePoint).toUpperCase()
                            + " is outside the Unicode range");
        }
        if (codePoint >= MIN_SURROGATE && codePoint <= MAX_SURROGATE) {
            throw new IllegalArgumentException(
                    "code point U+"
                            + Integer.toHexString(codePoint).toUpperCase()
                            + " is a surrogate, which is not a scalar value");
        }
        return new CharValue(codePoint);
    }

    /** Wraps a {@code char}, for the scalar values that happen to fit in one. */
    public static CharValue of(char c) {
        return of((int) c);
    }

    /**
     * Wraps the single scalar value {@code s} consists of.
     *
     * @param s a string of exactly one code point, so that {@code "🍰"} is accepted while
     *     {@code "ab"} and {@code ""} are not
     * @return the wrapped scalar value
     * @throws IllegalArgumentException if {@code s} is not exactly one scalar value
     */
    public static CharValue ofString(String s) {
        if (s.codePointCount(0, s.length()) != 1) {
            throw new IllegalArgumentException(
                    "expected exactly one code point but got \"" + s + "\"");
        }
        return of(s.codePointAt(0));
    }

    /** The Unicode scalar value, in the range {@code 0..0x10FFFF} excluding surrogates. */
    public int codePoint() {
        return codePoint;
    }

    /**
     * This scalar value as a string — one Java {@code char} below {@code U+FFFF}, a surrogate
     * pair above it.
     */
    public String stringValue() {
        return new String(Character.toChars(codePoint));
    }

    private static final int MIN_SURROGATE = 0xD800;
    private static final int MAX_SURROGATE = 0xDFFF;
    private static final int MAX_CODE_POINT = 0x10FFFF;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CharValue)) {
            return false;
        }
        return codePoint == ((CharValue) o).codePoint;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(codePoint);
    }

    @Override
    public String toString() {
        return "CharValue{U+" + Integer.toHexString(codePoint).toUpperCase() + "}";
    }
}
