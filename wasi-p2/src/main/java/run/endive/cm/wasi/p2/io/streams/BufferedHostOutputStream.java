package run.endive.cm.wasi.p2.io.streams;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.locks.ReentrantLock;
import run.endive.cm.wasi.p2.io.poll.HostPoll;

/**
 * An output stream holding what the guest writes until a consumer takes it. The consumer may run on
 * any thread, such as a worker writing to a blocking Java stream.
 *
 * <p>The guest may buffer up to {@code capacity} bytes. A flush completes once the consumer has
 * taken everything written before it, and the guest may not write in the meantime. The consumer
 * learns there is something to take through {@code onWritten}.
 */
public class BufferedHostOutputStream extends HostOutputStream {

    private static final Runnable NOTHING_WRITTEN = () -> {};

    private final EventExecutor eventLoopExecutor;
    private final int capacity;
    private final Runnable onWritten;

    private final ReentrantLock lock = new ReentrantLock();
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private boolean flushing;
    private boolean closed;
    private Throwable failure;
    private volatile Promise<Void> readiness;

    /**
     * @param capacity how many bytes the guest may buffer before it has to wait for the consumer
     * @param onWritten run after the guest writes or flushes, outside of any lock this holds
     */
    public BufferedHostOutputStream(
            EventExecutor eventLoopExecutor, HostPoll poll, int capacity, Runnable onWritten) {
        super(eventLoopExecutor, poll);
        this.eventLoopExecutor = eventLoopExecutor;
        this.capacity = capacity;
        this.onWritten = onWritten == null ? NOTHING_WRITTEN : onWritten;
        this.readiness = eventLoopExecutor.<Void>newPromise().setSuccess(null);
    }

    /**
     * Takes everything the guest has written, which completes a flush in progress and frees the
     * buffer for more writes.
     */
    public byte[] drain() {
        lock.lock();
        try {
            byte[] bytes = buffer.toByteArray();
            buffer.reset();
            flushing = false;
            readiness.trySuccess(null);
            return bytes;
        } finally {
            lock.unlock();
        }
    }

    /** Ends the stream, which the guest sees on its next operation. */
    public void close() {
        lock.lock();
        try {
            closed = true;
            readiness.trySuccess(null);
        } finally {
            lock.unlock();
        }
    }

    /** Fails the stream with {@code cause}, which the guest sees on its next operation. */
    public void fail(Throwable cause) {
        lock.lock();
        try {
            if (failure == null && !closed) {
                failure = cause;
            }
            readiness.trySuccess(null);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Future<?> readiness() {
        return readiness;
    }

    @Override
    protected long writeCapacity() {
        lock.lock();
        try {
            checkOpen();
            return flushing ? 0 : capacity - buffer.size();
        } finally {
            lock.unlock();
        }
    }

    @Override
    protected void writeBytes(byte[] contents) {
        lock.lock();
        try {
            checkOpen();
            buffer.write(contents, 0, contents.length);
            if (buffer.size() >= capacity) {
                notReady();
            }
        } finally {
            lock.unlock();
        }
        onWritten.run();
    }

    @Override
    protected void startFlush() {
        lock.lock();
        try {
            checkOpen();
            if (buffer.size() == 0) {
                return;
            }
            flushing = true;
            notReady();
        } finally {
            lock.unlock();
        }
        onWritten.run();
    }

    /** Reports a failure once, after which the stream is closed. */
    private void checkOpen() {
        if (failure != null) {
            Throwable cause = failure;
            failure = null;
            closed = true;
            throw StreamErrors.failed(cause);
        }
        if (closed) {
            throw StreamErrors.closed();
        }
    }

    private void notReady() {
        if (readiness.isDone()) {
            readiness = eventLoopExecutor.newPromise();
        }
    }
}
