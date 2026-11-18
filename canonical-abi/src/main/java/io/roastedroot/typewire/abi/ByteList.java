package io.roastedroot.typewire.abi;

import java.util.AbstractList;
import java.util.List;
import java.util.RandomAccess;

/**
 * A lifted {@code list<u8>}, backed by the bytes copied out of linear memory in one read.
 *
 * <p>It is still a {@link List}, typed like every other lifted list, and its elements are the
 * {@link Short} values a {@code u8} lifts to, so code reading lifted values generically keeps
 * working. Code that wants the bytes takes them with {@link #toBytes} instead of reading element
 * by element.
 */
public final class ByteList extends AbstractList<Object> implements RandomAccess {

    private final byte[] bytes;

    private ByteList(byte[] bytes) {
        this.bytes = bytes;
    }

    /** Wraps {@code bytes} without copying them. */
    public static ByteList of(byte[] bytes) {
        return new ByteList(bytes);
    }

    /**
     * The bytes a {@code list<u8>} value holds.
     *
     * @param value a {@link ByteList}, a {@code byte[]}, or a {@link List} of numbers
     * @return the backing array for a {@link ByteList} or {@code byte[]}, otherwise a new array
     */
    public static byte[] toBytes(Object value) {
        if (value instanceof ByteList) {
            return ((ByteList) value).bytes;
        }
        if (value instanceof byte[]) {
            return (byte[]) value;
        }
        List<?> list = (List<?>) value;
        byte[] bytes = new byte[list.size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = ((Number) list.get(i)).byteValue();
        }
        return bytes;
    }

    @Override
    public Object get(int index) {
        return (short) (bytes[index] & 0xFF);
    }

    @Override
    public int size() {
        return bytes.length;
    }
}
