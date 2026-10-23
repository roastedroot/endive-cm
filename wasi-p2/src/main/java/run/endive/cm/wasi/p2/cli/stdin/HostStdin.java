package run.endive.cm.wasi.p2.cli.stdin;

import java.io.InputStream;
import run.endive.cm.wasi.p2.bindings.wasi.cli.stdin.Host;
import run.endive.cm.wasi.p2.io.poll.HostPoll;
import run.endive.cm.wasi.p2.io.streams.BufferedHostInputStream;
import run.endive.cm.wasi.p2.io.streams.HostInputStream;

/**
 * The host side of {@code wasi:cli/stdin}, which hands the guest what an embedder's Java stream
 * holds.
 *
 * <p>A daemon thread starts reading the Java stream the first time the guest reads or polls stdin,
 * so a guest that only asks for it reads nothing. It pauses while the guest leaves {@value
 * #CAPACITY} bytes unread, and stops once the Java stream ends or fails, which the guest sees after
 * reading everything before it. Every handle the guest is given reads the same bytes, so dropping
 * one leaves the others usable. The Java stream is never closed.
 *
 * <p>Hosts given the same Java stream share that thread, and the one that read or polled stdin
 * most recently gets what it reads. A read cannot be interrupted, so what one returns after its host
 * closes goes to the next host given the stream. Bytes a guest left unread are dropped with its
 * host, as buffered stdio drops what it read ahead.
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
    private BufferedHostInputStream buffer;
    private StdinReader reader;

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
        return new StdinHandle(poll, this::buffer);
    }

    /**
     * Closes stdin for the guest and stops reading the Java stream for it. A thread blocked reading
     * it stops once that read returns, keeping what it read for the next host given the stream.
     */
    @Override
    public synchronized void close() {
        if (buffer == null) {
            buffer = new BufferedHostInputStream(poll);
        }
        if (reader != null) {
            reader.detach(buffer);
        }
        buffer.close();
    }

    /** The buffer every handle reads, which the stream's reader fills from the first time. */
    private synchronized BufferedHostInputStream buffer() {
        if (buffer == null) {
            if (source == null) {
                buffer = new BufferedHostInputStream(poll);
                buffer.close();
            } else {
                reader = StdinReader.of(source);
                buffer = new BufferedHostInputStream(poll, CAPACITY, reader::demand);
                reader.attach(buffer);
            }
        }
        return buffer;
    }
}
