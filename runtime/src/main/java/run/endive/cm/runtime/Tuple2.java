package run.endive.cm.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A WIT {@code tuple} of two elements, which are reached by position.
 *
 * <p>The Canonical ABI despecializes a tuple to a record whose fields are labelled by
 * position, which is what {@link #toComponent} and {@link #fromComponent} convert between.
 * See <a
 * href="https://github.com/WebAssembly/component-model/blob/main/design/mvp/CanonicalABI.md#despecialization">Canonical
 * ABI despecialization</a>.
 */
public final class Tuple2<A, B> {

    private final A f0;
    private final B f1;

    private Tuple2(A f0, B f1) {
        this.f0 = f0;
        this.f1 = f1;
    }

    /** A tuple holding the given elements. */
    public static <A, B> Tuple2<A, B> of(A f0, B f1) {
        return new Tuple2<>(f0, f1);
    }

    /** The tuple a lifted value carries, each element cast to the class it was declared with. */
    public static <A, B> Tuple2<A, B> fromComponent(Object value, Class<A> c0, Class<B> c1) {
        Map<?, ?> fields = (Map<?, ?>) value;
        return of(c0.cast(fields.get("0")), c1.cast(fields.get("1")));
    }

    public A f0() {
        return f0;
    }

    public B f1() {
        return f1;
    }

    /** This tuple as the ABI carries it, which is a map labelled by element position. */
    public Map<String, Object> toComponent() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("0", f0);
        fields.put("1", f1);
        return fields;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Tuple2)) {
            return false;
        }
        Tuple2<?, ?> that = (Tuple2<?, ?>) o;
        return Objects.equals(f0, that.f0) && Objects.equals(f1, that.f1);
    }

    @Override
    public int hashCode() {
        return Objects.hash(f0, f1);
    }

    @Override
    public String toString() {
        return "(" + f0 + ", " + f1 + ")";
    }
}
