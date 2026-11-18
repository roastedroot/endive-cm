package io.roastedroot.typewire.wasi.p2.sockets;

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Resolves the domain names a guest looks up through {@code wasi:sockets/ip-name-lookup}.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/ip-name-lookup.wit">wasi:sockets/ip-name-lookup</a>
 */
public interface NameResolver {

    /**
     * Starts resolving {@code name}, which never blocks. The host has already converted the name to
     * ASCII and checked that it is valid.
     *
     * <p>The lookup completes exceptionally with an {@link java.net.UnknownHostException} when the
     * name has no address, or with a {@link java.net.SocketTimeoutException} when no server
     * answered in time and a later lookup may succeed. The host cancels the lookup once the guest
     * stops waiting for it.
     */
    CompletableFuture<List<InetAddress>> resolveAll(String name);
}
