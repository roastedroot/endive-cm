package io.roastedroot.typewire.wasi.p2;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.Descriptor;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.DescriptorFlags;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.OpenFlags;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.PathFlags;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.io.poll.Pollable;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.io.streams.InputStream;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.io.streams.OutputStream;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.sockets.ipnamelookup.ResolveAddressStream;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.sockets.network.IpAddress;
import io.roastedroot.typewire.wasi.p2.sockets.NetworkPolicy;
import io.roastedroot.typewire.wasi.p2.sockets.network.HostNetwork;
import io.roastedroot.zerofs.Configuration;
import io.roastedroot.zerofs.ZeroFs;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** An embedder already running Netty plugs its event loops and futures in without adapters of ours. */
class NettySwapInTest {

    private EventExecutor eventLoop;

    @BeforeEach
    void startEventLoop() {
        eventLoop = new DefaultEventExecutor();
    }

    @AfterEach
    void stopEventLoop() {
        eventLoop.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
    }

    @Test
    void anEventLoopRunsTheTimers() {
        try (var wasi = WasiPreview2.builder().withScheduler(eventLoop).build()) {
            Pollable timeout =
                    wasi.monotonicClock().subscribeDuration(BigInteger.valueOf(1_000_000));

            assertEquals(List.of(0L), wasi.poll().poll(List.of(timeout)));
        }
    }

    @Test
    void aNettyFutureBacksAPollable() {
        try (var wasi = WasiPreview2.builder().withScheduler(eventLoop).build()) {
            Promise<Void> promise = eventLoop.newPromise();
            CompletableFuture<Void> adapted = new CompletableFuture<>();
            promise.addListener(done -> adapted.complete(null));
            eventLoop.schedule(() -> promise.setSuccess(null), 20, TimeUnit.MILLISECONDS);

            assertEquals(List.of(0L), wasi.poll().poll(List.of(wasi.poll().pollable(adapted))));
            assertTrue(promise.isDone());
        }
    }

    @Test
    void aFileStreamIsPolledBesideATimerOnAnEventLoop() throws IOException {
        try (FileSystem fs = ZeroFs.newFileSystem(Configuration.unix());
                var wasi =
                        WasiPreview2.builder()
                                .withScheduler(eventLoop)
                                .withDirectory("data", Files.createDirectory(fs.getPath("/data")))
                                .build()) {
            Descriptor file =
                    wasi.preopens()
                            .getDirectories()
                            .get(0)
                            .f0()
                            .openAt(
                                    PathFlags.of(),
                                    "file.txt",
                                    OpenFlags.of(OpenFlags.Flag.CREATE),
                                    DescriptorFlags.of(
                                            DescriptorFlags.Flag.READ, DescriptorFlags.Flag.WRITE));
            OutputStream out = file.writeViaStream(BigInteger.ZERO);
            out.blockingWriteAndFlush("hello".getBytes(UTF_8));
            InputStream in = file.readViaStream(BigInteger.ZERO);
            Pollable minuteLater =
                    wasi.monotonicClock().subscribeDuration(BigInteger.valueOf(60_000_000_000L));

            assertEquals(List.of(1L), wasi.poll().poll(List.of(minuteLater, in.subscribe())));
            assertArrayEquals("hello".getBytes(UTF_8), in.blockingRead(BigInteger.TEN));
            assertArrayEquals(
                    "hello".getBytes(UTF_8), Files.readAllBytes(fs.getPath("/data/file.txt")));
        }
    }

    @Test
    void aGuestGrantedTheNetworkRunsTimersOnTheGivenEventLoopGroup() {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        try {
            try (var wasi =
                    WasiPreview2.builder()
                            .withScheduler(group)
                            .withNetworkPolicy(NetworkPolicy.allowAll())
                            .build()) {
                Pollable timeout =
                        wasi.monotonicClock().subscribeDuration(BigInteger.valueOf(1_000_000));

                assertEquals(List.of(0L), wasi.poll().poll(List.of(timeout)));
            }
            assertFalse(group.isShuttingDown());
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
    }

    @Test
    void theGuestIsGrantedTheNetworkPolicyGiven() {
        NetworkPolicy policy = NetworkPolicy.allowAll();
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        try (var wasi =
                WasiPreview2.builder().withScheduler(group).withNetworkPolicy(policy).build()) {
            HostNetwork network = HostNetwork.of(wasi.instanceNetwork().instanceNetwork());

            assertSame(policy, network.policy());
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
    }

    @Test
    void aGuestGrantedTheNetworkNeedsNoScheduler() {
        try (var wasi =
                WasiPreview2.builder().withNetworkPolicy(NetworkPolicy.allowAll()).build()) {
            Pollable timeout =
                    wasi.monotonicClock().subscribeDuration(BigInteger.valueOf(1_000_000));

            assertEquals(List.of(0L), wasi.poll().poll(List.of(timeout)));
            assertNotNull(firstAddressOf(wasi, "localhost"));
        }
    }

    /** Netty's resolver answers localhost from the hosts file, on the group the network made. */
    @Test
    void aSchedulerOutsideNettyKeepsTheTimersWhileTheNetworkRunsElsewhere() {
        try (var wasi =
                WasiPreview2.builder()
                        .withScheduler(eventLoop)
                        .withNetworkPolicy(NetworkPolicy.allowAll())
                        .build()) {
            Pollable timeout =
                    wasi.monotonicClock().subscribeDuration(BigInteger.valueOf(1_000_000));

            assertEquals(List.of(0L), wasi.poll().poll(List.of(timeout)));
            assertNotNull(firstAddressOf(wasi, "localhost"));
        }
        assertFalse(eventLoop.isShuttingDown());
    }

    /** A build failing once the network has started leaves none of the network's threads running. */
    @Test
    void aFailedBuildReleasesTheNetwork() throws IOException, InterruptedException {
        Set<Thread> before = networkThreads();
        FileSystem fs = ZeroFs.newFileSystem(Configuration.unix());
        var builder =
                WasiPreview2.builder()
                        .withNetworkPolicy(NetworkPolicy.allowAll())
                        .withDirectory("data", Files.createDirectory(fs.getPath("/data")));
        fs.close();

        assertThrows(ClosedFileSystemException.class, builder::build);
        for (Thread thread : networkThreads()) {
            if (!before.contains(thread)) {
                thread.join(5_000);
                assertFalse(thread.isAlive(), thread.getName());
            }
        }
    }

    private static IpAddress firstAddressOf(WasiPreview2 wasi, String name) {
        ResolveAddressStream stream =
                wasi.ipNameLookup()
                        .resolveAddresses(wasi.instanceNetwork().instanceNetwork(), name);
        wasi.poll().poll(List.of(stream.subscribe()));
        return stream.resolveNextAddress();
    }

    private static Set<Thread> networkThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.getName().startsWith("typewire-wasi-p2-network"))
                .collect(Collectors.toSet());
    }
}
