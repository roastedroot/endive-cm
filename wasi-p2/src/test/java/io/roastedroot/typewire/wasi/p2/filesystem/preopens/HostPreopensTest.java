package io.roastedroot.typewire.wasi.p2.filesystem.preopens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.roastedroot.typewire.runtime.Tuple2;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.Descriptor;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.DescriptorFlags;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.DescriptorType;
import io.roastedroot.typewire.wasi.p2.filesystem.types.HostDescriptor;
import io.roastedroot.typewire.wasi.p2.io.poll.HostPoll;
import io.roastedroot.zerofs.Configuration;
import io.roastedroot.zerofs.ZeroFs;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class HostPreopensTest {

    private final HostPoll poll = new HostPoll();

    @Test
    void eachDirectoryIsGrantedUnderItsNameInOrder() throws IOException {
        try (FileSystem fs = ZeroFs.newFileSystem(Configuration.unix())) {
            Map<String, Path> directories = new LinkedHashMap<>();
            directories.put("/data", Files.createDirectories(fs.getPath("/host/data")));
            directories.put(".", Files.createDirectories(fs.getPath("/host/work")));

            List<Tuple2<Descriptor, String>> granted =
                    new HostPreopens(directories, poll).getDirectories();

            assertEquals(
                    List.of("/data", "."),
                    granted.stream().map(Tuple2::f1).collect(Collectors.toList()));
            assertEquals(fs.getPath("/host/data"), ((HostDescriptor) granted.get(0).f0()).path());
        }
    }

    @Test
    void aPreopenIsADirectoryTheGuestMayReadAndMutate() throws IOException {
        try (FileSystem fs = ZeroFs.newFileSystem(Configuration.unix())) {
            Path data = Files.createDirectories(fs.getPath("/data"));

            Descriptor descriptor =
                    new HostPreopens(Map.of("data", data), poll).getDirectories().get(0).f0();

            assertEquals(DescriptorType.DIRECTORY, descriptor.getType());
            assertTrue(descriptor.getFlags().has(DescriptorFlags.Flag.READ));
            assertTrue(descriptor.getFlags().has(DescriptorFlags.Flag.MUTATE_DIRECTORY));
            assertFalse(descriptor.getFlags().has(DescriptorFlags.Flag.WRITE));
        }
    }

    /** Every call hands out the same descriptors, so a guest dropping one loses nothing. */
    @Test
    void theSameDescriptorsAreGrantedEveryTime() throws IOException {
        try (FileSystem fs = ZeroFs.newFileSystem(Configuration.unix())) {
            HostPreopens preopens =
                    new HostPreopens(
                            Map.of("data", Files.createDirectories(fs.getPath("/data"))), poll);

            Descriptor first = preopens.getDirectories().get(0).f0();
            first.drop();

            assertSame(first, preopens.getDirectories().get(0).f0());
            assertTrue(first.isSameObject(preopens.getDirectories().get(0).f0()));
        }
    }
}
