package io.roastedroot.typewire.wasi.p2.filesystem.types;

import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.ErrorCode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The files guests hold open through one host's directories, so closing the host closes them too.
 * A guest using one afterwards, or opening another, fails with {@code bad-descriptor}.
 */
public final class OpenFiles implements AutoCloseable {

    private final ReentrantLock lock = new ReentrantLock();
    private final Set<SharedChannel> open = new HashSet<>();
    private boolean closed;

    /** Tracks {@code channel}, which is closed at once when the host already is. */
    void add(SharedChannel channel) {
        lock.lock();
        try {
            if (!closed) {
                open.add(channel);
                return;
            }
        } finally {
            lock.unlock();
        }
        closeQuietly(channel);
        throw ErrorCodes.error(ErrorCode.BAD_DESCRIPTOR);
    }

    /** Fails with {@code bad-descriptor} once the host is closed, before anything is opened. */
    void requireOpen() {
        lock.lock();
        try {
            if (closed) {
                throw ErrorCodes.error(ErrorCode.BAD_DESCRIPTOR);
            }
        } finally {
            lock.unlock();
        }
    }

    void remove(SharedChannel channel) {
        lock.lock();
        try {
            open.remove(channel);
        } finally {
            lock.unlock();
        }
    }

    /** Closes every file a guest left open, and every one opened from now on. */
    @Override
    public void close() {
        List<SharedChannel> left;
        lock.lock();
        try {
            closed = true;
            left = new ArrayList<>(open);
            open.clear();
        } finally {
            lock.unlock();
        }
        left.forEach(OpenFiles::closeQuietly);
    }

    private static void closeQuietly(SharedChannel channel) {
        try {
            channel.channel().close();
        } catch (IOException e) {
            // Nothing is left that could observe the failure.
        }
    }
}
