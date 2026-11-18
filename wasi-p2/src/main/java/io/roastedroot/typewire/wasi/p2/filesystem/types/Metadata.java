package io.roastedroot.typewire.wasi.p2.filesystem.types;

import io.roastedroot.typewire.wasi.p2.bindings.wasi.clocks.wallclock.Datetime;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.DescriptorStat;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.DescriptorType;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.ErrorCode;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.MetadataHashValue;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.NewTimestamp;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;

/**
 * Reads and adjusts the metadata of a host path, never following a symbolic link it names.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/filesystem/wit/types.wit">wasi:filesystem/types</a>
 */
final class Metadata {

    private static final LinkOption[] NOFOLLOW = {LinkOption.NOFOLLOW_LINKS};

    /** Mixed into every hash, so its inputs cannot be recovered from it. */
    private static final byte[] SECRET = secret();

    private Metadata() {}

    private static byte[] secret() {
        byte[] secret = new byte[16];
        new SecureRandom().nextBytes(secret);
        return secret;
    }

    /** The attributes of {@code path}. */
    static DescriptorStat stat(Path path) {
        return stat(path, attributes(path));
    }

    /** The attributes of {@code path}, whose basic ones are already read. */
    static DescriptorStat stat(Path path, BasicFileAttributes attributes) {
        Map<String, Object> unix = unixAttributes(path);
        Object links = unix.get("nlink");
        Object changed = unix.get("ctime");
        return new DescriptorStat(
                type(attributes),
                BigInteger.valueOf(links instanceof Number ? ((Number) links).longValue() : 1),
                BigInteger.valueOf(attributes.size()),
                datetime(attributes.lastAccessTime()),
                datetime(attributes.lastModifiedTime()),
                datetime(changed instanceof FileTime ? (FileTime) changed : null));
    }

    /** The attributes of {@code path} itself, or {@code null} when nothing is there. */
    static BasicFileAttributes attributesOrNull(Path path) {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class, NOFOLLOW);
        } catch (NoSuchFileException e) {
            return null;
        } catch (IOException | UnsupportedOperationException e) {
            throw ErrorCodes.failed(e);
        }
    }

    /**
     * The type and size of an open file that no longer has the name it was opened under, which is
     * all the host can still tell about it.
     */
    static DescriptorStat detached(DescriptorType type, long size) {
        return new DescriptorStat(
                type, BigInteger.ZERO, BigInteger.valueOf(size), null, null, null);
    }

    /**
     * What identifies the object {@code path} names, or {@code null} when it cannot be read.
     *
     * @see #identity(Path, BasicFileAttributes)
     */
    static Object fileKey(Path path) {
        try {
            return identity(path, Files.readAttributes(path, BasicFileAttributes.class, NOFOLLOW));
        } catch (IOException | UnsupportedOperationException e) {
            return null;
        }
    }

    /**
     * What identifies the object {@code path} names. Where the host has no file key, its absolute
     * path and creation time stand in, so a replacement under the same name differs.
     */
    static Object identity(Path path, BasicFileAttributes attributes) {
        Object fileKey = attributes.fileKey();
        return fileKey == null
                ? Arrays.asList(path.toAbsolutePath(), attributes.creationTime())
                : fileKey;
    }

    /** The type of the object {@code path} names. */
    static DescriptorType type(Path path) {
        return type(attributes(path));
    }

    static DescriptorType type(BasicFileAttributes attributes) {
        if (attributes.isDirectory()) {
            return DescriptorType.DIRECTORY;
        }
        if (attributes.isRegularFile()) {
            return DescriptorType.REGULAR_FILE;
        }
        if (attributes.isSymbolicLink()) {
            return DescriptorType.SYMBOLIC_LINK;
        }
        return DescriptorType.UNKNOWN;
    }

    /**
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/filesystem/wit/types.wit">wasi:filesystem/types, set-times</a>
     */
    static void setTimes(Path path, NewTimestamp dataAccess, NewTimestamp dataModification) {
        FileTime modified = fileTime(dataModification);
        FileTime accessed = fileTime(dataAccess);
        ErrorCodes.run(
                () ->
                        Files.getFileAttributeView(path, BasicFileAttributeView.class, NOFOLLOW)
                                .setTimes(modified, accessed, null));
    }

    /**
     * A hash of what changes when {@code path} is modified or replaced.
     *
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/filesystem/wit/types.wit">wasi:filesystem/types, metadata-hash</a>
     */
    static MetadataHashValue hash(Path path) {
        BasicFileAttributes attributes = attributes(path);
        return hash(identity(path, attributes), attributes.size(), attributes.lastModifiedTime());
    }

    /**
     * A hash of an object's identity, size and last modification, which is {@code null} when it
     * is not known.
     */
    static MetadataHashValue hash(Object identity, long size, FileTime modifiedTime) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every Java platform provides SHA-256", e);
        }
        digest.update(SECRET);
        digest.update(String.valueOf(identity).getBytes(StandardCharsets.UTF_8));
        Instant modified = modifiedTime == null ? Instant.EPOCH : modifiedTime.toInstant();
        digest.update(
                ByteBuffer.allocate(Long.BYTES * 3)
                        .putLong(size)
                        .putLong(modified.getEpochSecond())
                        .putLong(modified.getNano())
                        .array());
        byte[] hashed = digest.digest();
        return new MetadataHashValue(
                new BigInteger(1, Arrays.copyOfRange(hashed, 0, 8)),
                new BigInteger(1, Arrays.copyOfRange(hashed, 8, 16)));
    }

    private static BasicFileAttributes attributes(Path path) {
        return ErrorCodes.call(
                () -> Files.readAttributes(path, BasicFileAttributes.class, NOFOLLOW));
    }

    /**
     * The hard link count and status change time of {@code path}, which are empty where the host
     * has no {@code unix} view. A missing link count stands for one link.
     */
    private static Map<String, Object> unixAttributes(Path path) {
        try {
            return Files.readAttributes(path, "unix:nlink,ctime", NOFOLLOW);
        } catch (UnsupportedOperationException | IllegalArgumentException e) {
            return Map.of();
        } catch (IOException e) {
            throw ErrorCodes.failed(e);
        }
    }

    /** {@code time} as a {@code datetime}, which cannot represent a time before the epoch. */
    private static Datetime datetime(FileTime time) {
        if (time == null) {
            return null;
        }
        Instant instant = time.toInstant();
        if (instant.isBefore(Instant.EPOCH)) {
            return null;
        }
        return new Datetime(BigInteger.valueOf(instant.getEpochSecond()), (long) instant.getNano());
    }

    /** The time a {@code new-timestamp} sets, or {@code null} to leave it unchanged. */
    private static FileTime fileTime(NewTimestamp timestamp) {
        if (timestamp instanceof NewTimestamp.Now) {
            return FileTime.from(Instant.now());
        }
        if (timestamp instanceof NewTimestamp.Timestamp) {
            Datetime value = ((NewTimestamp.Timestamp) timestamp).value();
            if (value.seconds().bitLength() >= Long.SIZE) {
                throw ErrorCodes.error(ErrorCode.OVERFLOW);
            }
            if (value.nanoseconds() >= 1_000_000_000L) {
                throw ErrorCodes.error(ErrorCode.INVALID);
            }
            try {
                return FileTime.from(
                        Instant.ofEpochSecond(value.seconds().longValue(), value.nanoseconds()));
            } catch (DateTimeException e) {
                throw ErrorCodes.error(ErrorCode.OVERFLOW);
            }
        }
        return null;
    }
}
