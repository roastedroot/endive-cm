package io.roastedroot.typewire.wasi.p2.filesystem.types;

import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.DirectoryEntry;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.DirectoryEntryStream;
import java.util.Iterator;
import java.util.List;

/**
 * The host side of the {@code wasi:filesystem} {@code directory-entry-stream} resource, over the
 * entries a directory held when it was read.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/filesystem/wit/types.wit">wasi:filesystem/types</a>
 */
final class HostDirectoryEntryStream implements DirectoryEntryStream {

    private final Iterator<DirectoryEntry> entries;

    /** Takes over {@code entries} without copying them. */
    HostDirectoryEntryStream(List<DirectoryEntry> entries) {
        this.entries = entries.iterator();
    }

    /** The next entry, or {@code null} once every entry has been read. */
    @Override
    public DirectoryEntry readDirectoryEntry() {
        return entries.hasNext() ? entries.next() : null;
    }
}
