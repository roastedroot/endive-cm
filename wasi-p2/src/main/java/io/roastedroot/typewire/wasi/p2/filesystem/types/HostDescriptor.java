package io.roastedroot.typewire.wasi.p2.filesystem.types;

import io.roastedroot.typewire.runtime.Tuple2;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.Advice;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.Descriptor;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.DescriptorFlags;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.DescriptorStat;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.DirectoryEntryStream;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.ErrorCode;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.ErrorCodeException;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.MetadataHashValue;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.NewTimestamp;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.OpenFlags;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.PathFlags;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.io.streams.InputStream;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.io.streams.OutputStream;
import io.roastedroot.typewire.wasi.p2.io.poll.HostPoll;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Objects;

/**
 * The host side of the {@code wasi:filesystem} {@code descriptor} resource, naming an object on a
 * host {@link java.nio.file.FileSystem}.
 *
 * <p>A {@link HostDirectory} refuses what only a file supports with {@code bad-descriptor}, and a
 * {@link HostFile} refuses what only a directory supports with {@code not-directory}.
 *
 * <p>Java holds no handle to a directory, so a descriptor keeps where it was opened below its
 * preopened directory and checks that location again on every use.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/filesystem/wit/types.wit">wasi:filesystem/types</a>
 */
public abstract class HostDescriptor implements Descriptor {

    private final Path root;
    private final Path path;
    private final DescriptorFlags flags;
    private final HostPoll poll;
    private final OpenFiles files;
    private final Object fileKey;

    /**
     * @param root the preopened directory holding {@code path}
     * @param path the host object this descriptor names
     * @param flags the access the guest is granted to it
     * @param poll the poll behind the subscriptions of the streams this opens
     * @param files where the files opened through this descriptor are tracked
     */
    protected HostDescriptor(
            Path root, Path path, DescriptorFlags flags, HostPoll poll, OpenFiles files) {
        this.root = Objects.requireNonNull(root, "root");
        this.path = Objects.requireNonNull(path, "path");
        this.flags = Objects.requireNonNull(flags, "flags");
        this.poll = Objects.requireNonNull(poll, "poll");
        this.files = Objects.requireNonNull(files, "files");
        this.fileKey = Metadata.fileKey(path);
    }

    HostPoll poll() {
        return poll;
    }

    Path root() {
        return root;
    }

    OpenFiles files() {
        return files;
    }

    /** What identifies the object on its file system, or {@code null} where the host has none. */
    Object fileKey() {
        return fileKey;
    }

    /** The object as it was opened, by its file key, or by its absolute path when unreadable. */
    Object identity() {
        return fileKey == null ? path.toAbsolutePath() : fileKey;
    }

    /**
     * The host object this descriptor names, once its location is checked again.
     *
     * @throws ErrorCodeException {@code no-entry} once something else has taken its place
     */
    public Path path() {
        return PathResolver.descend(root, path);
    }

    @Override
    public DescriptorFlags getFlags() {
        return flags;
    }

    @Override
    public DescriptorStat stat() {
        return Metadata.stat(path());
    }

    @Override
    public MetadataHashValue metadataHash() {
        return Metadata.hash(path());
    }

    /** Compares the objects as they were opened, so renaming either does not change the answer. */
    @Override
    public Boolean isSameObject(Descriptor other) {
        if (!(other instanceof HostDescriptor)) {
            return false;
        }
        return identity().equals(((HostDescriptor) other).identity());
    }

    /** Succeeds with no effect, as syncing a descriptor not open for writing does. */
    @Override
    public void sync() {}

    /** Succeeds with no effect, as syncing a descriptor not open for writing does. */
    @Override
    public void syncData() {}

    @Override
    public InputStream readViaStream(BigInteger offset) {
        throw notAFile();
    }

    @Override
    public OutputStream writeViaStream(BigInteger offset) {
        throw notAFile();
    }

    @Override
    public OutputStream appendViaStream() {
        throw notAFile();
    }

    @Override
    public void advise(BigInteger offset, BigInteger length, Advice advice) {
        throw notAFile();
    }

    @Override
    public void setSize(BigInteger size) {
        throw notAFile();
    }

    @Override
    public Tuple2<byte[], Boolean> read(BigInteger length, BigInteger offset) {
        throw notAFile();
    }

    @Override
    public BigInteger write(byte[] buffer, BigInteger offset) {
        throw notAFile();
    }

    @Override
    public DirectoryEntryStream readDirectory() {
        throw notADirectory();
    }

    @Override
    public void createDirectoryAt(String path) {
        throw notADirectory();
    }

    @Override
    public DescriptorStat statAt(PathFlags pathFlags, String path) {
        throw notADirectory();
    }

    @Override
    public void setTimesAt(
            PathFlags pathFlags,
            String path,
            NewTimestamp dataAccessTimestamp,
            NewTimestamp dataModificationTimestamp) {
        throw notADirectory();
    }

    @Override
    public void linkAt(
            PathFlags oldPathFlags, String oldPath, Descriptor newDescriptor, String newPath) {
        throw notADirectory();
    }

    @Override
    public Descriptor openAt(
            PathFlags pathFlags, String path, OpenFlags openFlags, DescriptorFlags flags) {
        throw notADirectory();
    }

    @Override
    public String readlinkAt(String path) {
        throw notADirectory();
    }

    @Override
    public void removeDirectoryAt(String path) {
        throw notADirectory();
    }

    @Override
    public void renameAt(String oldPath, Descriptor newDescriptor, String newPath) {
        throw notADirectory();
    }

    @Override
    public void symlinkAt(String oldPath, String newPath) {
        throw notADirectory();
    }

    @Override
    public void unlinkFileAt(String path) {
        throw notADirectory();
    }

    @Override
    public MetadataHashValue metadataHashAt(PathFlags pathFlags, String path) {
        throw notADirectory();
    }

    private static ErrorCodeException notAFile() {
        return ErrorCodes.error(ErrorCode.BAD_DESCRIPTOR);
    }

    private static ErrorCodeException notADirectory() {
        return ErrorCodes.error(ErrorCode.NOT_DIRECTORY);
    }
}
