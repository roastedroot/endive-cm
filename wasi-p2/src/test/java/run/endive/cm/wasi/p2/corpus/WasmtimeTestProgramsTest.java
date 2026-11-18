package run.endive.cm.wasi.p2.corpus;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueIoHandler;
import io.netty.channel.uring.IoUring;
import io.netty.channel.uring.IoUringIoHandler;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import run.endive.cm.corpus.ComponentCorpus;
import run.endive.cm.parser.ComponentParser;
import run.endive.cm.runtime.ComponentStore;
import run.endive.cm.types.WasmComponent;
import run.endive.cm.wasi.p2.WasiPreview2;
import run.endive.cm.wasi.p2.sockets.NetworkPolicy;
import run.endive.runtime.TrapException;

/**
 * Wasmtime's own tests of WASI 0.2 sockets, each run as a command on every Netty transport
 * available here. The host is set up as wasmtime's test harness sets up its own, and a program
 * passes when it exits with success, or traps where wasmtime's harness expects it to.
 *
 * @see <a href="https://github.com/bytecodealliance/wasmtime/tree/v50.0.1/crates/test-programs/src/bin">wasmtime's test programs</a>
 * @see <a href="https://github.com/bytecodealliance/wasmtime/blob/v50.0.1/crates/wasi/tests/all/store.rs">wasmtime's test harness</a>
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class WasmtimeTestProgramsTest {

    private static final String DIRECTORY = "wasmtime/";
    private static final String WASM = ".wasm";

    private static final String NO_HOP_LIMIT_ON_NIO =
            "Java offers NIO no way to read or set the hop limit";

    /** Programs that fail on a transport, with the reason. */
    private static final Map<Transport, Map<String, String>> KNOWN_FAILURES =
            Map.of(
                    Transport.NIO,
                    Map.of(
                            "p2_tcp_sockopts", NO_HOP_LIMIT_ON_NIO,
                            "p2_tcp_states", NO_HOP_LIMIT_ON_NIO,
                            "p2_udp_sockopts", NO_HOP_LIMIT_ON_NIO,
                            "p2_udp_states", NO_HOP_LIMIT_ON_NIO,
                            "p2_udp_sample_application",
                                    "Java's NIO sends no empty datagram on a connected channel",
                            "p2_udp_stream",
                                    "The JDK discards the datagrams waiting as it connects a"
                                            + " channel"),
                    Transport.IO_URING,
                    Map.of(
                            "p2_udp_stream",
                            "Netty's io_uring reads each datagram as it arrives, so one"
                                    + " receive may take only some of those sent together,"
                                    + " which wasmtime notes the spec allows"),
                    Transport.KQUEUE,
                    Map.of(
                            "p2_tcp_sockopts", "Netty offers no keep-alive timing for kqueue",
                            "p2_tcp_states", "Netty offers no keep-alive timing for kqueue",
                            "p2_udp_stream",
                                    "Netty's kqueue takes every datagram a connected channel"
                                            + " receives to come from its remote"));

    /** Programs wasmtime skips on macOS, with the reason. */
    private static final Map<String, String> SKIPPED_ON_MACOS =
            Map.of(
                    "p2_udp_send_to_closed_receiver",
                    "macOS limits the rate of the ICMP messages the program waits for");

    /** Programs that pass by trapping as wasmtime's harness expects, with what the trap says. */
    private static final Map<String, String> TRAPS =
            Map.of("p2_udp_send_too_much", "check-send permitted");

    /** The Netty transports a network can run on. */
    enum Transport {
        NIO(null, () -> true),
        EPOLL(EpollIoHandler::newFactory, Epoll::isAvailable),
        IO_URING(IoUringIoHandler::newFactory, IoUring::isAvailable),
        KQUEUE(KQueueIoHandler::newFactory, KQueue::isAvailable);

        /** Creates the transport's handlers, or {@code null} for the network's own NIO group. */
        private final Supplier<IoHandlerFactory> handlers;

        private final BooleanSupplier available;

        Transport(Supplier<IoHandlerFactory> handlers, BooleanSupplier available) {
            this.handlers = handlers;
            this.available = available;
        }

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    static Stream<Arguments> programs() {
        return ComponentCorpus.entries().stream()
                .filter(entry -> entry.startsWith(DIRECTORY) && entry.endsWith(WASM))
                .map(entry -> entry.substring(DIRECTORY.length(), entry.length() - WASM.length()))
                .flatMap(
                        program ->
                                Arrays.stream(Transport.values())
                                        .filter(transport -> transport.available.getAsBoolean())
                                        .map(transport -> Arguments.of(program, transport)));
    }

    @ParameterizedTest(name = "{0} on {1}")
    @MethodSource("programs")
    void passes(String program, Transport transport) {
        String knownFailure = KNOWN_FAILURES.getOrDefault(transport, Map.of()).get(program);
        if (knownFailure == null && isMacOs()) {
            knownFailure = SKIPPED_ON_MACOS.get(program);
        }
        assumeTrue(knownFailure == null, knownFailure);
        WasmComponent component = parse(ComponentCorpus.readBytes(DIRECTORY + program + WASM));
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        EventLoopGroup group =
                transport.handlers == null
                        ? null
                        : new MultiThreadIoEventLoopGroup(1, transport.handlers.get());

        WasiPreview2.Builder builder =
                WasiPreview2.builder()
                        .withArguments(List.of(program, "."))
                        .withNetworkPolicy(policy(program))
                        .withStdout(stdout)
                        .withStderr(stderr);
        if (!hasIpv6Loopback()) {
            builder.withEnvironment("DISABLE_IPV6", "1");
        }
        if (group != null) {
            builder.withScheduler(group);
        }
        int exitCode;
        try (WasiPreview2 wasi = builder.build()) {
            exitCode = wasi.run(new ComponentStore(), component);
        } catch (RuntimeException e) {
            if (TRAPS.containsKey(program) && isTrap(e, TRAPS.get(program))) {
                return;
            }
            throw new AssertionError("The program trapped." + output(stdout, stderr), e);
        } finally {
            if (group != null) {
                group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
            }
        }

        assertFalse(
                TRAPS.containsKey(program), "The program did not trap." + output(stdout, stderr));
        assertEquals(0, exitCode, output(stdout, stderr));
    }

    private static boolean isTrap(Throwable e, String says) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof TrapException && cause.getMessage().contains(says)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMacOs() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("mac");
    }

    private static String output(ByteArrayOutputStream stdout, ByteArrayOutputStream stderr) {
        return "\nstdout:\n" + stdout.toString(UTF_8) + "\nstderr:\n" + stderr.toString(UTF_8);
    }

    /**
     * Everything, as wasmtime's harness grants, except what a {@code p2_cli_no_*} program checks is
     * denied.
     */
    private static NetworkPolicy policy(String program) {
        NetworkPolicy.Builder policy =
                NetworkPolicy.builder().allowAddresses((address, use) -> true);
        if (!program.equals("p2_cli_no_tcp")) {
            policy.allowTcp();
        }
        if (!program.equals("p2_cli_no_udp")) {
            policy.allowUdp();
        }
        if (!program.equals("p2_cli_no_ip_name_lookup")) {
            policy.allowIpNameLookup();
        }
        return policy.build();
    }

    /** Whether the host has IPv6 loopback, which wasmtime's harness checks the same way. */
    private static boolean hasIpv6Loopback() {
        try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getByName("::1"))) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static WasmComponent parse(byte[] bytes) {
        return ComponentParser.builder()
                .withValidation(false)
                .build()
                .parse(() -> new ByteArrayInputStream(bytes));
    }
}
