package run.endive.cm.wasi.p2.cli.terminalstderr;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class HostTerminalStderrTest {

    @Test
    void aTerminalIsReturnedWhenStderrIsOne() {
        assertNotNull(new HostTerminalStderr(true).getTerminalStderr());
    }

    @Test
    void noTerminalIsReturnedWhenStderrIsNotOne() {
        assertNull(new HostTerminalStderr(false).getTerminalStderr());
    }
}
