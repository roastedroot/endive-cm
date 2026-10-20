package run.endive.cm.wasi.p2.cli.terminalstderr;

import run.endive.cm.wasi.p2.bindings.wasi.cli.terminaloutput.TerminalOutput;
import run.endive.cm.wasi.p2.bindings.wasi.cli.terminalstderr.Host;

/**
 * The host side of {@code wasi:cli/terminal-stderr}, which tells the guest whether stderr is a
 * terminal.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/cli/wit/terminal.wit">wasi:cli/terminal-stderr</a>
 */
public final class HostTerminalStderr implements Host {

    private final TerminalOutput terminal;

    /**
     * @param tty whether stderr is a terminal
     */
    public HostTerminalStderr(boolean tty) {
        this.terminal = tty ? new TerminalOutput() {} : null;
    }

    /** The terminal stderr is, or {@code null} when it is not one. */
    @Override
    public TerminalOutput getTerminalStderr() {
        return terminal;
    }
}
