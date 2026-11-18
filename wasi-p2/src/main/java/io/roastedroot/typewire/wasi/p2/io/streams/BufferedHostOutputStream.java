package io.roastedroot.typewire.wasi.p2.io.streams;

import io.roastedroot.typewire.wasi.p2.io.poll.HostPoll;
import java.io.ByteArrayOutputStream;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An output stream holding what the guest writes until a consumer delivers it. The consumer may run
 * on any thread, such as a worker writing to a blocking Java stream. Neither side ever waits on the
 * other.
 *
 * <p>The consumer learns there is something to take through {@code onWritten}, takes it with
 * {@link #drain}, and calls {@link #markDelivered} once those bytes have reached their destination.
 * The guest may have up to {@code capacity} bytes not yet delivered. A flush completes once
 * everything written before it is delivered, and the guest may not write in the meantime.
 *
 * <p>The stream keeps each array written to it rather than copying it.
 */
public class BufferedHostOutputStream extends HostOutputStream {

    private static final Runnable NOTHING_WRITTEN = () -> {};

    private final int capacity;
    private final Runnable onWritten;

    private final Queue<byte[]> chunks = new ConcurrentLinkedQueue<>();
    private final AtomicLong drained = new AtomicLong();
    private final AtomicLong delivered = new AtomicLong();
    private final AtomicReference<End> end = new AtomicReference<>();

    /** Only the guest updates this, so the update needs no atomic operation. */
    private volatile long written;

    private volatile long flushedThrough;
    private volatile CompletableFuture<Void> readiness = CompletableFuture.completedFuture(null);

    /**
     * @param capacity how many bytes the guest may buffer before it has to wait for the consumer
     * @param onWritten run after the guest writes or flushes
     */
    public BufferedHostOutputStream(HostPoll poll, int capacity, Runnable onWritten) {
        super(poll);
        this.capacity = capacity;
        this.onWritten = onWritten == null ? NOTHING_WRITTEN : onWritten;
    }

    /**
     * Takes everything the guest has written. The bytes keep their room in the buffer until the
     * consumer marks them delivered.
     */
    public byte[] drain() {
        byte[] first = chunks.poll();
        byte[] bytes;
        if (first == null) {
            bytes = new byte[0];
        } else if (chunks.isEmpty()) {
            bytes = first;
        } else {
            ByteArrayOutputStream joined = new ByteArrayOutputStream();
            joined.writeBytes(first);
            for (byte[] chunk = chunks.poll(); chunk != null; chunk = chunks.poll()) {
                joined.writeBytes(chunk);
            }
            bytes = joined.toByteArray();
        }
        drained.addAndGet(bytes.length);
        return bytes;
    }

    /**
     * Marks {@code count} drained bytes as having reached their destination, which frees their
     * room and completes a flush once everything written before it is delivered.
     *
     * @throws IllegalArgumentException when more bytes are marked than were drained
     */
    public void markDelivered(int count) {
        long current;
        do {
            current = delivered.get();
            if (count < 0 || current + count > drained.get()) {
                throw new IllegalArgumentException(
                        count
                                + " bytes marked delivered, but "
                                + (drained.get() - current)
                                + " were drained and not yet delivered");
            }
        } while (!delivered.compareAndSet(current, current + count));
        if (isReady()) {
            readiness.complete(null);
        }
    }

    /** Ends the stream, which the guest sees on its next operation. */
    public void close() {
        end.compareAndSet(null, End.CLOSED);
        readiness.complete(null);
    }

    /** Fails the stream with {@code cause}, which the guest sees on its next operation. */
    public void fail(Throwable cause) {
        end.compareAndSet(null, new End(cause));
        readiness.complete(null);
    }

    /**
     * Whether the guest can no longer write, after {@link #close}, {@link #fail}, or the guest
     * dropping the stream. Bytes it wrote before may still be drained.
     */
    public boolean isClosed() {
        return end.get() != null;
    }

    /**
     * Closes the stream once the guest drops it, and tells the consumer through {@code onWritten}
     * so it can drain what is left and see the stream is closed.
     */
    @Override
    public void drop() {
        close();
        onWritten.run();
    }

    @Override
    public CompletableFuture<?> readiness() {
        return readiness;
    }

    @Override
    protected long writeCapacity() {
        checkOpen();
        long done = delivered.get();
        return done < flushedThrough ? 0 : capacity - (written - done);
    }

    @Override
    @SuppressWarnings("NonAtomicOperationOnVolatileField")
    protected void writeBytes(byte[] contents) {
        checkOpen();
        if (contents.length == 0) {
            return;
        }
        written = written + contents.length;
        chunks.add(contents);
        if (written - delivered.get() >= capacity) {
            notReady();
        }
        onWritten.run();
    }

    @Override
    protected void startFlush() {
        checkOpen();
        if (written == delivered.get()) {
            return;
        }
        flushedThrough = written;
        notReady();
        onWritten.run();
    }

    /** Reports a failure once, after which the stream is closed. */
    private void checkOpen() {
        End ended = end.get();
        if (ended == null) {
            return;
        }
        if (ended.failure != null) {
            end.set(End.CLOSED);
            throw StreamErrors.failed(ended.failure);
        }
        throw StreamErrors.closed();
    }

    /** Whether the guest may write, with no flush pending and room in the buffer, or has ended. */
    private boolean isReady() {
        long done = delivered.get();
        return end.get() != null || (done >= flushedThrough && written - done < capacity);
    }

    /**
     * Makes {@link #readiness} pending. A consumer that delivers or ends the stream meanwhile
     * completes either the old future or the new one, which the check after publishing it
     * catches.
     */
    private void notReady() {
        if (!readiness.isDone()) {
            return;
        }
        CompletableFuture<Void> pending = new CompletableFuture<>();
        readiness = pending;
        if (isReady()) {
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
