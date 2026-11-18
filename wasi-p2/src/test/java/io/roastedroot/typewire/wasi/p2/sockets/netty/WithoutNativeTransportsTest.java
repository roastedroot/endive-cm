package io.roastedroot.typewire.wasi.p2.sockets.netty;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.local.LocalIoHandler;
import io.netty.channel.nio.NioIoHandler;
import io.roastedroot.typewire.wasi.p2.WasiPreview2;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.sockets.ipnamelookup.ResolveAddressStream;
import io.roastedroot.typewire.wasi.p2.sockets.NetworkPolicy;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * An embedder with only Netty's NIO transport runs the network, and is told which transports work
 * when its scheduler runs another. The build runs this with the native transports left off the
 * class path, and nothing else does.
 */
class WithoutNativeTransportsTest {

    @Test
    void theNativeTransportsAreMissing() {
        for (String handler :
                List.of(
                        "io.netty.channel.epoll.EpollIoHandler",
                        "io.netty.channel.uring.IoUringIoHandler",
                        "io.netty.channel.kqueue.KQueueIoHandler")) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(handler), handler);
        }
    }

    @Test
    void theNetworkRunsOnANioGroup() {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        try (var wasi =
                WasiPreview2.builder()
                        .withScheduler(group)
                        .withNetworkPolicy(NetworkPolicy.allowAll())
                        .build()) {
            ResolveAddressStream stream =
                    wasi.ipNameLookup()
                            .resolveAddresses(
                                    wasi.instanceNetwork().instanceNetwork(), "localhost");

            wasi.poll().poll(List.of(stream.subscribe()));
            assertNotNull(stream.resolveNextAddress());
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
    }

    @Test
    void aGroupRunningAnotherTransportIsRefused() {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, LocalIoHandler.newFactory());
        try {
            var builder =
                    WasiPreview2.builder()
                            .withScheduler(group)
                            .withNetworkPolicy(NetworkPolicy.allowAll());

            IllegalArgumentException e =
                    assertThrows(IllegalArgumentException.class, builder::build);

            assertTrue(e.getMessage().contains("NIO"), e.getMessage());
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
    }
}
