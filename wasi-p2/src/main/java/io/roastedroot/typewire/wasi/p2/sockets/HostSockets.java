package io.roastedroot.typewire.wasi.p2.sockets;

import java.util.concurrent.ScheduledExecutorService;

/**
 * The {@code wasi:sockets} interfaces a guest is given. {@link HostSocketsFactory} creates them to
 * suit the network policy.
 */
public interface HostSockets extends AutoCloseable {

    /** The event loop group that runs the network, or {@code null} for none. */
    ScheduledExecutorService eventLoopGroup();

    io.roastedroot.typewire.wasi.p2.bindings.wasi.sockets.ipnamelookup.Host ipNameLookup();

    io.roastedroot.typewire.wasi.p2.bindings.wasi.sockets.tcpcreatesocket.Host tcpCreateSocket();

    io.roastedroot.typewire.wasi.p2.bindings.wasi.sockets.udpcreatesocket.Host udpCreateSocket();

    /** Releases what this created, leaving the scheduler and what the embedder gave running. */
    @Override
    void close();
}
