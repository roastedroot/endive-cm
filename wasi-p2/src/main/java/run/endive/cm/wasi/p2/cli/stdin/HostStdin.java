package run.endive.cm.wasi.p2.cli.stdin;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import run.endive.cm.wasi.p2.bindings.wasi.cli.stdin.Host;
import run.endive.cm.wasi.p2.io.poll.HostPoll;
import run.endive.cm.wasi.p2.io.streams.BufferedHostInputStream;
import run.endive.cm.wasi.p2.io.streams.HostInputStream;

/**
 * The host side of {@code wasi:cli/stdin}, which hands the guest what an embedder's Java stream
 * holds.
 *
 * <p>A daemon thread starts reading the Java stream the first time the guest asks for stdin. It
 * pauses while the guest leaves {@value #CAPACITY} bytes unread, and stops once the Java stream
 * ends or fails, which the guest sees after reading everything before it. Every handle the guest is
 * given reads the same bytes, so dropping one leaves the others usable. The Java stream is never
 * closed.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/cli/wit/stdio.wit">wasi:cli/stdin</a>
 */
public final class HostStdin implements Host, AutoCloseable {

    /** The most one read of the Java stream takes. */
    static final int CHUNK_SIZE = 8 * 1024;

    /** How many unread bytes make the reading thread pause. */
    static final int CAPACITY = 64 * 1024;

    private final HostPoll poll;
    private final InputStream source;
    private final Semaphore demand = new Semaphore(0);
    private BufferedHostInputStream buffer;

    /**
     * @param source the Java stream to read, or {@code null} for a stdin that is empty and already
     *     closed
     */
    public HostStdin(HostPoll poll, InputStream source) {
        this.poll = poll;
        this.source = source;
    }

    @Override
    public HostInputStream getStdin() {
        return new StdinHandle(poll, buffer());
    }

    /**
     * Closes stdin for the guest and stops reading the Java stream. A thread blocked reading it
     * stops once that read returns.
     */
    @Override
    public synchronized void close() {
        if (buffer == null) {
            buffer = new BufferedHostInputStream(poll);
        }
        buffer.close();
        demand.release();
    }

    /** The buffer every handle reads, whose reading thread starts the first time it is needed. */
    private synchronized BufferedHostInputStream buffer() {
        if (buffer == null) {
            buffer = new BufferedHostInputStream(poll, CAPACITY, demand::release);
            if (source == null) {
                buffer.close();
            } else {
                BufferedHostInputStream target = buffer;
                Thread reader = new Thread(() -> pump(target), "endive-wasi-p2-stdin");
                reader.setDaemon(true);
                reader.start();
            }
        }
        return buffer;
    }

    /** Moves the Java stream into {@code target} until either of them ends. */
    private void pump(BufferedHostInputStream target) {
        byte[] chunk = new byte[CHUNK_SIZE];
        try {
            while (!target.isClosed()) {
                int read = source.read(chunk);
                if (read < 0) {
                    target.close();
                } else if (!target.offer(Arrays.copyOf(chunk, read)) && !target.isClosed()) {
                    demand.acquireUninterruptibly();
                }
            }
        } catch (IOException | RuntimeException e) {
            target.fail(e);
        }
    }
}
