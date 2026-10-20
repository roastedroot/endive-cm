package run.endive.cm.wasi.p2.cli.stdout;

import java.io.OutputStream;
import run.endive.cm.wasi.p2.bindings.wasi.cli.stdout.Host;
import run.endive.cm.wasi.p2.cli.HostStdioOutputStream;
import run.endive.cm.wasi.p2.io.poll.HostPoll;

/**
 * The host side of {@code wasi:cli/stdout}, which writes what the guest sends to an embedder's
 * Java stream.
 *
 * <p>Each call hands the guest a stream of its own, so dropping one leaves the others usable.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/cli/wit/stdio.wit">wasi:cli/stdout</a>
 */
public final class HostStdout implements Host {

    private final HostPoll poll;
    private final OutputStream stdout;

    public HostStdout(HostPoll poll, OutputStream stdout) {
        this.poll = poll;
        this.stdout = stdout;
    }

    @Override
    public HostStdioOutputStream getStdout() {
        return new HostStdioOutputStream(poll, stdout);
    }
}
