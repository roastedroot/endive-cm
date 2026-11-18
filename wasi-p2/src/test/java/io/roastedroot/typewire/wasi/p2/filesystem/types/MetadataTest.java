package io.roastedroot.typewire.wasi.p2.filesystem.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import org.junit.jupiter.api.Test;

class MetadataTest {

    private static final Path PATH = Path.of("a.txt");

    /** Without a file key, a file made later under the same name is another object. */
    @Test
    void withoutAFileKeyTheCreationTimeTellsFilesApart() {
        assertEquals(
                Metadata.identity(PATH, attributes(null, 1)),
                Metadata.identity(PATH, attributes(null, 1)));
        assertNotEquals(
                Metadata.identity(PATH, attributes(null, 1)),
                Metadata.identity(PATH, attributes(null, 2)));
    }

    @Test
    void aFileKeyIsTheIdentity() {
        assertEquals("key", Metadata.identity(PATH, attributes("key", 1)));
    }

    private static BasicFileAttributes attributes(Object fileKey, long created) {
        return new BasicFileAttributes() {
            @Override
            public FileTime lastModifiedTime() {
                return FileTime.fromMillis(created);
            }

            @Override
            public FileTime lastAccessTime() {
                return FileTime.fromMillis(created);
            }

            @Override
            public FileTime creationTime() {
                return FileTime.fromMillis(created);
            }

            @Override
            public boolean isRegularFile() {
                return true;
            }

            @Override
            public boolean isDirectory() {
                return false;
            }

            @Override
            public boolean isSymbolicLink() {
                return false;
            }

            @Override
            public boolean isOther() {
                return false;
            }

            @Override
            public long size() {
                return 0;
            }

            @Override
            public Object fileKey() {
                return fileKey;
            }
        };
    }
}
