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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BufferedHostInputStreamTest {

    private static final int RACE_ROUNDS = 100_000;

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
    void readReturnsWhatIsBufferedUpToLen() {
        var stream = new BufferedHostInputStream(poll);
        stream.offer(bytes("hel"));
        stream.offer(bytes("lo"));

        assertArrayEquals(bytes("hell"), stream.read(len(4)));
        assertArrayEquals(bytes("o"), stream.read(len(10)));
    }

    @Test
    void readReturnsNothingWhenNothingIsBuffered() {
        var stream = new BufferedHostInputStream(poll);

        assertArrayEquals(new byte[0], stream.read(len(10)));
    }

    /** What was buffered before closing is still read, and only then is the stream closed. */
    @Test
    void closingTakesEffectOnceTheBufferIsRead() {
        var stream = new BufferedHostInputStream(poll);
        stream.offer(bytes("bye"));
        stream.close();

        assertArrayEquals(bytes("bye"), stream.read(len(10)));
        assertClosed(() -> stream.read(len(10)));
    }

    @Test
    void readingNothingSucceedsWhileOpenAndFailsOnceClosed() {
        var stream = new BufferedHostInputStream(poll);

        assertArrayEquals(new byte[0], stream.read(BigInteger.ZERO));
        stream.close();
        assertClosed(() -> stream.read(BigInteger.ZERO));
    }

    /** A failure is reported once, and the stream is closed from then on. */
    @Test
    void aFailureIsReportedOnceAndThenTheStreamIsClosed() {
        var stream = new BufferedHostInputStream(poll);
        stream.fail(new IOException("reset"));

        StreamErrorException e =
                assertThrows(StreamErrorException.class, () -> stream.read(len(10)));
        StreamError.LastOperationFailed failed =
                assertInstanceOf(StreamError.LastOperationFailed.class, e.error());
        assertEquals("java.io.IOException: reset", failed.value().toDebugString());
        assertClosed(() -> stream.read(len(10)));
    }

    /** Bytes the source produced before failing still reach the guest, ahead of the failure. */
    @Test
    void whatWasBufferedIsReadBeforeTheFailure() {
        var stream = new BufferedHostInputStream(poll);
        Pollable pollable = stream.subscribe();
        stream.offer(bytes("kept"));
        stream.fail(new IOException("reset"));

        assertArrayEquals(bytes("kept"), stream.read(len(10)));
        assertTrue(pollable.ready());
        StreamErrorException e =
                assertThrows(StreamErrorException.class, () -> stream.read(len(10)));
        assertInstanceOf(StreamError.LastOperationFailed.class, e.error());
    }

    @Test
    void aLargeLenIsClampedRatherThanRefused() {
        var stream = new BufferedHostInputStream(poll);
        stream.offer(bytes("x"));

        assertArrayEquals(
                bytes("x"), stream.read(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)));
    }

    @Test
    void blockingReadWaitsForBytes() {
        var stream = new BufferedHostInputStream(poll);
        scheduler.schedule(() -> stream.offer(bytes("late")), 20, TimeUnit.MILLISECONDS);

        assertArrayEquals(bytes("late"), stream.blockingRead(len(10)));
    }

    @Test
    void blockingReadWaitsForTheStreamToClose() {
        var stream = new BufferedHostInputStream(poll);
        scheduler.schedule(stream::close, 20, TimeUnit.MILLISECONDS);

        assertClosed(() -> stream.blockingRead(len(10)));
    }

    @Test
    void skipDiscardsAndCounts() {
        var stream = new BufferedHostInputStream(poll);
        stream.offer(bytes("abcdef"));

        assertEquals(len(4), stream.skip(len(4)));
        assertArrayEquals(bytes("ef"), stream.read(len(10)));
    }

    @Test
    void blockingSkipWaitsForBytes() {
        var stream = new BufferedHostInputStream(poll);
        scheduler.schedule(() -> stream.offer(bytes("abc")), 20, TimeUnit.MILLISECONDS);

        assertEquals(len(3), stream.blockingSkip(len(10)));
    }

    /** A subscription follows the stream, ready while there is something to read or it ended. */
    @Test
    void aSubscriptionIsReadyWhileThereIsSomethingToRead() {
        var stream = new BufferedHostInputStream(poll);
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
        var stream = new BufferedHostInputStream(poll, 8, demand::incrementAndGet);

        assertTrue(stream.offer(new byte[6]));
        assertFalse(stream.offer(new byte[2]));
        stream.read(len(3));
        assertEquals(0, demand.get());
        stream.read(len(2));
        assertEquals(1, demand.get());
    }

    /** Dropping discards what was unread and resumes a paused source, which then sees it should stop. */
    @Test
    void droppingDiscardsTheBufferAndStopsTheSource() {
        AtomicInteger demand = new AtomicInteger();
        var stream = new BufferedHostInputStream(poll, 4, demand::incrementAndGet);
        assertFalse(stream.offer(new byte[4]));

        stream.drop();

        assertEquals(1, demand.get());
        assertTrue(stream.isClosed());
        assertFalse(stream.offer(new byte[1]));
    }

    @Test
    void aStreamIsClosedOnceTheSourceEndsIt() {
        var stream = new BufferedHostInputStream(poll);

        assertFalse(stream.isClosed());
        stream.close();
        assertTrue(stream.isClosed());
    }

    /**
     * A source on another thread that pauses whenever asked and waits for demand, against a guest
     * that blocks for each read. A lost resume or a lost wakeup would leave both waiting.
     */
    @Test
    void aPausingSourceAndABlockingGuestHandOverEverything() {
        byte[] expected = new byte[200_000];
        new Random(1).nextBytes(expected);
        Semaphore demand = new Semaphore(0);
        var stream = new BufferedHostInputStream(poll, 64, demand::release);
        Thread source = new Thread(() -> produce(stream, expected, demand));
        ByteArrayOutputStream received = new ByteArrayOutputStream();

        assertTimeoutPreemptively(
                Duration.ofSeconds(30),
                () -> {
                    source.start();
                    try {
                        while (true) {
                            received.writeBytes(stream.blockingRead(len(37)));
                        }
                    } catch (StreamErrorException e) {
                        assertInstanceOf(StreamError.Closed.class, e.error());
                    }
                    source.join();
                });
        assertArrayEquals(expected, received.toByteArray());
    }

    /**
     * A source offers one byte as the guest starts a blocking read, round after round, waiting a
     * little longer each round so the offer lands at every point of the read. However they
     * interleave, the guest receives it.
     */
    @Test
    void aGuestBlockingOnAnEmptyStreamSeesTheNextOffer() {
        var stream = new BufferedHostInputStream(poll);
        CyclicBarrier start = new CyclicBarrier(2);
        Thread source =
                new Thread(
                        () -> {
                            for (int i = 0; i < RACE_ROUNDS; i++) {
                                await(start);
                                for (int spin = i % 256; spin > 0; spin--) {
                                    Thread.onSpinWait();
                                }
                                stream.offer(new byte[] {(byte) i});
                            }
                        });

        assertTimeoutPreemptively(
                Duration.ofSeconds(30),
                () -> {
                    source.start();
                    for (int i = 0; i < RACE_ROUNDS; i++) {
                        await(start);
                        assertArrayEquals(new byte[] {(byte) i}, stream.blockingRead(len(1)));
                    }
                    source.join();
                });
    }

    /** Offers {@code bytes} in pieces of varying size, waiting for demand whenever asked to pause. */
    private static void produce(BufferedHostInputStream stream, byte[] bytes, Semaphore demand) {
        Random sizes = new Random(2);
        int offset = 0;
        while (offset < bytes.length) {
            int end = Math.min(bytes.length, offset + 1 + sizes.nextInt(50));
            if (!stream.offer(Arrays.copyOfRange(bytes, offset, end))) {
                demand.acquireUninterruptibly();
            }
            offset = end;
        }
        stream.close();
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
