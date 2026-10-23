package run.endive.cm.wasi.p2.cli.stdin;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import run.endive.cm.wasi.p2.io.streams.BufferedHostInputStream;

/**
 * Reads one Java stream for every host that hands it to a guest as stdin, so that at most one
 * thread waits on it. What it reads goes to the buffer attached most recently.
 *
 * <p>A read cannot be interrupted. One that returns while no buffer is attached keeps its bytes,
 * or the end of the stream, for the next buffer to attach.
 */
final class StdinReader {

    private static final Map<InputStream, StdinReader> READERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** Held weakly so that {@link #READERS} forgets a stream nobody reads any more. */
    private final WeakReference<InputStream> source;

    private BufferedHostInputStream target;
    private boolean reading;
    private boolean demanded;
    private byte[] pendingBytes;
    private boolean pendingEnd;
    private Throwable pendingFailure;

    private StdinReader(InputStream source) {
        this.source = new WeakReference<>(source);
    }

    /** The reader of {@code source}, shared by every host given it. */
    static StdinReader of(InputStream source) {
        return READERS.computeIfAbsent(source, StdinReader::new);
    }

    /**
     * Sends what the stream gives next to {@code buffer}, starting with anything a read returned
     * while no buffer was attached.
     */
    synchronized void attach(BufferedHostInputStream buffer) {
        target = buffer;
        demand();
        if (pendingBytes != null) {
            buffer.offer(pendingBytes);
            pendingBytes = null;
        }
        if (pendingEnd) {
            end(pendingFailure);
            pendingEnd = false;
            pendingFailure = null;
        } else if (!reading) {
            reading = true;
            InputStream stream = source.get();
            Thread reader = new Thread(() -> pump(stream), "endive-wasi-p2-stdin");
            reader.setDaemon(true);
            reader.start();
        }
    }

    /** Stops sending to {@code buffer}, if it is still the one attached. */
    synchronized void detach(BufferedHostInputStream buffer) {
        if (target == buffer) {
            target = null;
            notifyAll();
        }
    }

    /** Resumes a read paused because the attached buffer was full. */
    synchronized void demand() {
        demanded = true;
        notifyAll();
    }

    private void pump(InputStream stream) {
        byte[] chunk = new byte[HostStdin.CHUNK_SIZE];
        try {
            int read;
            while ((read = stream.read(chunk)) >= 0) {
                if (!deliver(Arrays.copyOf(chunk, read))) {
                    return;
                }
            }
            finish(null);
        } catch (IOException | RuntimeException e) {
            finish(e);
        }
    }

    /**
     * Hands {@code bytes} to the attached buffer, waiting while it is full.
     *
     * @return whether to keep reading, which is {@code false} once no buffer is attached
     */
    private synchronized boolean deliver(byte[] bytes) {
        if (target == null) {
            pendingBytes = bytes;
            reading = false;
            return false;
        }
        if (!target.offer(bytes)) {
            BufferedHostInputStream full = target;
            demanded = false;
            boolean interrupted = false;
            while (!demanded && target == full) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        if (target == null) {
            reading = false;
            return false;
        }
        return true;
    }

    /** Ends the attached buffer, or the next one to attach, as the stream ended. */
    private synchronized void finish(Throwable failure) {
        reading = false;
        if (target == null) {
            pendingEnd = true;
            pendingFailure = failure;
        } else {
            end(failure);
        }
    }

    private void end(Throwable failure) {
        if (failure == null) {
            target.close();
        } else {
            target.fail(failure);
        }
    }
}
