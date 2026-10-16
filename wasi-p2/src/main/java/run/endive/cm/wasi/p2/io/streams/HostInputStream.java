package run.endive.cm.wasi.p2.io.streams;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import java.math.BigInteger;
import run.endive.cm.wasi.p2.bindings.wasi.io.poll.Pollable;
import run.endive.cm.wasi.p2.bindings.wasi.io.streams.InputStream;
import run.endive.cm.wasi.p2.io.Blocking;
import run.endive.cm.wasi.p2.io.poll.HostPoll;

/**
 * The host side of a {@code wasi:io/streams} input stream, built on two operations a source
 * implements without blocking. Everything else the interface asks for, including the blocking
 * variants, is derived from those.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/io/wit/streams.wit">wasi:io/streams</a>
 */
public abstract class HostInputStream implements InputStream {

    /** The most one read hands back, whatever the guest asks for. */
    static final int MAX_READ = 64 * 1024;

    private final EventExecutor eventLoopExecutor;
    private final HostPoll poll;

    protected HostInputStream(EventExecutor eventLoopExecutor, HostPoll poll) {
        this.eventLoopExecutor = eventLoopExecutor;
        this.poll = poll;
    }

    /**
     * Takes up to {@code max} bytes that are available now, which may be none. Asked for none, it
     * succeeds while the stream is open.
     *
     * @throws run.endive.cm.wasi.p2.bindings.wasi.io.streams.StreamErrorException once the stream
     *     has failed or is closed with nothing left to read
     */
    protected abstract byte[] readAvailable(int max);

    /** A future that is complete when a read would either return bytes or fail. */
    public abstract Future<?> readiness();

    @Override
    public byte[] read(BigInteger len) {
        return readAvailable(clamp(len));
    }

    @Override
    public byte[] blockingRead(BigInteger len) {
        int max = clamp(len);
        while (true) {
            byte[] bytes = readAvailable(max);
            if (bytes.length > 0 || max == 0) {
                return bytes;
            }
            Blocking.await(eventLoopExecutor, readiness());
        }
    }

    @Override
    public BigInteger skip(BigInteger len) {
        return BigInteger.valueOf(read(len).length);
    }

    @Override
    public BigInteger blockingSkip(BigInteger len) {
        return BigInteger.valueOf(blockingRead(len).length);
    }

    @Override
    public Pollable subscribe() {
        return poll.pollable(this::readiness);
    }

    private static int clamp(BigInteger len) {
        return len.min(BigInteger.valueOf(MAX_READ)).intValueExact();
    }
}
