package run.endive.cm.wasi.p2.cli;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.concurrent.CompletableFuture;
import run.endive.cm.wasi.p2.io.poll.HostPoll;
import run.endive.cm.wasi.p2.io.streams.HostOutputStream;
import run.endive.cm.wasi.p2.io.streams.StreamErrors;

/**
 * An output stream writing straight to an embedder's Java stream, which {@code get-stdout} and
 * {@code get-stderr} return.
 *
 * <p>The stream is always ready, and each write reaches the Java stream before it returns. A flush
 * flushes the Java stream. The stream closes after a failure. Dropping it leaves the Java stream
 * open, since the embedder owns it.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/cli/wit/stdio.wit">wasi:cli/stdout and wasi:cli/stderr</a>
 */
public final class HostStdioOutputStream extends HostOutputStream {

    /** How many bytes one {@code check-write} permits. */
    static final int CAPACITY = 1 << 20;

    private final OutputStream sink;
    private final CompletableFuture<?> ready = CompletableFuture.completedFuture(null);
    private boolean closed;

    public HostStdioOutputStream(HostPoll poll, OutputStream sink) {
        super(poll);
        this.sink = sink;
    }

    @Override
    public CompletableFuture<?> readiness() {
        return ready;
    }

    @Override
    protected long writeCapacity() {
        checkOpen();
        return CAPACITY;
    }

    @Override
    protected void writeBytes(byte[] contents) {
        checkOpen();
        try {
            sink.write(contents);
            checkSink();
        } catch (IOException e) {
            closed = true;
            throw StreamErrors.failed(e);
        }
    }

    @Override
    protected void startFlush() {
        checkOpen();
        try {
            sink.flush();
            checkSink();
        } catch (IOException e) {
            closed = true;
            throw StreamErrors.failed(e);
        }
    }

    /** A {@link PrintStream}, such as {@link System#out}, records a failure instead of throwing it. */
    private void checkSink() throws IOException {
        if (sink instanceof PrintStream && ((PrintStream) sink).checkError()) {
            throw new IOException("the print stream reports an error");
        }
    }

    private void checkOpen() {
        if (closed) {
            throw StreamErrors.closed();
        }
    }
}
