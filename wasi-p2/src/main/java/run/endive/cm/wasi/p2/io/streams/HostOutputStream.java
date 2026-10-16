package run.endive.cm.wasi.p2.io.streams;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import java.math.BigInteger;
import java.util.Arrays;
import run.endive.cm.wasi.p2.bindings.wasi.io.poll.Pollable;
import run.endive.cm.wasi.p2.bindings.wasi.io.streams.InputStream;
import run.endive.cm.wasi.p2.bindings.wasi.io.streams.OutputStream;
import run.endive.cm.wasi.p2.io.Blocking;
import run.endive.cm.wasi.p2.io.poll.HostPoll;
import run.endive.runtime.TrapException;

/**
 * The host side of a {@code wasi:io/streams} output stream, built on operations a sink implements
 * without blocking. Everything else the interface asks for, including the blocking variants and
 * splicing, is derived from those.
 *
 * <p>A write may carry no more bytes than the last {@code check-write} permitted, and the guest
 * traps when it does. A flush withdraws the permit, so the guest has to check again before writing.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/io/wit/streams.wit">wasi:io/streams</a>
 */
public abstract class HostOutputStream implements OutputStream {

    /** The most a blocking write may carry, which the interface fixes. */
    static final int MAX_BLOCKING_WRITE = 4096;

    private final EventExecutor eventLoopExecutor;
    private final HostPoll poll;
    private long permit;

    protected HostOutputStream(EventExecutor eventLoopExecutor, HostPoll poll) {
        this.eventLoopExecutor = eventLoopExecutor;
        this.poll = poll;
    }

    /**
     * How many bytes the sink takes now, which is none while a flush is in progress.
     *
     * @throws run.endive.cm.wasi.p2.bindings.wasi.io.streams.StreamErrorException once the stream
     *     has failed or closed
     */
    protected abstract long writeCapacity();

    /**
     * Accepts {@code contents}, which never exceeds the last {@link #writeCapacity}.
     *
     * @throws run.endive.cm.wasi.p2.bindings.wasi.io.streams.StreamErrorException once the stream
     *     has failed or closed
     */
    protected abstract void writeBytes(byte[] contents);

    /**
     * Begins flushing everything written so far, after which {@link #writeCapacity} is zero until
     * the flush completes.
     *
     * @throws run.endive.cm.wasi.p2.bindings.wasi.io.streams.StreamErrorException once the stream
     *     has failed or closed
     */
    protected abstract void startFlush();

    /** A future that is complete while {@link #writeCapacity} would be positive or fail. */
    public abstract Future<?> readiness();

    @Override
    public BigInteger checkWrite() {
        return BigInteger.valueOf(checkPermit());
    }

    @Override
    public void write(byte[] contents) {
        takePermit(contents.length);
        writeBytes(contents);
    }

    @Override
    public void blockingWriteAndFlush(byte[] contents) {
        if (contents.length > MAX_BLOCKING_WRITE) {
            throw new TrapException(
                    "blocking-write-and-flush of "
                            + contents.length
                            + " bytes exceeds the limit of "
                            + MAX_BLOCKING_WRITE);
        }
        int offset = 0;
        while (offset < contents.length) {
            int chunk = (int) Math.min(awaitPermit(), contents.length - offset);
            write(Arrays.copyOfRange(contents, offset, offset + chunk));
            offset += chunk;
        }
        blockingFlush();
    }

    @Override
    public void flush() {
        permit = 0;
        startFlush();
    }

    @Override
    public void blockingFlush() {
        flush();
        awaitPermit();
    }

    @Override
    public Pollable subscribe() {
        return poll.pollable(this::readiness);
    }

    @Override
    public void writeZeroes(BigInteger len) {
        write(new byte[permittedLength(len)]);
    }

    @Override
    public void blockingWriteZeroesAndFlush(BigInteger len) {
        if (len.compareTo(BigInteger.valueOf(MAX_BLOCKING_WRITE)) > 0) {
            throw new TrapException(
                    "blocking-write-zeroes-and-flush of "
                            + len
                            + " bytes exceeds the limit of "
                            + MAX_BLOCKING_WRITE);
        }
        blockingWriteAndFlush(new byte[len.intValueExact()]);
    }

    /** A {@code check-write}, then a {@code read} of what it permits, then a {@code write}. */
    @Override
    public BigInteger splice(InputStream src, BigInteger len) {
        long permitted = checkPermit();
        if (permitted == 0) {
            return BigInteger.ZERO;
        }
        byte[] bytes = src.read(len.min(BigInteger.valueOf(permitted)));
        write(bytes);
        return BigInteger.valueOf(bytes.length);
    }

    /** As {@link #splice}, once this stream may be written and {@code src} may be read. */
    @Override
    public BigInteger blockingSplice(InputStream src, BigInteger len) {
        long permitted = awaitPermit();
        byte[] bytes = src.blockingRead(len.min(BigInteger.valueOf(permitted)));
        write(bytes);
        return BigInteger.valueOf(bytes.length);
    }

    private long checkPermit() {
        permit = writeCapacity();
        return permit;
    }

    /** Waits until the sink takes at least one byte, and gives back the permit. */
    private long awaitPermit() {
        while (checkPermit() == 0) {
            Blocking.await(eventLoopExecutor, readiness());
        }
        return permit;
    }

    private void takePermit(long length) {
        if (length > permit) {
            throw exceedsPermit(length);
        }
        permit -= length;
    }

    /** Traps before allocating anything for a length the permit does not cover. */
    private int permittedLength(BigInteger len) {
        if (len.compareTo(BigInteger.valueOf(permit)) > 0) {
            throw exceedsPermit(len);
        }
        return len.intValueExact();
    }

    private TrapException exceedsPermit(Object length) {
        return new TrapException(
                "write of "
                        + length
                        + " bytes exceeds the "
                        + permit
                        + " permitted by check-write");
    }
}
