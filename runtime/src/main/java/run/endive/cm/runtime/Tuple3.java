package run.endive.cm.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A WIT {@code tuple} of three elements, which are reached by position.
 *
 * <p>The Canonical ABI despecializes a tuple to a record whose fields are labelled by
 * position, which is what {@link #toComponent} and {@link #fromComponent} convert between.
 * See <a
 * href="https://github.com/WebAssembly/component-model/blob/main/design/mvp/CanonicalABI.md#despecialization">Canonical
 * ABI despecialization</a>.
 */
public final class Tuple3<A, B, C> {

    private final A f0;
    private final B f1;
    private final C f2;

    private Tuple3(A f0, B f1, C f2) {
        this.f0 = f0;
        this.f1 = f1;
        this.f2 = f2;
    }

    /** A tuple holding the given elements. */
    public static <A, B, C> Tuple3<A, B, C> of(A f0, B f1, C f2) {
        return new Tuple3<>(f0, f1, f2);
    }

    /** The tuple a lifted value carries, each element cast to the class it was declared with. */
    public static <A, B, C> Tuple3<A, B, C> fromComponent(
            Object value, Class<A> c0, Class<B> c1, Class<C> c2) {
        Map<?, ?> fields = (Map<?, ?>) value;
        return of(c0.cast(fields.get("0")), c1.cast(fields.get("1")), c2.cast(fields.get("2")));
    }

    public A f0() {
        return f0;
    }

    public B f1() {
        return f1;
    }

    public C f2() {
        return f2;
    }

    /** This tuple as the ABI carries it, which is a map labelled by element position. */
    public Map<String, Object> toComponent() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("0", f0);
        fields.put("1", f1);
        fields.put("2", f2);
        return fields;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Tuple3)) {
            return false;
        }
        Tuple3<?, ?, ?> that = (Tuple3<?, ?, ?>) o;
        return Objects.equals(f0, that.f0)
                && Objects.equals(f1, that.f1)
                && Objects.equals(f2, that.f2);
    }

    @Override
    public int hashCode() {
        return Objects.hash(f0, f1, f2);
    }

    @Override
    public String toString() {
        return "(" + f0 + ", " + f1 + ", " + f2 + ")";
    }
}
