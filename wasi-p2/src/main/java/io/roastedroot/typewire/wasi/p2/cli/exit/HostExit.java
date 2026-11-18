package io.roastedroot.typewire.wasi.p2.cli.exit;

import io.roastedroot.typewire.runtime.Result;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.cli.exit.Host;
import io.roastedroot.typewire.wasi.p2.cli.WasiExitException;

/**
 * The host side of {@code wasi:cli/exit}, which ends the guest's call by throwing {@link
 * WasiExitException} with the status the guest reported.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/cli/wit/exit.wit">wasi:cli/exit</a>
 */
public final class HostExit implements Host {

    /** Exits with code 0 for {@code ok} and 1 for {@code err}. */
    @Override
    public void exit(Result<Void, Void> status) {
        throw new WasiExitException(status.isOk() ? 0 : 1);
    }

    @Override
    public void exitWithCode(Short statusCode) {
        throw new WasiExitException(statusCode);
    }
}
