package io.roastedroot.typewire.wasi.p2.io.streams;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.roastedroot.typewire.wasi.p2.bindings.wasi.io.poll.Pollable;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.io.streams.StreamError;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.io.streams.StreamErrorException;
import io.roastedroot.typewire.wasi.p2.io.poll.HostPoll;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import run.endive.runtime.TrapException;

class BufferedHostOutputStreamTest {

    private static final int RACE_ROUNDS = 20_000;

    private ScheduledExecutorService scheduler;
    private HostPoll poll;

    @BeforeEach
    void startScheduler() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        poll = new HostPoll();
    }

    @AfterEach
    void stopScheduler() {
        scheduler.shutdownNow();
    }

    @Test
    void checkWritePermitsWhatTheBufferHasRoomFor() {
        var stream = new BufferedHostOutputStream(poll, 8, null);

        assertEquals(len(8), stream.checkWrite());
        stream.write(bytes("abc"));
        assertEquals(len(5), stream.checkWrite());
        assertArrayEquals(bytes("abc"), stream.drain());
    }

    @Test
    void writingMoreThanPermittedTraps() {
        var stream = new BufferedHostOutputStream(poll, 8, null);
        stream.checkWrite();

        assertThrows(TrapException.class, () -> stream.write(new byte[9]));
    }

    /** Each write spends the permit, so two writes cannot both use all of it. */
    @Test
    void thePermitIsSpentByEachWrite() {
        var stream = new BufferedHostOutputStream(poll, 8, null);
        stream.checkWrite();
        stream.write(new byte[6]);

        assertThrows(TrapException.class, () -> stream.write(new byte[3]));
    }

    @Test
    void writingWithoutCheckingFirstTraps() {
        var stream = new BufferedHostOutputStream(poll, 8, null);

        assertThrows(TrapException.class, () -> stream.write(new byte[1]));
    }

    /** A flush withholds writes until the consumer delivers everything written before it. */
    @Test
    void aFlushHoldsWritesUntilTheConsumerDeliversThem() {
        var stream = new BufferedHostOutputStream(poll, 8, null);
        Pollable pollable = stream.subscribe();
        stream.checkWrite();
        stream.write(bytes("flushed"));

        stream.flush();

        assertEquals(BigInteger.ZERO, stream.checkWrite());
        assertFalse(pollable.ready());
        assertArrayEquals(bytes("flushed"), stream.drain());
        assertEquals(BigInteger.ZERO, stream.checkWrite());
        assertFalse(pollable.ready());
        stream.markDelivered(7);
        assertEquals(len(8), stream.checkWrite());
        assertTrue(pollable.ready());
    }

    /** Bytes the consumer is still delivering count against the room the guest has. */
    @Test
    void drainedBytesKeepTheirRoomUntilDelivered() {
        var stream = new BufferedHostOutputStream(poll, 8, null);
        stream.checkWrite();
        stream.write(bytes("abc"));

        stream.drain();

        assertEquals(len(5), stream.checkWrite());
        stream.markDelivered(2);
        assertEquals(len(7), stream.checkWrite());
    }

    @Test
    void markingMoreThanWasDrainedIsRefused() {
        var stream = new BufferedHostOutputStream(poll, 8, null);
        stream.checkWrite();
        stream.write(bytes("abc"));
        stream.drain();

        assertThrows(IllegalArgumentException.class, () -> stream.markDelivered(4));
    }

    @Test
    void aFlushWithNothingWrittenCompletesAtOnce() {
        var stream = new BufferedHostOutputStream(poll, 8, null);

        stream.flush();

        assertEquals(len(8), stream.checkWrite());
    }

    @Test
    void aFullBufferIsNotReady() {
        var stream = new BufferedHostOutputStream(poll, 4, null);
        Pollable pollable = stream.subscribe();
        stream.checkWrite();

        stream.write(new byte[4]);

        assertFalse(pollable.ready());
        stream.markDelivered(stream.drain().length);
        assertTrue(pollable.ready());
    }

    /** The guest writes in pieces as the consumer makes room, then waits for the flush. */
    @Test
    void blockingWriteAndFlushWaitsForTheConsumer() {
        ByteArrayOutputStream consumed = new ByteArrayOutputStream();
        var stream = new BufferedHostOutputStream(poll, 1024, null);
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
        var stream = new BufferedHostOutputStream(poll, 8192, null);

        assertThrows(TrapException.class, () -> stream.blockingWriteAndFlush(new byte[4097]));
    }

    @Test
    void writeZeroesWritesThatManyZeroes() {
        var stream = new BufferedHostOutputStream(poll, 8, null);
        stream.checkWrite();

        stream.writeZeroes(len(3));

        assertArrayEquals(new byte[3], stream.drain());
    }

    @Test
    void writingMoreZeroesThanPermittedTraps() {
        var stream = new BufferedHostOutputStream(poll, 8, null);
        stream.checkWrite();

        assertThrows(TrapException.class, () -> stream.writeZeroes(BigInteger.ONE.shiftLeft(40)));
    }

    @Test
    void blockingWriteZeroesAndFlushWaitsForTheConsumer() {
        ByteArrayOutputStream consumed = new ByteArrayOutputStream();
        var stream = new BufferedHostOutputStream(poll, 8, null);
        ScheduledFuture<?> consumer = drainEvery(stream, consumed);

        stream.blockingWriteZeroesAndFlush(len(20));

        consumer.cancel(false);
        assertArrayEquals(new byte[20], consumed.toByteArray());
    }

    /** A failure is reported once, and the stream is closed from then on. */
    @Test
    void aFailureIsReportedOnceAndThenTheStreamIsClosed() {
        var stream = new BufferedHostOutputStream(poll, 8, null);
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
        var stream = new BufferedHostOutputStream(poll, 8, null);
        stream.checkWrite();
        stream.close();

        assertClosed(() -> stream.write(bytes("late")));
        assertArrayEquals(new byte[0], stream.drain());
    }

    @Test
    void theConsumerLearnsOfWritesAndFlushes() {
        int[] notified = new int[1];
        var stream = new BufferedHostOutputStream(poll, 8, () -> notified[0]++);
        stream.checkWrite();

        stream.write(bytes("a"));
        stream.flush();

        assertEquals(2, notified[0]);
    }

    /** The consumer learns of the drop, and can still drain what the guest wrote before it. */
    @Test
    void droppingClosesTheStreamAndLeavesItsBytesToDrain() {
        int[] notified = new int[1];
        var stream = new BufferedHostOutputStream(poll, 8, () -> notified[0]++);
        stream.checkWrite();
        stream.write(bytes("abc"));

        stream.drop();

        assertEquals(2, notified[0]);
        assertTrue(stream.isClosed());
        assertArrayEquals(bytes("abc"), stream.drain());
    }

    @Test
    void spliceMovesWhatIsAvailable() {
        var input = new BufferedHostInputStream(poll);
        input.offer(bytes("spliced!"));
        var output = new BufferedHostOutputStream(poll, 4, null);

        assertEquals(len(4), output.splice(input, len(10)));
        assertArrayEquals(bytes("spli"), output.drain());
        assertArrayEquals(bytes("ced!"), input.read(len(10)));
    }

    @Test
    void spliceMovesNothingWhileTheOutputIsFull() {
        var input = new BufferedHostInputStream(poll);
        input.offer(bytes("data"));
        var output = new BufferedHostOutputStream(poll, 2, null);
        output.checkWrite();
        output.write(new byte[2]);

        assertEquals(BigInteger.ZERO, output.splice(input, len(10)));
        assertArrayEquals(bytes("data"), input.read(len(10)));
    }

    @Test
    void blockingSpliceWaitsForInput() {
        var input = new BufferedHostInputStream(poll);
        var output = new BufferedHostOutputStream(poll, 16, null);
        scheduler.schedule(() -> input.offer(bytes("later")), 20, TimeUnit.MILLISECONDS);

        assertEquals(len(5), output.blockingSplice(input, len(10)));
        assertArrayEquals(bytes("later"), output.drain());
    }

    @Test
    void spliceReportsTheInputClosing() {
        var input = new BufferedHostInputStream(poll);
        input.close();
        var output = new BufferedHostOutputStream(poll, 16, null);

        assertClosed(() -> output.splice(input, len(10)));
    }

    /**
     * A consumer on another thread delivering as fast as it can, against a guest that writes and
     * flushes. A flush that completed before everything it covers was delivered would leave bytes
     * to drain, or less than the full capacity to write, once it returned.
     */
    @Test
    void aFlushCoversEveryWriteBeforeItDespiteConcurrentDeliveries() {
        byte[] expected = new byte[200_000];
        new Random(1).nextBytes(expected);
        var stream = new BufferedHostOutputStream(poll, 64, null);
        AtomicBoolean done = new AtomicBoolean();
        ByteArrayOutputStream consumed = new ByteArrayOutputStream();
        Thread consumer =
                new Thread(
                        () -> {
                            while (!done.get()) {
                                deliver(stream, consumed);
                            }
                        });

        assertTimeoutPreemptively(
                Duration.ofSeconds(30),
                () -> {
                    consumer.start();
                    Random sizes = new Random(2);
                    int offset = 0;
                    while (offset < expected.length) {
                        int end = Math.min(expected.length, offset + 1 + sizes.nextInt(200));
                        stream.blockingWriteAndFlush(Arrays.copyOfRange(expected, offset, end));
                        assertEquals(0, stream.drain().length);
                        assertEquals(len(64), stream.checkWrite());
                        offset = end;
                    }
                    done.set(true);
                    consumer.join();
                });
        consumed.writeBytes(stream.drain());
        assertArrayEquals(expected, consumed.toByteArray());
    }

    /**
     * Closing races a write that fills the buffer, round after round. However they interleave, a
     * guest waiting on the stream afterward is woken.
     */
    @Test
    void aGuestWaitingOnAFullStreamSeesItClose() {
        CyclicBarrier start = new CyclicBarrier(2);
        BufferedHostOutputStream[] round = new BufferedHostOutputStream[1];
        Thread closer =
                new Thread(
                        () -> {
                            for (int i = 0; i < RACE_ROUNDS; i++) {
                                await(start);
                                round[0].close();
                                await(start);
                            }
                        });

        assertTimeoutPreemptively(
                Duration.ofSeconds(30),
                () -> {
                    closer.start();
                    for (int i = 0; i < RACE_ROUNDS; i++) {
                        var stream = new BufferedHostOutputStream(poll, 4, null);
                        stream.checkWrite();
                        round[0] = stream;
                        await(start);
                        try {
                            stream.write(new byte[4]);
                        } catch (StreamErrorException e) {
                            assertInstanceOf(StreamError.Closed.class, e.error());
                        }
                        stream.subscribe().block();
                        await(start);
                    }
                    closer.join();
                });
    }

    /** Delivers {@code stream} into {@code consumed} every few milliseconds on the scheduler. */
    private ScheduledFuture<?> drainEvery(
            BufferedHostOutputStream stream, ByteArrayOutputStream consumed) {
        return scheduler.scheduleAtFixedRate(
                () -> deliver(stream, consumed), 5, 5, TimeUnit.MILLISECONDS);
    }

    private static void deliver(BufferedHostOutputStream stream, ByteArrayOutputStream consumed) {
        byte[] bytes = stream.drain();
        consumed.writeBytes(bytes);
        stream.markDelivered(bytes.length);
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (InterruptedException | BrokenBarrierException e) {
            throw new IllegalStateException(e);
        }
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
