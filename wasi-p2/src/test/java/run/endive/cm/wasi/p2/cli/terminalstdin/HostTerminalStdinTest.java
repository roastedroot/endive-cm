package run.endive.cm.wasi.p2.cli.terminalstdin;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class HostTerminalStdinTest {

    @Test
    void aTerminalIsReturnedWhenStdinIsOne() {
        assertNotNull(new HostTerminalStdin(true).getTerminalStdin());
    }

    @Test
    void noTerminalIsReturnedWhenStdinIsNotOne() {
        assertNull(new HostTerminalStdin(false).getTerminalStdin());
    }
}
