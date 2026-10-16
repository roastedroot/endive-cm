package run.endive.cm.wasi.p2.io.error;

import java.util.Objects;

/**
 * The host side of the {@code wasi:io/error} resource, carrying the {@link Throwable} that made an
 * operation fail.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/io/wit/error.wit">wasi:io/error</a>
 */
// FQN of interface used because a simple Error resolves to java.lang.Error before the bindings
// exist.
public final class HostError implements run.endive.cm.wasi.p2.bindings.wasi.io.error.Error {

    private final Throwable cause;

    public HostError(Throwable cause) {
        this.cause = Objects.requireNonNull(cause, "cause");
    }

    /** The failure this error reports. */
    public Throwable cause() {
        return cause;
    }

    /** A description meant for a person reading it, whose format may change at any time. */
    @Override
    public String toDebugString() {
        return cause.toString();
    }

    @Override
    public String toString() {
        return toDebugString();
    }
}
