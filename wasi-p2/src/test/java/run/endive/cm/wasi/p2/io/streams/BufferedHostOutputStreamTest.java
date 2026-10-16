package run.endive.cm.wasi.p2.io.streams;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.ScheduledFuture;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import run.endive.cm.wasi.p2.bindings.wasi.io.poll.Pollable;
import run.endive.cm.wasi.p2.bindings.wasi.io.streams.StreamError;
import run.endive.cm.wasi.p2.bindings.wasi.io.streams.StreamErrorException;
import run.endive.cm.wasi.p2.io.poll.HostPoll;
import run.endive.runtime.TrapException;

class BufferedHostOutputStreamTest {

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
    void checkWritePermitsWhatTheBufferHasRoomFor() {
        var stream = new BufferedHostOutputStream(executor, poll, 8, null);

        assertEquals(len(8), stream.checkWrite());
        stream.write(bytes("abc"));
        assertEquals(len(5), stream.checkWrite());
        assertArrayEquals(bytes("abc"), stream.drain());
    }

    @Test
    void writingMoreThanPermittedTraps() {
        var stream = new BufferedHostOutputStream(executor, poll, 8, null);
        stream.checkWrite();

        assertThrows(TrapException.class, () -> stream.write(new byte[9]));
    }

    /** Each write spends the permit, so two writes cannot both use all of it. */
    @Test
    void thePermitIsSpentByEachWrite() {
        var stream = new BufferedHostOutputStream(executor, poll, 8, null);
        stream.checkWrite();
        stream.write(new byte[6]);

        assertThrows(TrapException.class, () -> stream.write(new byte[3]));
    }

    @Test
    void writingWithoutCheckingFirstTraps() {
        var stream = new BufferedHostOutputStream(executor, poll, 8, null);

        assertThrows(TrapException.class, () -> stream.write(new byte[1]));
    }

    /** A flush withholds writes until the consumer has taken everything written before it. */
    @Test
    void aFlushHoldsWritesUntilTheConsumerTakesThem() {
        var stream = new BufferedHostOutputStream(executor, poll, 8, null);
        Pollable pollable = stream.subscribe();
        stream.checkWrite();
        stream.write(bytes("flushed"));

        stream.flush();

        assertEquals(BigInteger.ZERO, stream.checkWrite());
        assertFalse(pollable.ready());
        assertArrayEquals(bytes("flushed"), stream.drain());
        assertEquals(len(8), stream.checkWrite());
        assertTrue(pollable.ready());
    }

    @Test
    void aFlushWithNothingWrittenCompletesAtOnce() {
        var stream = new BufferedHostOutputStream(executor, poll, 8, null);

        stream.flush();

        assertEquals(len(8), stream.checkWrite());
    }

    @Test
    void aFullBufferIsNotReady() {
        var stream = new BufferedHostOutputStream(executor, poll, 4, null);
        Pollable pollable = stream.subscribe();
        stream.checkWrite();

        stream.write(new byte[4]);

        assertFalse(pollable.ready());
        stream.drain();
        assertTrue(pollable.ready());
    }

    /** The guest writes in pieces as the consumer makes room, then waits for the flush. */
    @Test
    void blockingWriteAndFlushWaitsForTheConsumer() {
        ByteArrayOutputStream consumed = new ByteArrayOutputStream();
        var stream = new BufferedHostOutputStream(executor, poll, 1024, null);
        ScheduledFuture<?> consumer = drainEvery(stream, consumed);
        byte[] contents = new byte[3000];
        for (int i = 0; i < contents.length; i++) {
            contents[i] = (byte) i;
        }

        stream.blockingWriteAndFlush(contents);

        consumer.cancel(false);
        assertArrayEquals(contents, consumed.toByteArray());
    }

    @Test
    void blockingWriteAndFlushOfMoreThan4096BytesTraps() {
        var stream = new BufferedHostOutputStream(executor, poll, 8192, null);

        assertThrows(TrapException.class, () -> stream.blockingWriteAndFlush(new byte[4097]));
    }

    @Test
    void writeZeroesWritesThatManyZeroes() {
        var stream = new BufferedHostOutputStream(executor, poll, 8, null);
        stream.checkWrite();

        stream.writeZeroes(len(3));

        assertArrayEquals(new byte[3], stream.drain());
    }

    @Test
    void writingMoreZeroesThanPermittedTraps() {
        var stream = new BufferedHostOutputStream(executor, poll, 8, null);
        stream.checkWrite();

        assertThrows(TrapException.class, () -> stream.writeZeroes(BigInteger.ONE.shiftLeft(40)));
    }

    @Test
    void blockingWriteZeroesAndFlushWaitsForTheConsumer() {
        ByteArrayOutputStream consumed = new ByteArrayOutputStream();
        var stream = new BufferedHostOutputStream(executor, poll, 8, null);
        ScheduledFuture<?> consumer = drainEvery(stream, consumed);

        stream.blockingWriteZeroesAndFlush(len(20));

        consumer.cancel(false);
        assertArrayEquals(new byte[20], consumed.toByteArray());
    }

    /** A failure is reported once, and the stream is closed from then on. */
    @Test
    void aFailureIsReportedOnceAndThenTheStreamIsClosed() {
        var stream = new BufferedHostOutputStream(executor, poll, 8, null);
        Pollable pollable = stream.subscribe();
        stream.fail(new IOException("broken pipe"));

        assertTrue(pollable.ready());
        StreamErrorException e = assertThrows(StreamErrorException.class, stream::checkWrite);
        StreamError.LastOperationFailed failed =
                assertInstanceOf(StreamError.LastOperationFailed.class, e.error());
        assertEquals("java.io.IOException: broken pipe", failed.value().toDebugString());
        assertClosed(stream::checkWrite);
    }

    /** A write permitted before the stream closed fails rather than writing anything. */
    @Test
    void aWritePermittedBeforeClosingFailsWithClosed() {
        var stream = new BufferedHostOutputStream(executor, poll, 8, null);
        stream.checkWrite();
        stream.close();

        assertClosed(() -> stream.write(bytes("late")));
        assertArrayEquals(new byte[0], stream.drain());
    }

    @Test
    void theConsumerLearnsOfWritesAndFlushes() {
        int[] notified = new int[1];
        var stream = new BufferedHostOutputStream(executor, poll, 8, () -> notified[0]++);
        stream.checkWrite();

        stream.write(bytes("a"));
        stream.flush();

        assertEquals(2, notified[0]);
    }

    @Test
    void spliceMovesWhatIsAvailable() {
        var input = new BufferedHostInputStream(executor, poll);
        input.offer(bytes("spliced!"));
        var output = new BufferedHostOutputStream(executor, poll, 4, null);

        assertEquals(len(4), output.splice(input, len(10)));
        assertArrayEquals(bytes("spli"), output.drain());
        assertArrayEquals(bytes("ced!"), input.read(len(10)));
    }

    @Test
    void spliceMovesNothingWhileTheOutputIsFull() {
        var input = new BufferedHostInputStream(executor, poll);
        input.offer(bytes("data"));
        var output = new BufferedHostOutputStream(executor, poll, 2, null);
        output.checkWrite();
        output.write(new byte[2]);

        assertEquals(BigInteger.ZERO, output.splice(input, len(10)));
        assertArrayEquals(bytes("data"), input.read(len(10)));
    }

    @Test
    void blockingSpliceWaitsForInput() {
        var input = new BufferedHostInputStream(executor, poll);
        var output = new BufferedHostOutputStream(executor, poll, 16, null);
        executor.schedule(() -> input.offer(bytes("later")), 20, TimeUnit.MILLISECONDS);

        assertEquals(len(5), output.blockingSplice(input, len(10)));
        assertArrayEquals(bytes("later"), output.drain());
    }

    @Test
    void spliceReportsTheInputClosing() {
        var input = new BufferedHostInputStream(executor, poll);
        input.close();
        var output = new BufferedHostOutputStream(executor, poll, 16, null);

        assertClosed(() -> output.splice(input, len(10)));
    }

    /** Drains {@code stream} into {@code consumed} every few milliseconds on the event loop. */
    private ScheduledFuture<?> drainEvery(
            BufferedHostOutputStream stream, ByteArrayOutputStream consumed) {
        return executor.scheduleAtFixedRate(
                () -> consumed.writeBytes(stream.drain()), 5, 5, TimeUnit.MILLISECONDS);
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
