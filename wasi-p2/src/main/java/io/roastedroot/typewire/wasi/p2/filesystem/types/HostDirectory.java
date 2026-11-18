package io.roastedroot.typewire.wasi.p2.filesystem.types;

import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.Descriptor;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.DescriptorFlags;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.DescriptorStat;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.DescriptorType;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.DirectoryEntry;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.DirectoryEntryStream;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.ErrorCode;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.ErrorCodeException;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.MetadataHashValue;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.NewTimestamp;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.OpenFlags;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.PathFlags;
import io.roastedroot.typewire.wasi.p2.io.poll.HostPoll;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * A descriptor naming a directory, against which every path a guest passes is resolved without
 * reaching outside it.
 *
 * <p>Creating, removing, renaming or changing the metadata of anything through it needs {@code
 * mutate-directory}, and fails with {@code read-only} without it.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/filesystem/wit/types.wit">wasi:filesystem/types</a>
 */
public final class HostDirectory extends HostDescriptor {

    private static final LinkOption NOFOLLOW = LinkOption.NOFOLLOW_LINKS;

    /**
     * A preopened directory, the bound no path resolved through it or its descendants leaves.
     *
     * @param path the host directory this descriptor names
     * @param flags the access the guest is granted to it
     * @param poll the poll behind the subscriptions of streams of the files it opens
     */
    HostDirectory(Path path, DescriptorFlags flags, HostPoll poll) {
        this(path, flags, poll, new OpenFiles());
    }

    /**
     * A preopened directory whose open files {@code files} tracks.
     *
     * @param path the host directory this descriptor names
     * @param flags the access the guest is granted to it
     * @param poll the poll behind the subscriptions of streams of the files it opens
     * @param files where the files opened through it are tracked
     */
    public HostDirectory(Path path, DescriptorFlags flags, HostPoll poll, OpenFiles files) {
        this(path, path, flags, poll, files);
    }

    private HostDirectory(
            Path root, Path path, DescriptorFlags flags, HostPoll poll, OpenFiles files) {
        super(root, path, flags, poll, files);
    }

    @Override
    public DescriptorType getType() {
        return DescriptorType.DIRECTORY;
    }

    @Override
    public void setTimes(NewTimestamp dataAccessTimestamp, NewTimestamp dataModificationTimestamp) {
        requireMutate();
        Metadata.setTimes(path(), dataAccessTimestamp, dataModificationTimestamp);
    }

    /** The entries as they stand now, since a guest may read them long after asking. */
    @Override
    public DirectoryEntryStream readDirectory() {
        Path directory = path();
        List<DirectoryEntry> entries = new ArrayList<>();
        ErrorCodes.run(
                () -> {
                    try (DirectoryStream<Path> listed = Files.newDirectoryStream(directory)) {
                        for (Path entry : listed) {
                            DescriptorType type = typeOrNull(entry);
                            if (type != null) {
                                entries.add(
                                        new DirectoryEntry(type, entry.getFileName().toString()));
                            }
                        }
                    }
                });
        return new HostDirectoryEntryStream(entries);
    }

    /** The type of an entry, or {@code null} when it was removed after it was listed. */
    private static DescriptorType typeOrNull(Path entry) {
        try {
            return Metadata.type(entry);
        } catch (ErrorCodeException e) {
            if (e.error() == ErrorCode.NO_ENTRY) {
                return null;
            }
            throw e;
        }
    }

    @Override
    public void createDirectoryAt(String path) {
        requireMutate();
        Path target = resolve(false, path);
        ErrorCodes.run(() -> Files.createDirectory(target));
    }

    @Override
    public DescriptorStat statAt(PathFlags pathFlags, String path) {
        return Metadata.stat(resolve(pathFlags, path));
    }

    @Override
    public void setTimesAt(
            PathFlags pathFlags,
            String path,
            NewTimestamp dataAccessTimestamp,
            NewTimestamp dataModificationTimestamp) {
        requireMutate();
        Metadata.setTimes(resolve(pathFlags, path), dataAccessTimestamp, dataModificationTimestamp);
    }

    /**
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/filesystem/wit/types.wit">wasi:filesystem/types, link-at</a>
     */
    @Override
    public void linkAt(
            PathFlags oldPathFlags, String oldPath, Descriptor newDescriptor, String newPath) {
        HostDirectory target = directory(newDescriptor);
        requireMutate();
        target.requireMutate();
        Path existing = resolve(oldPathFlags, oldPath);
        Path link = target.resolve(false, newPath);
        requireSameFileSystem(existing, link);
        if (!Files.exists(existing, NOFOLLOW)) {
            throw ErrorCodes.error(ErrorCode.NO_ENTRY);
        }
        if (Files.isDirectory(existing, NOFOLLOW)) {
            throw ErrorCodes.error(ErrorCode.NOT_PERMITTED);
        }
        // Some hosts link what a symbolic link points to, which could be outside.
        if (Files.isSymbolicLink(existing)) {
            throw ErrorCodes.error(ErrorCode.UNSUPPORTED);
        }
        ErrorCodes.run(() -> Files.createLink(link, existing));
    }

    /**
     * Opens a directory as another {@code HostDirectory} and a regular file as a {@link HostFile}.
     * A symbolic link left unfollowed fails with {@code loop}, as {@code O_NOFOLLOW} does, and an
     * exclusive create fails with {@code exist} whenever the path names a link, as {@code O_EXCL}
     * does. Anything
     * else, such as a named pipe or a device, fails with {@code unsupported}, since a file is read
     * and written at an offset and opening a pipe would block the guest.
     *
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/filesystem/wit/types.wit">wasi:filesystem/types, open-at</a>
     */
    @Override
    public Descriptor openAt(
            PathFlags pathFlags, String path, OpenFlags openFlags, DescriptorFlags flags) {
        boolean create = openFlags.has(OpenFlags.Flag.CREATE);
        boolean exclusive = openFlags.has(OpenFlags.Flag.EXCLUSIVE);
        boolean truncate = openFlags.has(OpenFlags.Flag.TRUNCATE);
        boolean directoryOnly = openFlags.has(OpenFlags.Flag.DIRECTORY);
        if (flags.has(DescriptorFlags.Flag.WRITE)
                || flags.has(DescriptorFlags.Flag.MUTATE_DIRECTORY)
                || create
                || truncate) {
            requireMutate();
        }
        if (create && directoryOnly) {
            throw ErrorCodes.error(ErrorCode.INVALID);
        }
        Path target = resolve(pathFlags, path);
        if (create && exclusive) {
            Path named =
                    pathFlags.has(PathFlags.Flag.SYMLINK_FOLLOW) ? resolve(false, path) : target;
            if (Metadata.attributesOrNull(named) != null) {
                throw ErrorCodes.error(ErrorCode.EXIST);
            }
        }
        BasicFileAttributes attributes = Metadata.attributesOrNull(target);
        if (attributes == null) {
            if (path.endsWith("/")) {
                throw ErrorCodes.error(create ? ErrorCode.IS_DIRECTORY : ErrorCode.NO_ENTRY);
            }
            if (directoryOnly) {
                throw ErrorCodes.error(ErrorCode.NO_ENTRY);
            }
        } else if (attributes.isSymbolicLink()) {
            throw ErrorCodes.error(ErrorCode.LOOP);
        } else if (attributes.isDirectory()) {
            if (flags.has(DescriptorFlags.Flag.WRITE) || create || truncate) {
                throw ErrorCodes.error(ErrorCode.IS_DIRECTORY);
            }
            return new HostDirectory(root(), target, flags, poll(), files());
        } else if (directoryOnly) {
            throw ErrorCodes.error(ErrorCode.NOT_DIRECTORY);
        } else if (!attributes.isRegularFile()) {
            throw ErrorCodes.error(ErrorCode.UNSUPPORTED);
        }
        return HostFile.open(
                root(), target, fileFlags(flags), poll(), files(), create, exclusive, truncate);
    }

    /** What a guest asked for, apart from {@code mutate-directory}, which only a directory has. */
    private static DescriptorFlags fileFlags(DescriptorFlags flags) {
        return DescriptorFlags.of(
                flags.flags().stream()
                        .filter(flag -> flag != DescriptorFlags.Flag.MUTATE_DIRECTORY)
                        .toArray(DescriptorFlags.Flag[]::new));
    }

    @Override
    public String readlinkAt(String path) {
        Path link = resolve(false, path);
        return PathResolver.guestPath(ErrorCodes.call(() -> Files.readSymbolicLink(link)));
    }

    @Override
    public void removeDirectoryAt(String path) {
        requireMutate();
        Path target = resolve(false, path);
        if (target.equals(path())) {
            throw ErrorCodes.error(ErrorCode.INVALID);
        }
        if (Files.exists(target, NOFOLLOW) && !Files.isDirectory(target, NOFOLLOW)) {
            throw ErrorCodes.error(ErrorCode.NOT_DIRECTORY);
        }
        delete(target);
    }

    /**
     * Replaces whatever the new path names in one atomic step, as POSIX {@code rename} does,
     * provided both name a directory or neither does.
     *
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/filesystem/wit/types.wit">wasi:filesystem/types, rename-at</a>
     */
    @Override
    public void renameAt(String oldPath, Descriptor newDescriptor, String newPath) {
        HostDirectory target = directory(newDescriptor);
        requireMutate();
        target.requireMutate();
        Path from = resolve(false, oldPath);
        Path to = target.resolve(false, newPath);
        requireSameFileSystem(from, to);
        if (from.equals(path()) || to.equals(target.path())) {
            throw ErrorCodes.error(ErrorCode.INVALID);
        }
        BasicFileAttributes source = Metadata.attributesOrNull(from);
        if (source == null) {
            throw ErrorCodes.error(ErrorCode.NO_ENTRY);
        }
        boolean fromDirectory = source.isDirectory();
        BasicFileAttributes replaced = Metadata.attributesOrNull(to);
        if (replaced != null) {
            boolean toDirectory = replaced.isDirectory();
            if (fromDirectory && !toDirectory) {
                throw ErrorCodes.error(ErrorCode.NOT_DIRECTORY);
            }
            if (!fromDirectory && toDirectory) {
                throw ErrorCodes.error(ErrorCode.IS_DIRECTORY);
            }
        }
        if (from.equals(to)) {
            return;
        }
        if (fromDirectory && to.startsWith(from)) {
            throw ErrorCodes.error(ErrorCode.INVALID);
        }
        // Without ATOMIC_MOVE the JDK deletes the target first, and copies across devices.
        ErrorCodes.run(
                () ->
                        Files.move(
                                from,
                                to,
                                StandardCopyOption.ATOMIC_MOVE,
                                StandardCopyOption.REPLACE_EXISTING));
    }

    /**
     * The link is created as written, so a target reaching outside this directory is refused
     * only when something resolves through the link.
     *
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/filesystem/wit/types.wit">wasi:filesystem/types, symlink-at</a>
     */
    @Override
    public void symlinkAt(String oldPath, String newPath) {
        requireMutate();
        Path target = PathResolver.relative(path().getFileSystem(), oldPath);
        Path link = resolve(false, newPath);
        ErrorCodes.run(() -> Files.createSymbolicLink(link, target));
    }

    @Override
    public void unlinkFileAt(String path) {
        requireMutate();
        Path target = resolve(false, path);
        if (Files.isDirectory(target, NOFOLLOW)) {
            throw ErrorCodes.error(ErrorCode.IS_DIRECTORY);
        }
        delete(target);
    }

    @Override
    public MetadataHashValue metadataHashAt(PathFlags pathFlags, String path) {
        return Metadata.hash(resolve(pathFlags, path));
    }

    private Path resolve(PathFlags pathFlags, String path) {
        return resolve(pathFlags.has(PathFlags.Flag.SYMLINK_FOLLOW), path);
    }

    private Path resolve(boolean followFinal, String path) {
        return PathResolver.resolve(path(), path, followFinal);
    }

    private void requireMutate() {
        if (!getFlags().has(DescriptorFlags.Flag.MUTATE_DIRECTORY)) {
            throw ErrorCodes.error(ErrorCode.READ_ONLY);
        }
    }

    /** The directory another descriptor argument names. */
    private static HostDirectory directory(Descriptor descriptor) {
        if (descriptor instanceof HostDirectory) {
            return (HostDirectory) descriptor;
        }
        throw ErrorCodes.error(
                descriptor instanceof HostDescriptor
                        ? ErrorCode.NOT_DIRECTORY
                        : ErrorCode.BAD_DESCRIPTOR);
    }

    private static void requireSameFileSystem(Path first, Path second) {
        if (!first.getFileSystem().equals(second.getFileSystem())) {
            throw ErrorCodes.error(ErrorCode.CROSS_DEVICE);
        }
    }

    private static void delete(Path path) {
        ErrorCodes.run(() -> Files.delete(path));
    }
}
