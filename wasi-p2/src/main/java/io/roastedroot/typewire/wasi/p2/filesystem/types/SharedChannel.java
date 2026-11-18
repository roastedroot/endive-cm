package io.roastedroot.typewire.wasi.p2.filesystem.types;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An open file shared by a descriptor and the streams opened from it, closed once every one of
 * them has let go. A stream may outlive the descriptor that opened it.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/filesystem/wit/types.wit">wasi:filesystem/types</a>
 */
final class SharedChannel {

    private final FileChannel channel;
    private final OpenFiles files;
    private final boolean appending;
    private final AtomicInteger holders = new AtomicInteger(1);

    /** A channel held by its first holder, which {@code files} closes if nothing else does. */
    SharedChannel(FileChannel channel, OpenFiles files) {
        this(channel, files, false);
    }

    /**
     * A channel held by its first holder, which {@code files} closes if nothing else does.
     *
     * @param appending whether the channel was opened to append
     */
    SharedChannel(FileChannel channel, OpenFiles files, boolean appending) {
        this.channel = channel;
        this.files = files;
        this.appending = appending;
        files.add(this);
    }

    FileChannel channel() {
        return channel;
    }

    /** Writes all of {@code contents} from {@code position}, which a channel may take in parts. */
    void writeFully(ByteBuffer contents, long position) throws IOException {
        while (contents.hasRemaining()) {
            channel.write(contents, position + contents.position());
        }
    }

    /**
     * Writes all of {@code contents} at the end of the file. A channel opened to append puts each
     * write there whoever else writes the file, and any other first reads where the end is.
     */
    void append(ByteBuffer contents) throws IOException {
        if (!appending) {
            writeFully(contents, channel.size());
            return;
        }
        while (contents.hasRemaining()) {
            channel.write(contents);
        }
    }

    /** Adds a holder, which has to {@link #release} it once. */
    SharedChannel retain() {
        holders.incrementAndGet();
        return this;
    }

    /** Lets go of the channel, closing it when no holder is left. */
    void release() {
        if (holders.decrementAndGet() == 0) {
            files.remove(this);
            try {
                channel.close();
            } catch (IOException e) {
                // Nothing is left that could observe the failure.
            }
        }
    }
}
