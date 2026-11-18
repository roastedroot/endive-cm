package io.roastedroot.typewire.wasi.p2.cli.exit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.roastedroot.typewire.runtime.Result;
import io.roastedroot.typewire.wasi.p2.cli.WasiExitException;
import org.junit.jupiter.api.Test;
import run.endive.runtime.WasmException;

class HostExitTest {

    private final HostExit exit = new HostExit();

    @Test
    void exitingWithOkExitsWithZero() {
        assertEquals(0, exitCode(() -> exit.exit(Result.ok(null))));
    }

    @Test
    void exitingWithErrExitsWithOne() {
        assertEquals(1, exitCode(() -> exit.exit(Result.err(null))));
    }

    @Test
    void exitingWithACodeExitsWithThatCode() {
        assertEquals(0, exitCode(() -> exit.exitWithCode((short) 0)));
        assertEquals(255, exitCode(() -> exit.exitWithCode((short) 255)));
    }

    /**
     * Endive's interpreter catches a {@link WasmException} to hand it to the guest's own exception
     * handlers, so an exit must not be one or a guest could catch it.
     */
    @Test
    void anExitIsNotAGuestException() {
        WasiExitException thrown =
                assertThrows(WasiExitException.class, () -> exit.exit(Result.ok(null)));

        assertFalse(WasmException.class.isInstance(thrown));
        assertEquals(0, thrown.getStackTrace().length);
    }

    private static int exitCode(Runnable call) {
        return assertThrows(WasiExitException.class, call::run).exitCode();
    }
}
