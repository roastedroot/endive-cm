package run.endive.cm.wasi.p2.cli;

import run.endive.wasm.WasmEngineException;

/**
 * Thrown when the guest exits through {@code wasi:cli/exit}, carrying the status code it reported.
 * It unwinds the guest's call up to the embedder, so the instance should not be called again.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/cli/wit/exit.wit">wasi:cli/exit</a>
 */
public final class WasiExitException extends WasmEngineException {

    private static final long serialVersionUID = 1L;

    private final int exitCode;

    public WasiExitException(int exitCode) {
        super("Process exit code: " + exitCode);
        this.exitCode = exitCode;
    }

    public int exitCode() {
        return exitCode;
    }

    /** An exit is not a failure, so there is no stack trace worth capturing. */
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
