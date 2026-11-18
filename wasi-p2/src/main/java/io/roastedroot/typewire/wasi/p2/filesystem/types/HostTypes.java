package io.roastedroot.typewire.wasi.p2.filesystem.types;

import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.ErrorCode;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.Host;
import io.roastedroot.typewire.wasi.p2.io.error.HostError;

/**
 * The host side of {@code wasi:filesystem/types}, apart from its resources.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/filesystem/wit/types.wit">wasi:filesystem/types</a>
 */
public class HostTypes implements Host {

    /** The code a stream error stands for, or {@code null} when it is not a filesystem failure. */
    @Override
    public ErrorCode filesystemErrorCode(
            io.roastedroot.typewire.wasi.p2.bindings.wasi.io.error.Error err) {
        return err instanceof HostError ? ErrorCodes.codeOf(((HostError) err).cause()) : null;
    }
}
