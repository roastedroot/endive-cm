package io.roastedroot.typewire.wasi.p2.filesystem.preopens;

import io.roastedroot.typewire.runtime.Tuple2;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.preopens.Host;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.Descriptor;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.DescriptorFlags;
import io.roastedroot.typewire.wasi.p2.filesystem.types.HostDirectory;
import io.roastedroot.typewire.wasi.p2.filesystem.types.OpenFiles;
import io.roastedroot.typewire.wasi.p2.io.poll.HostPoll;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The host side of {@code wasi:filesystem/preopens}, granting a guest its configured directories.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/filesystem/wit/preopens.wit">wasi:filesystem/preopens</a>
 */
public class HostPreopens implements Host {

    private final List<Tuple2<Descriptor, String>> directories;

    /**
     * Each directory is granted with read and directory mutation access.
     *
     * @param directories each host directory, keyed by its name in the guest
     * @param poll the poll behind the subscriptions of file streams
     */
    HostPreopens(Map<String, Path> directories, HostPoll poll) {
        this(directories, poll, new OpenFiles());
    }

    /**
     * Each directory is granted with read and directory mutation access.
     *
     * @param directories each host directory, keyed by its name in the guest
     * @param poll the poll behind the subscriptions of file streams
     * @param files where the files opened through the directories are tracked
     */
    public HostPreopens(Map<String, Path> directories, HostPoll poll, OpenFiles files) {
        this(directories, Set.of(), poll, files);
    }

    /**
     * Each directory is granted with read access, and with directory mutation access unless it is
     * read-only.
     *
     * @param directories each host directory, keyed by its name in the guest
     * @param readOnly the names of the directories the guest may only read
     * @param poll the poll behind the subscriptions of file streams
     * @param files where the files opened through the directories are tracked
     */
    public HostPreopens(
            Map<String, Path> directories, Set<String> readOnly, HostPoll poll, OpenFiles files) {
        DescriptorFlags mutable =
                DescriptorFlags.of(
                        DescriptorFlags.Flag.READ, DescriptorFlags.Flag.MUTATE_DIRECTORY);
        DescriptorFlags readable = DescriptorFlags.of(DescriptorFlags.Flag.READ);
        List<Tuple2<Descriptor, String>> granted = new ArrayList<>();
        for (Map.Entry<String, Path> directory : directories.entrySet()) {
            DescriptorFlags flags = readOnly.contains(directory.getKey()) ? readable : mutable;
            granted.add(
                    Tuple2.of(
                            new HostDirectory(directory.getValue(), flags, poll, files),
                            directory.getKey()));
        }
        this.directories = List.copyOf(granted);
    }

    @Override
    public List<Tuple2<Descriptor, String>> getDirectories() {
        return directories;
    }
}
