package run.endive.cm.runtime;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;
import run.endive.cm.abi.VariantValue;

/**
 * A WIT {@code result} reached as a value, such as a parameter or a field, which holds either an
 * ok payload or an error payload. A side declared without a payload is typed as {@link Void} and
 * holds {@code null}.
 *
 * <p>The Canonical ABI despecializes a result to a variant with the cases {@code ok} and {@code
 * error}. {@link #toComponent} and {@link #fromComponent} convert a result to and from that
 * variant. See <a
 * href="https://github.com/WebAssembly/component-model/blob/main/design/mvp/CanonicalABI.md#despecialization">Canonical
 * ABI despecialization</a>.
 */
public final class Result<T, E> {

    private static final String OK = "ok";
    private static final String ERROR = "error";

    private final boolean ok;
    private final T value;
    private final E error;

    private Result(boolean ok, T value, E error) {
        this.ok = ok;
        this.value = value;
        this.error = error;
    }

    /** The ok case carrying {@code value}. */
    public static <T, E> Result<T, E> ok(T value) {
        return new Result<>(true, value, null);
    }

    /** The error case carrying {@code error}. */
    public static <T, E> Result<T, E> err(E error) {
        return new Result<>(false, null, error);
    }

    /** The result a lifted value carries, its payload cast to the class declared for its case. */
    public static <T, E> Result<T, E> fromComponent(
            Object value, Class<T> okClass, Class<E> errorClass) {
        return fromComponent(value, okClass::cast, errorClass::cast);
    }

    /** The result a lifted value carries, its payload converted by the function for its case. */
    public static <T, E> Result<T, E> fromComponent(
            Object value,
            Function<Object, ? extends T> okConverter,
            Function<Object, ? extends E> errorConverter) {
        VariantValue variant = (VariantValue) value;
        switch (variant.label()) {
            case OK:
                return ok(okConverter.apply(variant.value()));
            case ERROR:
                return err(errorConverter.apply(variant.value()));
            default:
                throw new IllegalArgumentException("unknown result case: " + variant.label());
        }
    }

    public boolean isOk() {
        return ok;
    }

    /**
     * The ok payload.
     *
     * @throws NoSuchElementException when this is the error case
     */
    public T value() {
        if (!ok) {
            throw new NoSuchElementException("an error result has no ok payload");
        }
        return value;
    }

    /**
     * The error payload.
     *
     * @throws NoSuchElementException when this is the ok case
     */
    public E error() {
        if (ok) {
            throw new NoSuchElementException("an ok result has no error payload");
        }
        return error;
    }

    /** This result as the ABI carries it, which is a variant labelled by its case. */
    public VariantValue toComponent() {
        return toComponent(Function.identity(), Function.identity());
    }

    /** This result as the ABI carries it, its payload converted by the function for its case. */
    public VariantValue toComponent(
            Function<? super T, ?> okConverter, Function<? super E, ?> errorConverter) {
        return ok
                ? VariantValue.of(OK, okConverter.apply(value))
                : VariantValue.of(ERROR, errorConverter.apply(error));
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Result)) {
            return false;
        }
        Result<?, ?> that = (Result<?, ?>) o;
        return ok == that.ok
                && Objects.equals(value, that.value)
                && Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ok, value, error);
    }

    @Override
    public String toString() {
        return ok ? "ok(" + value + ")" : "err(" + error + ")";
    }
}
