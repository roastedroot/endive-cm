package io.roastedroot.typewire.wasi.p2.filesystem.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.ErrorCode;
import io.roastedroot.typewire.wasi.p2.io.error.HostError;
import java.nio.file.NoSuchFileException;
import org.junit.jupiter.api.Test;

class HostTypesTest {

    private final HostTypes types = new HostTypes();

    @Test
    void aStreamErrorFromTheFileSystemHasItsCode() {
        assertEquals(
                ErrorCode.NO_ENTRY,
                types.filesystemErrorCode(new HostError(new NoSuchFileException("a"))));
    }

    @Test
    void aStreamErrorFromElsewhereHasNoCode() {
        assertNull(types.filesystemErrorCode(new HostError(new IllegalStateException())));
    }
}
