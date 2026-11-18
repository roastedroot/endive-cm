package io.roastedroot.typewire.wasi.p2.filesystem.types;

import io.roastedroot.typewire.wasi.p2.io.poll.HostPoll;
import io.roastedroot.typewire.wasi.p2.io.streams.HostInputStream;
import io.roastedroot.typewire.wasi.p2.io.streams.StreamErrors;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * An input stream reading a file from an offset, which a {@code read-via-stream} returns.
 *
 * <p>A file is always ready to read, so a read returns at once. The stream closes at the end of
 * the file, and after a failure.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/filesystem/wit/types.wit">wasi:filesystem/types, read-via-stream</a>
 */
final class HostFileInputStream extends HostInputStream {

    private final SharedChannel file;
    private final CompletableFuture<?> ready;
    private long position;
    private boolean closed;
    private boolean dropped;

    HostFileInputStream(HostPoll poll, SharedChannel file, long position) {
        super(poll);
        this.file = file.retain();
        this.ready = CompletableFuture.completedFuture(null);
        this.position = position;
    }

    @Override
    public CompletableFuture<?> readiness() {
        return ready;
    }

    @Override
    protected byte[] readAvailable(int max) {
        if (closed) {
            throw StreamErrors.closed();
        }
        if (max == 0) {
            return new byte[0];
        }
        ByteBuffer buffer = ByteBuffer.allocate(max);
        int read;
        try {
            read = file.channel().read(buffer, position);
        } catch (IOException e) {
            closed = true;
            throw StreamErrors.failed(e);
        }
        if (read < 0) {
            closed = true;
            throw StreamErrors.closed();
        }
        position += read;
        return read == max ? buffer.array() : Arrays.copyOf(buffer.array(), read);
    }

    @Override
    public void drop() {
        if (!dropped) {
            dropped = true;
            file.release();
        }
    }
}
