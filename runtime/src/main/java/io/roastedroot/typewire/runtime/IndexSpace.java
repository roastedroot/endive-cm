package io.roastedroot.typewire.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One numbered index space, built up in declaration order and read back by index.
 *
 * <p>A component instantiation keeps one of these per sort, and an {@code instance} type
 * declaration keeps two of its own. They all behave identically, with definitions appended as they
 * are read and referred to afterwards by the position they landed at, so the bounds checking and
 * the diagnostics live here once rather than once per sort.
 */
final class IndexSpace<T> {

    /** The kinds of index space, named as the Component Model's diagnostics name them. */
    enum Kind {
        CORE_MODULE("core module"),
        CORE_TYPE("core type"),
        CORE_INSTANCE("core instance"),
        CORE_FUNCTION("core function"),
        CORE_MEMORY("core memory"),
        CORE_TABLE("core table"),
        CORE_GLOBAL("core global"),
        CORE_TAG("core tag"),
        FUNCTION("component function"),
        TYPE("type"),
        COMPONENT("component"),
        INSTANCE("component instance"),
        /** The little scope an {@code instance} type declaration builds up as it is read. */
        INSTANCE_DECL_TYPE("instance type"),
        INSTANCE_DECL_CORE_TYPE("instance core type");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final Kind kind;
    private final List<T> entries = new ArrayList<>();
    private boolean sealed;

    IndexSpace(Kind kind) {
        this.kind = kind;
    }

    /** Appends {@code entry}, returning the index it landed at. */
    int add(T entry) {
        if (sealed) {
            throw new IllegalStateException(kind + " index space is already built");
        }
        entries.add(entry);
        return entries.size() - 1;
    }

    T at(int index) {
        if (index < 0 || index >= entries.size()) {
            throw new LinkageException(
                    kind + " index " + index + " out of bounds (size " + entries.size() + ")");
        }
        return entries.get(index);
    }

    int size() {
        return entries.size();
    }

    List<T> all() {
        return Collections.unmodifiableList(entries);
    }

    /**
     * Closes the space to further definitions, which is what finishing an instantiation means.
     * Reading carries on working.
     */
    void seal() {
        sealed = true;
    }
}
