package io.roastedroot.typewire.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * A WIT {@code tuple} of six elements, which are reached by position.
 *
 * <p>The Canonical ABI despecializes a tuple to a record whose fields are labelled by
 * position, which is what {@link #toComponent} and {@link #fromComponent} convert between.
 * See <a
 * href="https://github.com/WebAssembly/component-model/blob/main/design/mvp/CanonicalABI.md#despecialization">Canonical
 * ABI despecialization</a>.
 */
public final class Tuple6<A, B, C, D, E, F> {

    private final A f0;
    private final B f1;
    private final C f2;
    private final D f3;
    private final E f4;
    private final F f5;

    private Tuple6(A f0, B f1, C f2, D f3, E f4, F f5) {
        this.f0 = f0;
        this.f1 = f1;
        this.f2 = f2;
        this.f3 = f3;
        this.f4 = f4;
        this.f5 = f5;
    }

    /** A tuple holding the given elements. */
    public static <A, B, C, D, E, F> Tuple6<A, B, C, D, E, F> of(
            A f0, B f1, C f2, D f3, E f4, F f5) {
        return new Tuple6<>(f0, f1, f2, f3, f4, f5);
    }

    /** The tuple a lifted value carries, each element cast to the class it was declared with. */
    public static <A, B, C, D, E, F> Tuple6<A, B, C, D, E, F> fromComponent(
            Object value,
            Class<A> c0,
            Class<B> c1,
            Class<C> c2,
            Class<D> c3,
            Class<E> c4,
            Class<F> c5) {
        return fromComponent(value, c0::cast, c1::cast, c2::cast, c3::cast, c4::cast, c5::cast);
    }

    /** The tuple a lifted value carries, each element converted by the function at its position. */
    public static <A, B, C, D, E, F> Tuple6<A, B, C, D, E, F> fromComponent(
            Object value,
            Function<Object, ? extends A> c0,
            Function<Object, ? extends B> c1,
            Function<Object, ? extends C> c2,
            Function<Object, ? extends D> c3,
            Function<Object, ? extends E> c4,
            Function<Object, ? extends F> c5) {
        Map<?, ?> fields = (Map<?, ?>) value;
        return of(
                c0.apply(fields.get("0")),
                c1.apply(fields.get("1")),
                c2.apply(fields.get("2")),
                c3.apply(fields.get("3")),
                c4.apply(fields.get("4")),
                c5.apply(fields.get("5")));
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

    public F f5() {
        return f5;
    }

    /** This tuple as the ABI carries it, which is a map labelled by element position. */
    public Map<String, Object> toComponent() {
        return toComponent(
                Function.identity(),
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
            Function<? super E, ?> c4,
            Function<? super F, ?> c5) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("0", c0.apply(f0));
        fields.put("1", c1.apply(f1));
        fields.put("2", c2.apply(f2));
        fields.put("3", c3.apply(f3));
        fields.put("4", c4.apply(f4));
        fields.put("5", c5.apply(f5));
        return fields;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Tuple6)) {
            return false;
        }
        Tuple6<?, ?, ?, ?, ?, ?> that = (Tuple6<?, ?, ?, ?, ?, ?>) o;
        return Objects.equals(f0, that.f0)
                && Objects.equals(f1, that.f1)
                && Objects.equals(f2, that.f2)
                && Objects.equals(f3, that.f3)
                && Objects.equals(f4, that.f4)
                && Objects.equals(f5, that.f5);
    }

    @Override
    public int hashCode() {
        return Objects.hash(f0, f1, f2, f3, f4, f5);
    }

    @Override
    public String toString() {
        return "(" + f0 + ", " + f1 + ", " + f2 + ", " + f3 + ", " + f4 + ", " + f5 + ")";
    }
}
