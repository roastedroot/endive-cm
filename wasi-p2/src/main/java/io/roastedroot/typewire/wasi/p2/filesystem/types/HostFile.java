package io.roastedroot.typewire.wasi.p2.filesystem.types;

import io.roastedroot.typewire.runtime.Tuple2;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.Advice;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.DescriptorFlags;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.DescriptorStat;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.DescriptorType;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.ErrorCode;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.ErrorCodeException;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.MetadataHashValue;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.NewTimestamp;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.io.streams.InputStream;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.io.streams.OutputStream;
import io.roastedroot.typewire.wasi.p2.io.poll.HostPoll;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * A descriptor naming an open file, read and written at an offset given with each call.
 *
 * <p>Reading needs {@code read} and anything changing the file needs {@code write}. Either fails
 * with {@code bad-descriptor} without the flag it needs.
 *
 * <p>Reads and writes go through the open file. Once it no longer has the name it was opened
 * under, its metadata can only be described in part, and its times can no longer be set.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/filesystem/wit/types.wit">wasi:filesystem/types</a>
 */
public final class HostFile extends HostDescriptor {

    /** The most one {@code read} returns, so a guest asking for more cannot exhaust the heap. */
    static final int MAX_READ = 1 << 20;

    /** How far a read tries past the size a file reports, which some files leave at zero. */
    static final int MIN_ATTEMPT = 8 * 1024;

    private final SharedChannel shared;
    private boolean dropped;
    private volatile FileTime lastModified;

    private HostFile(
            Path root,
            Path path,
            DescriptorFlags flags,
            HostPoll poll,
            OpenFiles files,
            FileChannel channel) {
        super(root, path, flags, poll, files);
        this.shared = new SharedChannel(channel, files);
    }

    /**
     * Opens the file {@code path} names, which is never a symbolic link.
     *
     * @param create whether a missing file is created
     * @param exclusive whether an existing file fails with {@code exist} instead
     * @param truncate whether an existing file is emptied
     */
    static HostFile open(
            Path path,
            DescriptorFlags flags,
            HostPoll poll,
            boolean create,
            boolean exclusive,
            boolean truncate) {
        return open(
                path.getParent(), path, flags, poll, new OpenFiles(), create, exclusive, truncate);
    }

    /**
     * Opens the file {@code path} names below the preopened directory {@code root}.
     *
     * @param files where the open file is tracked
     * @param create whether a missing file is created
     * @param exclusive whether an existing file fails with {@code exist} instead
     * @param truncate whether an existing file is emptied
     */
    static HostFile open(
            Path root,
            Path path,
            DescriptorFlags flags,
            HostPoll poll,
            OpenFiles files,
            boolean create,
            boolean exclusive,
            boolean truncate) {
        files.requireOpen();
        Set<OpenOption> options = new HashSet<>();
        options.add(LinkOption.NOFOLLOW_LINKS);
        if (flags.has(DescriptorFlags.Flag.READ)) {
            options.add(StandardOpenOption.READ);
        }
        // The host ignores creating and truncating on a channel it cannot write.
        if (flags.has(DescriptorFlags.Flag.WRITE) || create || truncate) {
            options.add(StandardOpenOption.WRITE);
        }
        if (!options.contains(StandardOpenOption.WRITE)) {
            options.add(StandardOpenOption.READ);
        }
        if (create) {
            options.add(exclusive ? StandardOpenOption.CREATE_NEW : StandardOpenOption.CREATE);
        }
        if (truncate) {
            options.add(StandardOpenOption.TRUNCATE_EXISTING);
        }
        // requested-write-sync needs nothing more, as reads follow writes that are already synced.
        if (flags.has(DescriptorFlags.Flag.FILE_INTEGRITY_SYNC)) {
            options.add(StandardOpenOption.SYNC);
        }
        if (flags.has(DescriptorFlags.Flag.DATA_INTEGRITY_SYNC)) {
            options.add(StandardOpenOption.DSYNC);
        }
        try {
            return new HostFile(root, path, flags, poll, files, FileChannel.open(path, options));
        } catch (IOException | UnsupportedOperationException e) {
            throw ErrorCodes.failed(e);
        }
    }

    @Override
    public DescriptorType getType() {
        return DescriptorType.REGULAR_FILE;
    }

    /**
     * Up to {@code length} bytes from {@code offset}, and whether they reach the end of the file.
     * No more than {@value #MAX_READ} bytes are returned by one call. A read goes past the size the
     * file reports, so a file still growing or one sized zero, as under {@code /proc}, is read.
     *
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/filesystem/wit/types.wit">wasi:filesystem/types, read</a>
     */
    @Override
    public Tuple2<byte[], Boolean> read(BigInteger length, BigInteger offset) {
        require(DescriptorFlags.Flag.READ);
        try {
            FileChannel channel = shared.channel();
            long size = channel.size();
            long position = clamp(offset);
            int wanted = (int) Math.min(clamp(length), MAX_READ);
            if (wanted == 0) {
                return Tuple2.of(new byte[0], position >= size);
            }
            int count = (int) Math.min(wanted, Math.max(size - position, MIN_ATTEMPT));
            ByteBuffer buffer = ByteBuffer.allocate(count);
            boolean end = false;
            while (buffer.hasRemaining() && !end) {
                end = channel.read(buffer, position + buffer.position()) < 0;
            }
            byte[] bytes =
                    buffer.position() == count
                            ? buffer.array()
                            : Arrays.copyOf(buffer.array(), buffer.position());
            return Tuple2.of(bytes, end || (size > 0 && position + bytes.length >= size));
        } catch (IOException e) {
            throw ErrorCodes.failed(e);
        }
    }

    /**
     * Writes all of {@code buffer} at {@code offset}, extending the file when that is past its
     * end.
     *
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/filesystem/wit/types.wit">wasi:filesystem/types, write</a>
     */
    @Override
    public BigInteger write(byte[] buffer, BigInteger offset) {
        require(DescriptorFlags.Flag.WRITE);
        long position = position(offset);
        try {
            shared.writeFully(ByteBuffer.wrap(buffer), position);
        } catch (IOException e) {
            throw ErrorCodes.failed(e);
        }
        return BigInteger.valueOf(buffer.length);
    }

    /**
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/filesystem/wit/types.wit">wasi:filesystem/types, set-size</a>
     */
    @Override
    public void setSize(BigInteger size) {
        require(DescriptorFlags.Flag.WRITE);
        long target = position(size);
        try {
            long current = shared.channel().size();
            if (target < current) {
                shared.channel().truncate(target);
            } else if (target > current) {
                shared.writeFully(ByteBuffer.allocate(1), target - 1);
            }
        } catch (IOException e) {
            throw ErrorCodes.failed(e);
        }
    }

    @Override
    public DescriptorStat stat() {
        BasicFileAttributes attributes = currentAttributes();
        return attributes == null
                ? Metadata.detached(DescriptorType.REGULAR_FILE, size())
                : Metadata.stat(path(), attributes);
    }

    /**
     * A hash of the file as it was opened, so renaming it changes nothing. Once it no longer has
     * its name, the last modification time seen through it takes the place of the current one.
     */
    @Override
    public MetadataHashValue metadataHash() {
        currentAttributes();
        return Metadata.hash(identity(), size(), lastModified);
    }

    /**
     * Sets the times through the name the file was opened under, which fails with {@code
     * no-entry} once the file no longer has it.
     */
    @Override
    public void setTimes(NewTimestamp dataAccessTimestamp, NewTimestamp dataModificationTimestamp) {
        require(DescriptorFlags.Flag.WRITE);
        if (currentAttributes() == null) {
            throw ErrorCodes.error(ErrorCode.NO_ENTRY);
        }
        Metadata.setTimes(path(), dataAccessTimestamp, dataModificationTimestamp);
    }

    /** Accepts any advice and acts on none, which the spec allows. */
    @Override
    public void advise(BigInteger offset, BigInteger length, Advice advice) {}

    @Override
    public void sync() {
        force(true);
    }

    @Override
    public void syncData() {
        force(false);
    }

    /**
     * A stream reading from {@code offset}, which keeps the file open after this descriptor is
     * dropped.
     *
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/filesystem/wit/types.wit">wasi:filesystem/types, read-via-stream</a>
     */
    @Override
    public InputStream readViaStream(BigInteger offset) {
        require(DescriptorFlags.Flag.READ);
        return new HostFileInputStream(poll(), shared, clamp(offset));
    }

    /**
     * A stream writing from {@code offset}, which keeps the file open after this descriptor is
     * dropped.
     *
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/filesystem/wit/types.wit">wasi:filesystem/types, write-via-stream</a>
     */
    @Override
    public OutputStream writeViaStream(BigInteger offset) {
        require(DescriptorFlags.Flag.WRITE);
        return HostFileOutputStream.at(poll(), shared, position(offset));
    }

    /**
     * A stream writing at the end of the file, which keeps the file open after this descriptor is
     * dropped. It writes through a channel of its own opened to append, so each write lands at the
     * end whoever else writes the file. Once the file no longer has its name, it writes at the
     * size it reads first instead.
     *
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/filesystem/wit/types.wit">wasi:filesystem/types, append-via-stream</a>
     */
    @Override
    public OutputStream appendViaStream() {
        require(DescriptorFlags.Flag.WRITE);
        if (currentAttributes() == null) {
            return HostFileOutputStream.appending(poll(), shared);
        }
        Path current = path();
        FileChannel channel =
                ErrorCodes.call(
                        () ->
                                FileChannel.open(
                                        current,
                                        StandardOpenOption.WRITE,
                                        StandardOpenOption.APPEND,
                                        LinkOption.NOFOLLOW_LINKS));
        SharedChannel appender = new SharedChannel(channel, files(), true);
        try {
            return HostFileOutputStream.appending(poll(), appender);
        } finally {
            appender.release();
        }
    }

    /** Lets go of the file, which closes once no stream opened from it is left either. */
    @Override
    public void drop() {
        if (!dropped) {
            dropped = true;
            shared.release();
        }
    }

    /**
     * The attributes of this file under the name it was opened with, or {@code null} once it was
     * renamed, removed or replaced.
     */
    private BasicFileAttributes currentAttributes() {
        BasicFileAttributes attributes;
        Path current;
        try {
            current = path();
            attributes = Metadata.attributesOrNull(current);
        } catch (ErrorCodeException e) {
            return null;
        }
        if (attributes == null || !identity().equals(Metadata.identity(current, attributes))) {
            return null;
        }
        lastModified = attributes.lastModifiedTime();
        return attributes;
    }

    private long size() {
        try {
            return shared.channel().size();
        } catch (IOException e) {
            throw ErrorCodes.failed(e);
        }
    }

    private void force(boolean metadata) {
        if (!getFlags().has(DescriptorFlags.Flag.WRITE)) {
            return;
        }
        try {
            shared.channel().force(metadata);
        } catch (IOException e) {
            throw ErrorCodes.failed(e);
        }
    }

    private void require(DescriptorFlags.Flag flag) {
        if (!getFlags().has(flag)) {
            throw ErrorCodes.error(ErrorCode.BAD_DESCRIPTOR);
        }
    }

    /** {@code value} as a {@code long}, saturating where it does not fit. */
    private static long clamp(BigInteger value) {
        return value.bitLength() < Long.SIZE ? value.longValue() : Long.MAX_VALUE;
    }

    /** {@code offset} as a position the host can write at, or {@code file-too-large}. */
    private static long position(BigInteger offset) {
        if (offset.bitLength() >= Long.SIZE) {
            throw ErrorCodes.error(ErrorCode.FILE_TOO_LARGE);
        }
        return offset.longValue();
    }
}
