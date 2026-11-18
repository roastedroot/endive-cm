package io.roastedroot.typewire.wasi.p2.cli.stdout;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import io.roastedroot.typewire.wasi.p2.bindings.wasi.io.streams.OutputStream;
import io.roastedroot.typewire.wasi.p2.io.poll.HostPoll;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;

class HostStdoutTest {

    private final ByteArrayOutputStream written = new ByteArrayOutputStream();
    private final HostStdout stdout = new HostStdout(new HostPoll(), written);

    /** Each handle is a stream of its own, so dropping one leaves the others writing. */
    @Test
    void droppingOneStreamLeavesTheOthersUsable() {
        OutputStream first = stdout.getStdout();
        OutputStream second = stdout.getStdout();
        assertNotSame(first, second);

        first.blockingWriteAndFlush("one ".getBytes(UTF_8));
        first.drop();
        second.blockingWriteAndFlush("two".getBytes(UTF_8));

        assertEquals("one two", written.toString(UTF_8));
    }
}
