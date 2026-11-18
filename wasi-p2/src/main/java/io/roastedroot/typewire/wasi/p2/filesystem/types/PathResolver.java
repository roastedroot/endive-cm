package io.roastedroot.typewire.wasi.p2.filesystem.types;

import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.ErrorCode;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.ErrorCodeException;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.StringJoiner;

/**
 * Resolves a guest path against the directory a descriptor names, without ever reaching outside
 * it.
 *
 * <p>Each component is resolved in turn, and every symbolic link is followed here rather than by
 * the host file system. The resolved path therefore holds no symbolic link below the base
 * directory, except a final one left unfollowed.
 *
 * <p>The host operation that follows a resolution is a separate step, which Java offers no way
 * to bind to the directories checked here. Something else that swaps a component for a symbolic
 * link in between can therefore lead that operation outside. A preopened directory must only
 * change through its guest, or live on a file system nothing else can reach.
 *
 * @see <a href="https://github.com/WebAssembly/wasi-filesystem/blob/main/path-resolution.md">WASI filesystem path resolution</a>
 */
final class PathResolver {

    /** The symbolic link expansions one resolution may take, which is what Linux allows. */
    static final int MAX_SYMLINK_EXPANSIONS = 40;

    private static final LinkOption NOFOLLOW = LinkOption.NOFOLLOW_LINKS;

    private PathResolver() {}

    /**
     * {@code path}, where a descriptor was opened below {@code root}, once each component between
     * them is checked to still be a directory and the last one not to be a symbolic link.
     *
     * @throws ErrorCodeException {@code no-entry} once something else has taken the place of a
     *     component, such as a symbolic link that would lead outside
     */
    static Path descend(Path root, Path path) {
        Path relative = root.relativize(path);
        if (relative.toString().isEmpty()) {
            return path;
        }
        Path current = root;
        int count = relative.getNameCount();
        for (int i = 0; i < count; i++) {
            current = current.resolve(relative.getName(i));
            boolean moved =
                    i == count - 1
                            ? Files.isSymbolicLink(current)
                            : !Files.isDirectory(current, NOFOLLOW);
            if (moved) {
                throw ErrorCodes.error(ErrorCode.NO_ENTRY);
            }
        }
        return path;
    }

    /**
     * The host path {@code path} names within {@code directory}.
     *
     * <p>A component followed by more of the path must name a directory, as in POSIX, so {@code
     * file/..} fails rather than naming the directory holding {@code file}. A trailing slash
     * requires the final component to be a directory if it exists, and follows a symbolic link
     * there.
     *
     * @param followFinal whether a symbolic link in the final component is followed
     * @throws ErrorCodeException {@code not-permitted} for a path that is absolute or reaches
     *     outside {@code directory}, {@code loop} for too many symbolic links, {@code no-entry}
     *     for an empty path or a missing directory on the way, {@code not-directory} for anything
     *     else on the way, and {@code invalid} for a component the host cannot represent
     */
    static Path resolve(Path directory, String path, boolean followFinal) {
        if (path.isEmpty()) {
            throw ErrorCodes.error(ErrorCode.NO_ENTRY);
        }
        if (path.startsWith("/")) {
            throw ErrorCodes.error(ErrorCode.NOT_PERMITTED);
        }
        // A trailing slash leaves an empty final component, so a link before it is followed.
        Deque<String> pending = new ArrayDeque<>(List.of(path.split("/", -1)));
        List<String> resolved = new ArrayList<>();
        int expansions = 0;
        while (!pending.isEmpty()) {
            String name = pending.removeFirst();
            if (name.isEmpty() || name.equals(".")) {
                continue;
            }
            if (name.equals("..")) {
                if (resolved.isEmpty()) {
                    throw ErrorCodes.error(ErrorCode.NOT_PERMITTED);
                }
                resolved.remove(resolved.size() - 1);
                continue;
            }
            Path candidate =
                    join(directory, resolved).resolve(component(directory.getFileSystem(), name));
            BasicFileAttributes attributes = Metadata.attributesOrNull(candidate);
            boolean link = attributes != null && attributes.isSymbolicLink();
            if (link && (followFinal || !pending.isEmpty())) {
                if (++expansions > MAX_SYMLINK_EXPANSIONS) {
                    throw ErrorCodes.error(ErrorCode.LOOP);
                }
                List<String> target = linkTarget(candidate);
                for (int i = target.size() - 1; i >= 0; i--) {
                    pending.addFirst(target.get(i));
                }
                continue;
            }
            if (!pending.isEmpty()) {
                requireDirectory(attributes, pending);
            }
            resolved.add(name);
        }
        return join(directory, resolved);
    }

    /**
     * Refuses a component that the rest of the path goes through, unless it is a directory. Only
     * a final component followed by nothing but slashes may be missing, so it can be created.
     */
    private static void requireDirectory(BasicFileAttributes attributes, Deque<String> pending) {
        if (attributes == null) {
            if (pending.stream().allMatch(String::isEmpty)) {
                return;
            }
            throw ErrorCodes.error(ErrorCode.NO_ENTRY);
        }
        if (!attributes.isDirectory()) {
            throw ErrorCodes.error(ErrorCode.NOT_DIRECTORY);
        }
    }

    /**
     * A guest's relative path as a host path on {@code fileSystem}, for a symbolic link to hold.
     * Nothing is resolved, so a {@code ..} is kept as it is.
     *
     * @throws ErrorCodeException {@code not-permitted} for an absolute path, and {@code no-entry}
     *     for an empty one
     */
    static Path relative(FileSystem fileSystem, String path) {
        if (path.isEmpty()) {
            throw ErrorCodes.error(ErrorCode.NO_ENTRY);
        }
        if (path.startsWith("/")) {
            throw ErrorCodes.error(ErrorCode.NOT_PERMITTED);
        }
        Path relative = null;
        for (String name : path.split("/")) {
            if (name.isEmpty()) {
                continue;
            }
            Path next = component(fileSystem, name);
            relative = relative == null ? next : relative.resolve(next);
        }
        return relative == null ? fileSystem.getPath(".") : relative;
    }

    /**
     * A symbolic link's target as the guest writes it, with {@code /} separating components.
     *
     * @throws ErrorCodeException {@code not-permitted} for an absolute or rooted target
     */
    static String guestPath(Path target) {
        if (target.getRoot() != null) {
            throw ErrorCodes.error(ErrorCode.NOT_PERMITTED);
        }
        StringJoiner joined = new StringJoiner("/");
        for (Path name : target) {
            joined.add(name.toString());
        }
        return joined.toString();
    }

    private static Path join(Path directory, List<String> names) {
        Path joined = directory;
        for (String name : names) {
            joined = joined.resolve(name);
        }
        return joined;
    }

    /**
     * {@code name} as a single host path component. A host file system with another separator or
     * with drive roots could otherwise read one guest component as several, or as absolute.
     */
    private static Path component(FileSystem fileSystem, String name) {
        Path component;
        try {
            component = fileSystem.getPath(name);
        } catch (InvalidPathException e) {
            throw ErrorCodes.error(ErrorCode.INVALID);
        }
        if (component.getRoot() != null || component.getNameCount() != 1) {
            throw ErrorCodes.error(ErrorCode.NOT_PERMITTED);
        }
        return component;
    }

    /** The components a symbolic link names, relative to the directory holding the link. */
    private static List<String> linkTarget(Path link) {
        Path target;
        try {
            target = Files.readSymbolicLink(link);
        } catch (IOException e) {
            throw ErrorCodes.failed(e);
        }
        if (target.getRoot() != null) {
            throw ErrorCodes.error(ErrorCode.NOT_PERMITTED);
        }
        if (target.toString().isEmpty()) {
            throw ErrorCodes.error(ErrorCode.NO_ENTRY);
        }
        List<String> names = new ArrayList<>();
        for (Path name : target) {
            names.add(name.toString());
        }
        return names;
    }
}
