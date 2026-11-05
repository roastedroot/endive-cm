package run.endive.cm.bindgen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import run.endive.cm.types.Type;

/**
 * A type index space, together with the WIT names given to the types in it.
 *
 * <p>A value type names anything but a primitive by index, so resolving one needs the space
 * against which it was written. Names matter because a Java type has to be called something, and
 * only the export declaring a type says what its name is.
 *
 * <p>A type one space uses from another keeps the numbering of the space declaring it, so anything
 * looking inside such a type has to resolve it against {@link #declaringScope} rather than here.
 */
final class WitScope {

    private final List<Type> types = new ArrayList<>();
    private final Map<Integer, String> names = new HashMap<>();
    private final Map<Integer, Origin> origins = new HashMap<>();
    private String owner;
    private String javaPackage;

    /**
     * Names the Java type the types in this space are generated inside, so that a reference from
     * outside it is written whole. A world's own space has no owner.
     */
    void withOwner(String owner) {
        this.owner = owner;
    }

    /** The Java type enclosing what this space declares, or {@code null} at the top of a world. */
    String owner() {
        return owner;
    }

    /** Names the Java package that holds these generated types. */
    void withJavaPackage(String javaPackage) {
        this.javaPackage = javaPackage;
    }

    /** The package these types live in, so a reference from elsewhere can be written whole. */
    String javaPackage() {
        return javaPackage;
    }

    /** Appends a type, at the next index. */
    int add(Type type) {
        types.add(type);
        return types.size() - 1;
    }

    /** Appends a type under the WIT name that declares it. */
    int add(Type type, String name) {
        int index = add(type);
        names.put(index, name);
        return index;
    }

    /**
     * Appends a type that {@code from} holds at {@code index}, which remains declared by whichever
     * space declared it in the first place.
     */
    int alias(WitScope from, int index) {
        Origin origin = from.originOf(index);
        int added = add(origin.scope.at(origin.index));
        origins.put(added, origin);
        return added;
    }

    /** Appends a type that {@code from} holds at {@code index}, under a WIT name of this space. */
    void alias(WitScope from, int index, String name) {
        names.put(alias(from, index), name);
    }

    int size() {
        return types.size();
    }

    /** The type at {@code index}, or {@code null} for a resource, which has no structure here. */
    Type at(int index) {
        if (index < 0 || index >= types.size()) {
            throw new BindgenException("type " + index + " was never declared");
        }
        return types.get(index);
    }

    /** The WIT name of the type at {@code index}, or {@code null} if it was never named. */
    String nameAt(int index) {
        return names.get(index);
    }

    /** The index of the type this space exports as {@code name}, or -1 when it exports none. */
    int indexOf(String name) {
        for (Map.Entry<Integer, String> entry : names.entrySet()) {
            if (entry.getValue().equals(name)) {
                return entry.getKey();
            }
        }
        return -1;
    }

    /**
     * Whether the type at {@code index} stands for one declared elsewhere, either by another space
     * or at another index of this one.
     */
    boolean isUsed(int index) {
        at(index);
        return origins.containsKey(index);
    }

    /**
     * The space that declared the type at {@code index}, against which the indices inside it
     * resolve. That is this space unless the type was used from another.
     */
    WitScope declaringScope(int index) {
        return originOf(index).scope;
    }

    /** Where the type at {@code index} sits in the space that declared it. */
    int declaringIndex(int index) {
        return originOf(index).index;
    }

    private Origin originOf(int index) {
        at(index);
        Origin origin = origins.get(index);
        return origin == null ? new Origin(this, index) : origin;
    }

    /** A slot in the space that declared a type, which is where its name and indices belong. */
    private static final class Origin {

        private final WitScope scope;
        private final int index;

        Origin(WitScope scope, int index) {
            this.scope = scope;
            this.index = index;
        }
    }
}
