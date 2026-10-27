package run.endive.cm.wasi.p2.cli.terminalstdin;

import run.endive.cm.wasi.p2.bindings.wasi.cli.terminalinput.TerminalInput;
import run.endive.cm.wasi.p2.bindings.wasi.cli.terminalstdin.Host;

/**
 * The host side of {@code wasi:cli/terminal-stdin}, which tells the guest whether stdin is a
 * terminal.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/cli/wit/terminal.wit">wasi:cli/terminal-stdin</a>
 */
public final class HostTerminalStdin implements Host {

    private final TerminalInput terminal;

    /**
     * @param tty whether stdin is a terminal
     */
    public HostTerminalStdin(boolean tty) {
        this.terminal = tty ? new TerminalInput() {} : null;
    }

    /** The terminal stdin is, or {@code null} when it is not one. */
    @Override
    public TerminalInput getTerminalStdin() {
        return terminal;
    }
}
