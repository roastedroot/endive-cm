package io.roastedroot.typewire.wasi.p2.filesystem.types;

import io.roastedroot.typewire.wasi.p2.io.poll.HostPoll;
import io.roastedroot.typewire.wasi.p2.io.streams.HostOutputStream;
import io.roastedroot.typewire.wasi.p2.io.streams.StreamErrors;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

/**
 * An output stream writing a file, from an offset or at its end, which a {@code write-via-stream}
 * or an {@code append-via-stream} returns.
 *
 * <p>A file is always ready to write, and each write reaches the file before it returns, so a
 * flush has nothing left to do. The stream closes after a failure.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/filesystem/wit/types.wit">wasi:filesystem/types, write-via-stream</a>
 */
final class HostFileOutputStream extends HostOutputStream {

    /** How many bytes one {@code check-write} permits. */
    static final int CAPACITY = 1 << 20;

    private final SharedChannel file;
    private final CompletableFuture<?> ready;
    private final boolean append;
    private long position;
    private boolean closed;
    private boolean dropped;

    private HostFileOutputStream(HostPoll poll, SharedChannel file, long position, boolean append) {
        super(poll);
        this.file = file.retain();
        this.ready = CompletableFuture.completedFuture(null);
        this.position = position;
        this.append = append;
    }

    /** A stream writing from {@code position}. */
    static HostFileOutputStream at(HostPoll poll, SharedChannel file, long position) {
        return new HostFileOutputStream(poll, file, position, false);
    }

    /** A stream writing at whatever the end of the file is when each write happens. */
    static HostFileOutputStream appending(HostPoll poll, SharedChannel file) {
        return new HostFileOutputStream(poll, file, 0, true);
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
            if (append) {
                file.append(ByteBuffer.wrap(contents));
            } else {
                file.writeFully(ByteBuffer.wrap(contents), position);
                position += contents.length;
            }
        } catch (IOException e) {
            closed = true;
            throw StreamErrors.failed(e);
        }
    }

    @Override
    protected void startFlush() {
        checkOpen();
    }

    @Override
    public void drop() {
        if (!dropped) {
            dropped = true;
            file.release();
        }
    }

    private void checkOpen() {
        if (closed) {
            throw StreamErrors.closed();
        }
    }
}
