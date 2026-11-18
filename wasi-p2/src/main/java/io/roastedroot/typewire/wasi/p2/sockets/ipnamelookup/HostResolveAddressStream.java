package io.roastedroot.typewire.wasi.p2.sockets.ipnamelookup;

import io.roastedroot.typewire.wasi.p2.bindings.wasi.io.poll.Pollable;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.sockets.ipnamelookup.ResolveAddressStream;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.sockets.network.ErrorCode;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.sockets.network.IpAddress;
import io.roastedroot.typewire.wasi.p2.io.poll.HostPoll;
import io.roastedroot.typewire.wasi.p2.sockets.network.ErrorCodes;
import io.roastedroot.typewire.wasi.p2.sockets.network.IpAddresses;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * The addresses one lookup finds, handed to the guest one at a time once the lookup completes.
 *
 * <p>A failed lookup is reported once, after which the stream is exhausted.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/ip-name-lookup.wit">wasi:sockets/ip-name-lookup</a>
 */
final class HostResolveAddressStream implements ResolveAddressStream {

    private final HostPoll poll;
    private final CompletableFuture<List<InetAddress>> lookup;
    private Iterator<InetAddress> remaining;

    HostResolveAddressStream(HostPoll poll, CompletableFuture<List<InetAddress>> lookup) {
        this.poll = poll;
        this.lookup = lookup;
    }

    /**
     * The next address, or {@code null} once every one has been returned.
     *
     * @throws io.roastedroot.typewire.wasi.p2.bindings.wasi.sockets.network.ErrorCodeException with {@code
     *     would-block} until the lookup completes, or with the code its failure stands for
     */
    @Override
    public IpAddress resolveNextAddress() {
        if (remaining == null) {
            if (!lookup.isDone()) {
                throw ErrorCodes.error(ErrorCode.WOULD_BLOCK);
            }
            remaining = Collections.emptyIterator();
            try {
                remaining = lookup.join().iterator();
            } catch (CompletionException | CancellationException e) {
                throw ErrorCodes.error(codeOf(e));
            }
        }
        return remaining.hasNext()
                ? IpAddresses.fromInet(IpAddresses.unmapped(remaining.next()))
                : null;
    }

    /**
     * The code a failed lookup stands for. A lookup timing out may succeed later, so that is a
     * temporary failure rather than an unresolvable name.
     */
    private static ErrorCode codeOf(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException) {
                return ErrorCode.TEMPORARY_RESOLVER_FAILURE;
            }
        }
        ErrorCode code = ErrorCodes.codeOf(failure);
        return code == null || code == ErrorCode.UNKNOWN
                ? ErrorCode.PERMANENT_RESOLVER_FAILURE
                : code;
    }

    @Override
    public Pollable subscribe() {
        return poll.pollable(lookup);
    }

    /** Cancels a lookup still in progress, which a resolver may use to stop its work. */
    @Override
    public void drop() {
        lookup.cancel(false);
    }
}
