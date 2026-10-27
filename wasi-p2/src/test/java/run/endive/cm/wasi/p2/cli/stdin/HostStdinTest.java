package run.endive.cm.wasi.p2.cli.stdin;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.SequenceInputStream;
import java.math.BigInteger;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import run.endive.cm.wasi.p2.bindings.wasi.io.poll.Pollable;
import run.endive.cm.wasi.p2.bindings.wasi.io.streams.InputStream;
import run.endive.cm.wasi.p2.bindings.wasi.io.streams.StreamError;
import run.endive.cm.wasi.p2.bindings.wasi.io.streams.StreamErrorException;
import run.endive.cm.wasi.p2.io.poll.HostPoll;

class HostStdinTest {

    private static final String READER = "endive-wasi-p2-stdin";

    private final HostPoll poll = new HostPoll();

    @Test
    void stdinHoldsWhatTheJavaStreamHoldsAndThenCloses() {
        try (HostStdin stdin = new HostStdin(poll, new ByteArrayInputStream(bytes("hello")))) {
            assertArrayEquals(bytes("hello"), readToEnd(stdin.getStdin()));
        }
    }

    /** A guest asks for stdin before any other operation, so only a read or a poll reads it. */
    @Test
    void askingForStdinReadsNothingUntilTheGuestReadsOrPolls() {
        Feed feed = new Feed();
        Set<Thread> before = readers();

        try (HostStdin stdin = new HostStdin(poll, feed)) {
            InputStream stream = stdin.getStdin();
            assertTrue(before.containsAll(readers()));

            stream.subscribe().ready();
            Set<Thread> started = readers();
            started.removeAll(before);
            assertEquals(1, started.size());
        } finally {
            feed.end();
        }
    }

    @Test
    void withoutAJavaStreamStdinIsClosedAndNothingReadsIt() {
        Set<Thread> before = readers();

        try (HostStdin stdin = new HostStdin(poll, null)) {
            InputStream stream = stdin.getStdin();

            assertTrue(before.containsAll(readers()));
            assertClosed(() -> stream.read(len(1)));
        }
    }

    /** What the Java stream gave before failing reaches the guest ahead of the failure. */
    @Test
    void aFailingJavaStreamFailsStdinAfterWhatCameBefore() {
        java.io.InputStream failing =
                new SequenceInputStream(
                        new ByteArrayInputStream(bytes("ok")),
                        new java.io.InputStream() {
                            @Override
                            public int read() throws IOException {
                                throw new IOException("unplugged");
                            }
                        });

        try (HostStdin stdin = new HostStdin(poll, failing)) {
            InputStream stream = stdin.getStdin();

            assertArrayEquals(bytes("ok"), stream.blockingRead(len(10)));
            StreamErrorException e =
                    assertThrows(StreamErrorException.class, () -> stream.blockingRead(len(10)));
            StreamError.LastOperationFailed failed =
                    assertInstanceOf(StreamError.LastOperationFailed.class, e.error());
            assertEquals("java.io.IOException: unplugged", failed.value().toDebugString());
            assertClosed(() -> stream.read(len(1)));
        }
    }

    /**
     * The reading thread pauses once the guest leaves {@link HostStdin#CAPACITY} bytes unread, which
     * is a whole number of reads, and resumes once the guest reads them.
     */
    @Test
    void theReadingThreadPausesWhileTheGuestLeavesBytesUnread() throws InterruptedException {
        Endless source = new Endless();

        try (HostStdin stdin = new HostStdin(poll, source)) {
            InputStream stream = stdin.getStdin();
            stream.subscribe().ready();
            awaitServed(source, HostStdin.CAPACITY);
            Thread.sleep(50);
            assertEquals(HostStdin.CAPACITY, source.served.get());

            assertEquals(HostStdin.CAPACITY, stream.read(len(HostStdin.CAPACITY)).length);
            awaitServed(source, 2L * HostStdin.CAPACITY);
        }
    }

    @Test
    void aSubscriptionIsReadyOnceBytesArrive() {
        Feed feed = new Feed();

        try (HostStdin stdin = new HostStdin(poll, feed)) {
            InputStream stream = stdin.getStdin();
            Pollable pollable = stream.subscribe();
            assertFalse(pollable.ready());

            feed.send("x");
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> poll.poll(List.of(pollable)));
            assertArrayEquals(bytes("x"), stream.read(len(1)));
            assertFalse(pollable.ready());
        } finally {
            feed.end();
        }
    }

    /** Every handle reads the same bytes, so dropping one leaves the others reading. */
    @Test
    void droppingOneHandleLeavesTheOthersReading() {
        try (HostStdin stdin = new HostStdin(poll, new ByteArrayInputStream(bytes("ab")))) {
            InputStream first = stdin.getStdin();
            InputStream second = stdin.getStdin();

            assertArrayEquals(bytes("a"), first.blockingRead(len(1)));
            first.drop();
            assertArrayEquals(bytes("b"), second.blockingRead(len(1)));
        }
    }

    /** A thread blocked reading stops once its read returns, and the Java stream stays open. */
    @Test
    void closingStopsTheReadingThreadAndLeavesTheJavaStreamOpen() throws InterruptedException {
        Feed feed = new Feed();
        HostStdin stdin = new HostStdin(poll, feed);
        Set<Thread> before = readers();
        InputStream stream = stdin.getStdin();
        stream.subscribe().ready();
        Set<Thread> started = readers();
        started.removeAll(before);
        assertEquals(1, started.size());
        Thread reader = started.iterator().next();
        assertTrue(reader.isDaemon());

        stdin.close();
        feed.send("late");
        reader.join(Duration.ofSeconds(10).toMillis());

        assertFalse(reader.isAlive());
        assertFalse(feed.closed);
        assertClosed(() -> stream.read(len(1)));
    }

    /** One thread reads a stream for every host, and what it reads after one closes goes on. */
    @Test
    void aReadReturningAfterItsHostClosesGoesToTheNextHost() {
        Feed feed = new Feed();
        Set<Thread> before = readers();
        try {
            HostStdin first = new HostStdin(poll, feed);
            first.getStdin().subscribe().ready();
            feed.awaitBlockedRead();
            first.close();

            try (HostStdin second = new HostStdin(poll, feed)) {
                InputStream stream = second.getStdin();
                stream.subscribe().ready();
                Set<Thread> started = readers();
                started.removeAll(before);
                assertEquals(1, started.size());

                feed.send("hello");
                assertArrayEquals(
                        bytes("hello"),
                        assertTimeoutPreemptively(
                                Duration.ofSeconds(10), () -> stream.blockingRead(len(10))));
            }
        } finally {
            feed.end();
        }
    }

    /** A terminal ends a stream once, so an end read after its host closes ends the next one. */
    @Test
    void anEndReadAfterItsHostClosesEndsTheNextHostsStdin() throws InterruptedException {
        Feed feed = new Feed();
        Set<Thread> before = readers();
        HostStdin first = new HostStdin(poll, feed);
        first.getStdin().subscribe().ready();
        feed.awaitBlockedRead();
        Set<Thread> started = readers();
        started.removeAll(before);
        first.close();
        feed.endOnce();
        started.iterator().next().join(Duration.ofSeconds(10).toMillis());

        try (HostStdin second = new HostStdin(poll, feed)) {
            InputStream stream = second.getStdin();

            assertTimeoutPreemptively(
                    Duration.ofSeconds(10), () -> assertClosed(() -> stream.blockingRead(len(1))));
        } finally {
            feed.end();
        }
    }

    private static Set<Thread> readers() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> READER.equals(thread.getName()))
                .collect(Collectors.toCollection(HashSet::new));
    }

    private static void awaitServed(Endless source, long bytes) {
        assertTimeoutPreemptively(
                Duration.ofSeconds(10),
                () -> {
                    while (source.served.get() < bytes) {
                        Thread.sleep(1);
                    }
                });
    }

    private static byte[] readToEnd(InputStream stream) {
        ByteArrayOutputStream read = new ByteArrayOutputStream();
        try {
            while (true) {
                read.writeBytes(stream.blockingRead(len(1024)));
            }
        } catch (StreamErrorException e) {
            assertInstanceOf(StreamError.Closed.class, e.error());
        }
        return read.toByteArray();
    }

    private static void assertClosed(Runnable operation) {
        StreamErrorException e = assertThrows(StreamErrorException.class, operation::run);
        assertInstanceOf(StreamError.Closed.class, e.error());
    }

    private static byte[] bytes(String text) {
        return text.getBytes(UTF_8);
    }

    private static BigInteger len(long len) {
        return BigInteger.valueOf(len);
    }

    /** A Java stream that never ends, counting the bytes it serves. */
    private static final class Endless extends java.io.InputStream {

        private final AtomicLong served = new AtomicLong();

        @Override
        public int read() {
            served.incrementAndGet();
            return 0;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            served.addAndGet(len);
            return len;
        }
    }

    /** A Java stream serving what a test sends one piece per read, blocking until one arrives. */
    private static final class Feed extends java.io.InputStream {

        private static final byte[] END_ONCE = new byte[0];

        private final BlockingQueue<byte[]> pieces = new LinkedBlockingQueue<>();
        private final AtomicInteger blockedReads = new AtomicInteger();
        private volatile boolean closed;

        void send(String text) {
            pieces.add(bytes(text));
        }

        /** Makes every later read find the end of the stream. */
        void end() {
            pieces.add(new byte[0]);
        }

        /** Makes the next read find the end of the stream, as a terminal does once. */
        void endOnce() {
            pieces.add(END_ONCE);
        }

        void awaitBlockedRead() {
            assertTimeoutPreemptively(
                    Duration.ofSeconds(10),
                    () -> {
                        while (blockedReads.get() == 0) {
                            Thread.sleep(1);
                        }
                    });
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            try {
                byte[] piece;
                blockedReads.incrementAndGet();
                try {
                    piece = pieces.take();
                } finally {
                    blockedReads.decrementAndGet();
                }
                if (piece == END_ONCE) {
                    return -1;
                }
                if (piece.length == 0) {
                    pieces.add(piece);
                    return -1;
                }
                System.arraycopy(piece, 0, b, off, piece.length);
                return piece.length;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException();
            }
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
