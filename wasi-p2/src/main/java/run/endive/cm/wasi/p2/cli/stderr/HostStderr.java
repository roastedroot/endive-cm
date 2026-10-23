package run.endive.cm.wasi.p2.cli.stderr;

import java.io.OutputStream;
import run.endive.cm.wasi.p2.bindings.wasi.cli.stderr.Host;
import run.endive.cm.wasi.p2.cli.HostStdioOutputStream;
import run.endive.cm.wasi.p2.io.poll.HostPoll;

/**
 * The host side of {@code wasi:cli/stderr}, which writes what the guest sends to an embedder's
 * Java stream.
 *
 * <p>Each call hands the guest a stream of its own, so dropping one leaves the others usable.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/cli/wit/stdio.wit">wasi:cli/stderr</a>
 */
public final class HostStderr implements Host {

    private final HostPoll poll;
    private final OutputStream stderr;

    public HostStderr(HostPoll poll, OutputStream stderr) {
        this.poll = poll;
        this.stderr = stderr;
    }

    @Override
    public HostStdioOutputStream getStderr() {
        return new HostStdioOutputStream(poll, stderr);
    }
}
