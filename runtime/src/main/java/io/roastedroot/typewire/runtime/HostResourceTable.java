package io.roastedroot.typewire.runtime;

import io.roastedroot.typewire.abi.ResourceValue;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Holds the Java objects a host-implemented resource type stands for, indexed by the representation
 * its handles carry.
 *
 * <p>A handle crossing the Canonical ABI carries an integer rather than an object, so something has
 * to remember which object a representation names. That is what this does, for one resource type of
 * one instantiation.
 *
 * @see <a href="https://github.com/WebAssembly/component-model/blob/706074c96bc14cfc58469e1bdc452bb4d91921c7/design/mvp/CanonicalABI.md#resource-tables">CanonicalABI.md, resource tables</a>
 */
public final class HostResourceTable<T> {

    private final Map<Integer, T> values = new ConcurrentHashMap<>();

    /** Representations start at one, so that a zeroed handle names nothing. */
    private final AtomicInteger next = new AtomicInteger(1);

    /** Records {@code value} and hands back the representation naming it. */
    public int add(T value) {
        Objects.requireNonNull(value, "value");
        int rep = next.getAndIncrement();
        values.put(rep, value);
        return rep;
    }

    /**
     * The value {@code handle} names.
     *
     * @throws LinkageException if the handle names nothing, which means it was dropped or never
     *     came from this table
     */
    public T get(ResourceValue handle) {
        Objects.requireNonNull(handle, "handle");
        T value = values.get(handle.rep());
        if (value == null) {
            throw new LinkageException("resource " + handle.rep() + " is not in this table");
        }
        return value;
    }

    /**
     * Forgets the value an owned {@code handle} names and hands it back without dropping it. This
     * is what receiving ownership of a resource does, since the value now belongs to whoever
     * received it rather than to the handle.
     *
     * @throws LinkageException if the handle names nothing, which means it was dropped or never
     *     came from this table
     * @see <a href="https://github.com/WebAssembly/component-model/blob/706074c96bc14cfc58469e1bdc452bb4d91921c7/design/mvp/CanonicalABI.md#handle-types">CanonicalABI.md, lifting an owned handle</a>
     */
    public T take(ResourceValue handle) {
        Objects.requireNonNull(handle, "handle");
        T value = values.remove(handle.rep());
        if (value == null) {
            throw new LinkageException("resource " + handle.rep() + " is not in this table");
        }
        return value;
    }

    /** Forgets the value at {@code rep}, which is what a destructor does. */
    public void drop(int rep) {
        values.remove(rep);
    }

    /**
     * Forgets the value at {@code rep}, handing it to {@code onDrop} first if it is still here, so
     * that an embedder can release whatever the resource was holding.
     */
    public void drop(int rep, Consumer<? super T> onDrop) {
        T value = values.remove(rep);
        if (value != null) {
            onDrop.accept(value);
        }
    }

    /** How many values this holds, for an embedder checking that drops are reaching it. */
    public int size() {
        return values.size();
    }
}
