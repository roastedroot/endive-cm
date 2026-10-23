package run.endive.cm.wasi.p2.cli.stderr;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;
import run.endive.cm.wasi.p2.bindings.wasi.io.streams.OutputStream;
import run.endive.cm.wasi.p2.io.poll.HostPoll;

class HostStderrTest {

    private final ByteArrayOutputStream written = new ByteArrayOutputStream();
    private final HostStderr stderr = new HostStderr(new HostPoll(), written);

    /** Each handle is a stream of its own, so dropping one leaves the others writing. */
    @Test
    void droppingOneStreamLeavesTheOthersUsable() {
        OutputStream first = stderr.getStderr();
        OutputStream second = stderr.getStderr();
        assertNotSame(first, second);

        first.blockingWriteAndFlush("one ".getBytes(UTF_8));
        first.drop();
        second.blockingWriteAndFlush("two".getBytes(UTF_8));

        assertEquals("one two", written.toString(UTF_8));
    }
}
