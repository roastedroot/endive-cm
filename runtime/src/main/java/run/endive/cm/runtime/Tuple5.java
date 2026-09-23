package run.endive.cm.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A WIT {@code tuple} of five elements, which are reached by position.
 *
 * <p>The Canonical ABI despecializes a tuple to a record whose fields are labelled by
 * position, which is what {@link #toComponent} and {@link #fromComponent} convert between.
 * See <a
 * href="https://github.com/WebAssembly/component-model/blob/main/design/mvp/CanonicalABI.md#despecialization">Canonical
 * ABI despecialization</a>.
 */
public final class Tuple5<A, B, C, D, E> {

    private final A f0;
    private final B f1;
    private final C f2;
    private final D f3;
    private final E f4;

    private Tuple5(A f0, B f1, C f2, D f3, E f4) {
        this.f0 = f0;
        this.f1 = f1;
        this.f2 = f2;
        this.f3 = f3;
        this.f4 = f4;
    }

    /** A tuple holding the given elements. */
    public static <A, B, C, D, E> Tuple5<A, B, C, D, E> of(A f0, B f1, C f2, D f3, E f4) {
        return new Tuple5<>(f0, f1, f2, f3, f4);
    }

    /** The tuple a lifted value carries, each element cast to the class it was declared with. */
    public static <A, B, C, D, E> Tuple5<A, B, C, D, E> fromComponent(
            Object value, Class<A> c0, Class<B> c1, Class<C> c2, Class<D> c3, Class<E> c4) {
        Map<?, ?> fields = (Map<?, ?>) value;
        return of(
                c0.cast(fields.get("0")),
                c1.cast(fields.get("1")),
                c2.cast(fields.get("2")),
                c3.cast(fields.get("3")),
                c4.cast(fields.get("4")));
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

    public D f3() {
        return f3;
    }

    public E f4() {
        return f4;
    }

    /** This tuple as the ABI carries it, which is a map labelled by element position. */
    public Map<String, Object> toComponent() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("0", f0);
        fields.put("1", f1);
        fields.put("2", f2);
        fields.put("3", f3);
        fields.put("4", f4);
        return fields;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Tuple5)) {
            return false;
        }
        Tuple5<?, ?, ?, ?, ?> that = (Tuple5<?, ?, ?, ?, ?>) o;
        return Objects.equals(f0, that.f0)
                && Objects.equals(f1, that.f1)
                && Objects.equals(f2, that.f2)
                && Objects.equals(f3, that.f3)
                && Objects.equals(f4, that.f4);
    }

    @Override
    public int hashCode() {
        return Objects.hash(f0, f1, f2, f3, f4);
    }

    @Override
    public String toString() {
        return "(" + f0 + ", " + f1 + ", " + f2 + ", " + f3 + ", " + f4 + ")";
    }
}
