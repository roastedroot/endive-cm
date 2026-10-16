package run.endive.cm.wasi.p2.io.streams;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.locks.ReentrantLock;
import run.endive.cm.wasi.p2.io.poll.HostPoll;

/**
 * An input stream holding what a source has produced until the guest reads it. The source may run
 * on any thread, such as an event loop delivering a channel's bytes or a thread reading a blocking
 * Java stream.
 *
 * <p>The source is asked to pause once the buffer holds {@code capacity} bytes, by {@link #offer}
 * answering {@code false}, and to resume through {@code onDemand} once the guest has read it below
 * half of that. A source that cannot pause may keep offering, and the buffer grows past capacity.
 */
public class BufferedHostInputStream extends HostInputStream {

    private static final Runnable NO_DEMAND = () -> {};

    private final EventExecutor eventLoopExecutor;
    private final int capacity;
    private final Runnable onDemand;

    private final ReentrantLock lock = new ReentrantLock();
    private final Deque<byte[]> chunks = new ArrayDeque<>();
    private int headOffset;
    private int buffered;
    private boolean paused;
    private boolean closed;
    private Throwable failure;
    private volatile Promise<Void> readiness;

    /**
     * @param capacity how many bytes may be buffered before the source is asked to pause
     * @param onDemand run once a paused source should resume, outside of any lock this holds
     */
    public BufferedHostInputStream(
            EventExecutor eventLoopExecutor, HostPoll poll, int capacity, Runnable onDemand) {
        super(eventLoopExecutor, poll);
        this.eventLoopExecutor = eventLoopExecutor;
        this.capacity = capacity;
        this.onDemand = onDemand == null ? NO_DEMAND : onDemand;
        this.readiness = eventLoopExecutor.newPromise();
    }

    /** A stream whose source never pauses, such as one that offers everything up front. */
    public BufferedHostInputStream(EventExecutor eventLoopExecutor, HostPoll poll) {
        this(eventLoopExecutor, poll, Integer.MAX_VALUE, null);
    }

    /**
     * Buffers {@code bytes} for the guest to read. Offering after {@link #close} or {@link #fail}
     * has no effect.
     *
     * @return whether the source may keep producing, which is {@code false} once the buffer is full
     */
    public boolean offer(byte[] bytes) {
        lock.lock();
        try {
            if (closed || failure != null) {
                return false;
            }
            if (bytes.length > 0) {
                chunks.addLast(bytes);
                buffered += bytes.length;
                readiness.trySuccess(null);
            }
            paused = buffered >= capacity;
            return !paused;
        } finally {
            lock.unlock();
        }
    }

    /** Ends the stream, which the guest sees once it has read everything buffered. */
    public void close() {
        lock.lock();
        try {
            closed = true;
            readiness.trySuccess(null);
        } finally {
            lock.unlock();
        }
    }

    /** Fails the stream with {@code cause}, reported to the guest ahead of anything buffered. */
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
    protected byte[] readAvailable(int max) {
        byte[] bytes;
        boolean resume;
        lock.lock();
        try {
            if (failure != null) {
                Throwable cause = failure;
                failure = null;
                closed = true;
                discard();
                throw StreamErrors.failed(cause);
            }
            if (buffered == 0) {
                if (closed) {
                    throw StreamErrors.closed();
                }
                return new byte[0];
            }
            bytes = take(max);
            if (buffered == 0 && !closed) {
                readiness = eventLoopExecutor.newPromise();
            }
            resume = paused && buffered < capacity / 2;
            if (resume) {
                paused = false;
            }
        } finally {
            lock.unlock();
        }
        if (resume) {
            onDemand.run();
        }
        return bytes;
    }

    private byte[] take(int max) {
        byte[] bytes = new byte[Math.min(max, buffered)];
        int filled = 0;
        while (filled < bytes.length) {
            byte[] head = chunks.peekFirst();
            int count = Math.min(bytes.length - filled, head.length - headOffset);
            System.arraycopy(head, headOffset, bytes, filled, count);
            filled += count;
            headOffset += count;
            if (headOffset == head.length) {
                chunks.removeFirst();
                headOffset = 0;
            }
        }
        buffered -= bytes.length;
        return bytes;
    }

    private void discard() {
        chunks.clear();
        headOffset = 0;
        buffered = 0;
    }
}
