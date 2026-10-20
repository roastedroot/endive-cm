package run.endive.cm.wasi.p2.cli.terminalstdout;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class HostTerminalStdoutTest {

    @Test
    void aTerminalIsReturnedWhenStdoutIsOne() {
        assertNotNull(new HostTerminalStdout(true).getTerminalStdout());
    }

    @Test
    void noTerminalIsReturnedWhenStdoutIsNotOne() {
        assertNull(new HostTerminalStdout(false).getTerminalStdout());
    }
}
