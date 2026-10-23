package run.endive.cm.wasi.p2.cli.terminalstdout;

import run.endive.cm.wasi.p2.bindings.wasi.cli.terminaloutput.TerminalOutput;
import run.endive.cm.wasi.p2.bindings.wasi.cli.terminalstdout.Host;

/**
 * The host side of {@code wasi:cli/terminal-stdout}, which tells the guest whether stdout is a
 * terminal.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/cli/wit/terminal.wit">wasi:cli/terminal-stdout</a>
 */
public final class HostTerminalStdout implements Host {

    private final TerminalOutput terminal;

    /**
     * @param tty whether stdout is a terminal
     */
    public HostTerminalStdout(boolean tty) {
        this.terminal = tty ? new TerminalOutput() {} : null;
    }

    /** The terminal stdout is, or {@code null} when it is not one. */
    @Override
    public TerminalOutput getTerminalStdout() {
        return terminal;
    }
}
