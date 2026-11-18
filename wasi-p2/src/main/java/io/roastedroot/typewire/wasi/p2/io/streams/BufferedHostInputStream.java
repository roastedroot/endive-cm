package io.roastedroot.typewire.wasi.p2.io.streams;

import io.roastedroot.typewire.wasi.p2.io.poll.HostPoll;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An input stream holding what a source has produced until the guest reads it. The source may run
 * on any thread, such as an event loop delivering a channel's bytes or a thread reading a blocking
 * Java stream. Neither side ever waits on the other.
 *
 * <p>The source is asked to pause once the buffer holds {@code capacity} bytes, by {@link #offer}
 * answering {@code false}, and to resume through {@code onDemand} once the guest has read it below
 * half of that. A source that cannot pause may keep offering, and the buffer grows past capacity.
 */
public class BufferedHostInputStream extends HostInputStream {

    private static final Runnable NO_DEMAND = () -> {};

    /** The bit of {@link #state} that records the source was asked to pause. */
    private static final long PAUSED = 1L << 62;

    private final int capacity;
    private final Runnable onDemand;

    private final Queue<byte[]> chunks = new ConcurrentLinkedQueue<>();
    private final AtomicLong state = new AtomicLong();
    private final AtomicReference<End> end = new AtomicReference<>();
    private volatile CompletableFuture<Void> readiness = new CompletableFuture<>();
    private byte[] head;
    private int headOffset;

    /**
     * @param capacity how many bytes may be buffered before the source is asked to pause
     * @param onDemand run once a paused source should resume
     */
    public BufferedHostInputStream(HostPoll poll, int capacity, Runnable onDemand) {
        super(poll);
        this.capacity = capacity;
        this.onDemand = onDemand == null ? NO_DEMAND : onDemand;
    }

    /** A stream whose source never pauses, such as one that offers everything up front. */
    public BufferedHostInputStream(HostPoll poll) {
        this(poll, Integer.MAX_VALUE, null);
    }

    /**
     * Buffers {@code bytes} for the guest to read. The stream keeps the array without copying it, so
     * the source must not change it afterwards. Offering once the stream is closed has no effect.
     *
     * @return whether the source may keep producing, which is {@code false} once the buffer is full
     *     or the stream is closed
     */
    public boolean offer(byte[] bytes) {
        if (end.get() != null) {
            return false;
        }
        if (bytes.length > 0) {
            chunks.add(bytes);
        }
        long current;
        long next;
        do {
            current = state.get();
            long buffered = (current & ~PAUSED) + bytes.length;
            next = buffered >= capacity ? buffered | PAUSED : buffered;
        } while (!state.compareAndSet(current, next));
        if (bytes.length > 0) {
            readiness.complete(null);
        }
        return (next & PAUSED) == 0;
    }

    /** Ends the stream, which the guest sees once it has read everything buffered. */
    public void close() {
        end.compareAndSet(null, End.CLOSED);
        readiness.complete(null);
    }

    /** Fails the stream with {@code cause}, reported to the guest once it has read everything buffered. */
    public void fail(Throwable cause) {
        end.compareAndSet(null, new End(cause));
        readiness.complete(null);
    }

    /**
     * Whether the stream takes no more bytes, after {@link #close}, {@link #fail}, or the guest
     * dropping it. A source told to pause checks this to tell whether it should stop instead.
     */
    public boolean isClosed() {
        return end.get() != null;
    }

    /**
     * Closes the stream once the guest drops it and discards what it had not read. A paused source
     * is resumed, so it can see the stream is closed.
     */
    @Override
    public void drop() {
        end.set(End.CLOSED);
        chunks.clear();
        head = null;
        readiness.complete(null);
        if ((state.getAndSet(0) & PAUSED) != 0) {
            onDemand.run();
        }
    }

    @Override
    public CompletableFuture<?> readiness() {
        return readiness;
    }

    /** Called only by the guest, which reads from one thread at a time. */
    @Override
    protected byte[] readAvailable(int max) {
        End ended = end.get();
        if (!hasBytes()) {
            if (ended != null && ended.failure != null) {
                end.set(End.CLOSED);
                throw StreamErrors.failed(ended.failure);
            }
            if (ended != null) {
                throw StreamErrors.closed();
            }
            awaitBytes();
            return new byte[0];
        }
        byte[] bytes = take(max);
        if (!hasBytes()) {
            awaitBytes();
        }
        if (bytes.length > 0 && taken(bytes.length)) {
            onDemand.run();
        }
        return bytes;
    }

    /** Whether a chunk is left to read, moving the next one to the head when that is used up. */
    private boolean hasBytes() {
        if (head == null) {
            head = chunks.poll();
            headOffset = 0;
        }
        return head != null;
    }

    /**
     * Takes up to {@code max} of the bytes counted in {@link #state}. A source counts its bytes
     * only after queueing them, so those counted are always there to take.
     */
    private byte[] take(int max) {
        byte[] bytes = new byte[(int) Math.min(max, state.get() & ~PAUSED)];
        int filled = 0;
        while (filled < bytes.length) {
            hasBytes();
            int count = Math.min(bytes.length - filled, head.length - headOffset);
            System.arraycopy(head, headOffset, bytes, filled, count);
            filled += count;
            headOffset += count;
            if (headOffset == head.length) {
                head = null;
            }
        }
        return bytes;
    }

    /** Counts {@code count} bytes as read, answering whether the source should now resume. */
    private boolean taken(int count) {
        while (true) {
            long current = state.get();
            long buffered = (current & ~PAUSED) - count;
            boolean resume = (current & PAUSED) != 0 && buffered < capacity / 2;
            if (state.compareAndSet(current, resume ? buffered : buffered | (current & PAUSED))) {
                return resume;
            }
        }
    }

    /**
     * Makes {@link #readiness} pending once the buffer is empty. A source that offers or ends the
     * stream meanwhile completes either the old future or the new one, which the check after
     * publishing it catches.
     */
    private void awaitBytes() {
        if (!readiness.isDone()) {
            return;
        }
        CompletableFuture<Void> pending = new CompletableFuture<>();
        readiness = pending;
        if (!chunks.isEmpty() || end.get() != null) {
            pending.complete(null);
        }
    }

    /** How the stream ended, with the failure the guest has yet to see, if any. */
    private static final class End {

        static final End CLOSED = new End(null);

        final Throwable failure;

        End(Throwable failure) {
            this.failure = failure;
        }
    }
}
