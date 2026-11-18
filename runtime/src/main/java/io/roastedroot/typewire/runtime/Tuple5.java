package io.roastedroot.typewire.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

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
        return fromComponent(value, c0::cast, c1::cast, c2::cast, c3::cast, c4::cast);
    }

    /** The tuple a lifted value carries, each element converted by the function at its position. */
    public static <A, B, C, D, E> Tuple5<A, B, C, D, E> fromComponent(
            Object value,
            Function<Object, ? extends A> c0,
            Function<Object, ? extends B> c1,
            Function<Object, ? extends C> c2,
            Function<Object, ? extends D> c3,
            Function<Object, ? extends E> c4) {
        Map<?, ?> fields = (Map<?, ?>) value;
        return of(
                c0.apply(fields.get("0")),
                c1.apply(fields.get("1")),
                c2.apply(fields.get("2")),
                c3.apply(fields.get("3")),
                c4.apply(fields.get("4")));
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
        return toComponent(
                Function.identity(),
                Function.identity(),
                Function.identity(),
                Function.identity(),
                Function.identity());
    }

    /** This tuple as the ABI carries it, each element converted by the function at its position. */
    public Map<String, Object> toComponent(
            Function<? super A, ?> c0,
            Function<? super B, ?> c1,
            Function<? super C, ?> c2,
            Function<? super D, ?> c3,
            Function<? super E, ?> c4) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("0", c0.apply(f0));
        fields.put("1", c1.apply(f1));
        fields.put("2", c2.apply(f2));
        fields.put("3", c3.apply(f3));
        fields.put("4", c4.apply(f4));
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
