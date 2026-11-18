package io.roastedroot.typewire.abi;

/**
 * A {@code record} value read by field position.
 *
 * <p>The Canonical ABI accepts this wherever it accepts a label-to-value {@link java.util.Map}.
 * Reading a field by position skips building a map and looking each field up by its label.
 *
 * @see <a href="https://github.com/WebAssembly/component-model/blob/main/design/mvp/CanonicalABI.md#storing">Storing</a>
 */
public interface RecordValue {

    /**
     * The value of a field as the ABI carries it.
     *
     * @param index the field's position in the record type's declaration, counted from zero
     */
    Object field(int index);
}
