package run.endive.cm.wasi.p2.io.streams;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.EventExecutor;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import run.endive.cm.wasi.p2.bindings.wasi.io.poll.Pollable;
import run.endive.cm.wasi.p2.bindings.wasi.io.streams.StreamError;
import run.endive.cm.wasi.p2.bindings.wasi.io.streams.StreamErrorException;
import run.endive.cm.wasi.p2.io.poll.HostPoll;
import run.endive.wasm.WasmEngineException;

class BufferedHostInputStreamTest {

    private EventExecutor executor;
    private HostPoll poll;

    @BeforeEach
    void startExecutor() {
        executor = new DefaultEventExecutor();
        poll = new HostPoll(executor);
    }

    @AfterEach
    void stopExecutor() {
        executor.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
    }

    @Test
    void readReturnsWhatIsBufferedUpToLen() {
        var stream = new BufferedHostInputStream(executor, poll);
        stream.offer(bytes("hel"));
        stream.offer(bytes("lo"));

        assertArrayEquals(bytes("hell"), stream.read(len(4)));
        assertArrayEquals(bytes("o"), stream.read(len(10)));
    }

    @Test
    void readReturnsNothingWhenNothingIsBuffered() {
        var stream = new BufferedHostInputStream(executor, poll);

        assertArrayEquals(new byte[0], stream.read(len(10)));
    }

    /** What was buffered before closing is still read, and only then is the stream closed. */
    @Test
    void closingTakesEffectOnceTheBufferIsRead() {
        var stream = new BufferedHostInputStream(executor, poll);
        stream.offer(bytes("bye"));
        stream.close();

        assertArrayEquals(bytes("bye"), stream.read(len(10)));
        assertClosed(() -> stream.read(len(10)));
    }

    @Test
    void readingNothingSucceedsWhileOpenAndFailsOnceClosed() {
        var stream = new BufferedHostInputStream(executor, poll);

        assertArrayEquals(new byte[0], stream.read(BigInteger.ZERO));
        stream.close();
        assertClosed(() -> stream.read(BigInteger.ZERO));
    }

    /** A failure is reported once, and the stream is closed from then on. */
    @Test
    void aFailureIsReportedOnceAndThenTheStreamIsClosed() {
        var stream = new BufferedHostInputStream(executor, poll);
        stream.offer(bytes("lost"));
        stream.fail(new IOException("reset"));

        StreamErrorException e =
                assertThrows(StreamErrorException.class, () -> stream.read(len(10)));
        StreamError.LastOperationFailed failed =
                assertInstanceOf(StreamError.LastOperationFailed.class, e.error());
        assertEquals("java.io.IOException: reset", failed.value().toDebugString());
        assertClosed(() -> stream.read(len(10)));
    }

    @Test
    void aLargeLenIsClampedRatherThanRefused() {
        var stream = new BufferedHostInputStream(executor, poll);
        stream.offer(bytes("x"));

        assertArrayEquals(
                bytes("x"), stream.read(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)));
    }

    @Test
    void blockingReadWaitsForBytes() {
        var stream = new BufferedHostInputStream(executor, poll);
        executor.schedule(() -> stream.offer(bytes("late")), 20, TimeUnit.MILLISECONDS);

        assertArrayEquals(bytes("late"), stream.blockingRead(len(10)));
    }

    @Test
    void blockingReadWaitsForTheStreamToClose() {
        var stream = new BufferedHostInputStream(executor, poll);
        executor.schedule(stream::close, 20, TimeUnit.MILLISECONDS);

        assertClosed(() -> stream.blockingRead(len(10)));
    }

    @Test
    void blockingReadOnTheEventLoopIsRefused() {
        var stream = new BufferedHostInputStream(executor, poll);

        ExecutionException e =
                assertThrows(
                        ExecutionException.class,
                        () -> executor.submit(() -> stream.blockingRead(len(1))).get());
        assertInstanceOf(WasmEngineException.class, e.getCause());
    }

    @Test
    void skipDiscardsAndCounts() {
        var stream = new BufferedHostInputStream(executor, poll);
        stream.offer(bytes("abcdef"));

        assertEquals(len(4), stream.skip(len(4)));
        assertArrayEquals(bytes("ef"), stream.read(len(10)));
    }

    @Test
    void blockingSkipWaitsForBytes() {
        var stream = new BufferedHostInputStream(executor, poll);
        executor.schedule(() -> stream.offer(bytes("abc")), 20, TimeUnit.MILLISECONDS);

        assertEquals(len(3), stream.blockingSkip(len(10)));
    }

    /** A subscription follows the stream, ready while there is something to read or it ended. */
    @Test
    void aSubscriptionIsReadyWhileThereIsSomethingToRead() {
        var stream = new BufferedHostInputStream(executor, poll);
        Pollable pollable = stream.subscribe();

        assertFalse(pollable.ready());
        stream.offer(bytes("data"));
        assertTrue(pollable.ready());
        stream.read(len(10));
        assertFalse(pollable.ready());
        stream.close();
        assertTrue(pollable.ready());
    }

    /** A full buffer pauses the source, which resumes once the guest has read it below half. */
    @Test
    void theSourcePausesWhenFullAndResumesOnceReadBelowHalf() {
        AtomicInteger demand = new AtomicInteger();
        var stream = new BufferedHostInputStream(executor, poll, 8, demand::incrementAndGet);

        assertTrue(stream.offer(new byte[6]));
        assertFalse(stream.offer(new byte[2]));
        stream.read(len(3));
        assertEquals(0, demand.get());
        stream.read(len(2));
        assertEquals(1, demand.get());
    }

    private static void assertClosed(Runnable operation) {
        StreamErrorException e = assertThrows(StreamErrorException.class, operation::run);
        assertInstanceOf(StreamError.Closed.class, e.error());
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static BigInteger len(long len) {
        return BigInteger.valueOf(len);
    }
}
