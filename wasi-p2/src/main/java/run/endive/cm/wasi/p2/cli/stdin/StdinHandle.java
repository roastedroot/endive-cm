package run.endive.cm.wasi.p2.cli.stdin;

import java.math.BigInteger;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import run.endive.cm.wasi.p2.io.poll.HostPoll;
import run.endive.cm.wasi.p2.io.streams.BufferedHostInputStream;
import run.endive.cm.wasi.p2.io.streams.HostInputStream;

/**
 * One handle the guest holds to stdin. Every handle reads the same buffer, so dropping one leaves
 * the buffer open for the others. The buffer is asked for only once the guest reads or polls.
 */
final class StdinHandle extends HostInputStream {

    private final Supplier<BufferedHostInputStream> shared;

    StdinHandle(HostPoll poll, Supplier<BufferedHostInputStream> shared) {
        super(poll);
        this.shared = shared;
    }

    /** Guests on several threads may share stdin, so the buffer takes one read at a time. */
    @Override
    protected byte[] readAvailable(int max) {
        BufferedHostInputStream buffer = shared.get();
        synchronized (buffer) {
            return buffer.read(BigInteger.valueOf(max));
        }
    }

    @Override
    public CompletableFuture<?> readiness() {
        return shared.get().readiness();
    }
}
