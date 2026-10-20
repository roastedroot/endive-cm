package run.endive.cm.wasi.p2.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import run.endive.cm.wasi.p2.bindings.wasi.io.streams.StreamError;
import run.endive.cm.wasi.p2.bindings.wasi.io.streams.StreamErrorException;
import run.endive.cm.wasi.p2.io.poll.HostPoll;

class HostStdioOutputStreamTest {

    private final HostPoll poll = new HostPoll();
    private final Recording sink = new Recording();
    private final HostStdioOutputStream stream = new HostStdioOutputStream(poll, sink);

    @Test
    void aWriteReachesTheJavaStreamBeforeItReturns() {
        stream.checkWrite();
        stream.write(bytes("hello"));

        assertEquals("hello", sink.toString(UTF_8));
    }

    @Test
    void aFlushFlushesTheJavaStream() {
        stream.blockingWriteAndFlush(bytes("line\n"));
        stream.flush();

        assertEquals("line\n", sink.toString(UTF_8));
        assertEquals(2, sink.flushes);
    }

    /** The stream is always ready, and permits the same amount after every flush. */
    @Test
    void theStreamIsAlwaysReady() {
        assertTrue(stream.subscribe().ready());
        assertEquals(BigInteger.valueOf(HostStdioOutputStream.CAPACITY), stream.checkWrite());
        stream.flush();
        assertEquals(BigInteger.valueOf(HostStdioOutputStream.CAPACITY), stream.checkWrite());
    }

    /** A failure is reported once, and every later operation finds the stream closed. */
    @Test
    void aFailingJavaStreamFailsTheStreamAndThenClosesIt() {
        HostStdioOutputStream failing =
                new HostStdioOutputStream(
                        poll,
                        new OutputStream() {
                            @Override
                            public void write(int b) throws IOException {
                                throw new IOException("disk full");
                            }
                        });
        failing.checkWrite();

        StreamErrorException e =
                assertThrows(StreamErrorException.class, () -> failing.write(bytes("x")));
        assertInstanceOf(StreamError.LastOperationFailed.class, e.error());
        StreamErrorException again = assertThrows(StreamErrorException.class, failing::checkWrite);
        assertInstanceOf(StreamError.Closed.class, again.error());
    }

    @Test
    void droppingTheStreamLeavesTheJavaStreamOpen() {
        stream.drop();

        assertFalse(sink.closed);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(UTF_8);
    }

    private static final class Recording extends ByteArrayOutputStream {

        private int flushes;
        private boolean closed;

        @Override
        public void flush() {
            flushes++;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
