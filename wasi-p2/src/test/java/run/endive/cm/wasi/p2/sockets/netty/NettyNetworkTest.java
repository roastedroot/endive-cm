package run.endive.cm.wasi.p2.sockets.netty;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoEventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.local.LocalIoHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import run.endive.cm.wasi.p2.io.poll.HostPoll;
import run.endive.cm.wasi.p2.sockets.NetworkPolicy;

class NettyNetworkTest {

    @Test
    void theNetworkRunsOnTheSchedulersGroupAndLeavesItRunning() {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        try {
            NettyNetwork network =
                    NettyNetwork.start(new HostPoll(), NetworkPolicy.allowAll(), group, null);

            assertSame(group, network.eventLoopGroup());
            network.close();
            assertFalse(group.isShuttingDown());
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
    }

    @Test
    void aSchedulerOutsideNettyLeavesTheNetworkAGroupOfItsOwn() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            NettyNetwork network =
                    NettyNetwork.start(new HostPoll(), NetworkPolicy.allowAll(), scheduler, null);
            IoEventLoopGroup group = (IoEventLoopGroup) network.eventLoopGroup();

            assertNotSame(scheduler, group);
            assertTrue(group.isIoType(NioIoHandler.class));
            network.close();
            assertTrue(group.isShuttingDown());
            assertFalse(scheduler.isShutdown());
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void noSchedulerLeavesTheNetworkAGroupOfItsOwn() {
        NettyNetwork network =
                NettyNetwork.start(new HostPoll(), NetworkPolicy.allowAll(), null, null);
        IoEventLoopGroup group = (IoEventLoopGroup) network.eventLoopGroup();

        network.close();

        assertTrue(group.isShuttingDown());
    }

    @Test
    void aGroupRunningAnotherTransportIsRefused() {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, LocalIoHandler.newFactory());
        try {
            IllegalArgumentException e =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    NettyNetwork.start(
                                            new HostPoll(), NetworkPolicy.allowAll(), group, null));

            assertTrue(e.getMessage().contains("io_uring"), e.getMessage());
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
    }

    @Test
    void theDefaultResolverCreatesItsChannelsWithThoseGiven() {
        AtomicInteger datagrams = new AtomicInteger();
        NettyChannels channels =
                NettyChannels.of(
                        family -> new NioSocketChannel(),
                        family -> new NioServerSocketChannel(),
                        () -> {
                            datagrams.incrementAndGet();
                            return new NioDatagramChannel();
                        });
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        try {
            NettyNetwork.start(new HostPoll(), NetworkPolicy.allowAll(), group, null, channels)
                    .close();

            assertTrue(datagrams.get() > 0, "datagram channels " + datagrams.get());
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
    }
}
