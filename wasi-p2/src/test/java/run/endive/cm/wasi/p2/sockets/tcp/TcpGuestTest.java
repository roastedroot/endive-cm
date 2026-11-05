package run.endive.cm.wasi.p2.sockets.tcp;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import run.endive.cm.runtime.ComponentInstance;
import run.endive.cm.runtime.ComponentStore;
import run.endive.cm.types.WasmComponent;
import run.endive.cm.wasi.p2.Guests;
import run.endive.cm.wasi.p2.WasiPreview2;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.ErrorCode;
import run.endive.cm.wasi.p2.sockets.NetworkPolicy;
import run.endive.cm.wasi.p2.sockets.NetworkPolicy.AddressUse;

/**
 * A guest serving and making TCP connections over loopback through {@code wasi:sockets/tcp}, end
 * to end through the generated world, on the network's own event loop.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class TcpGuestTest {

    private static final NetworkPolicy LOOPBACK =
            NetworkPolicy.builder()
                    .allowTcp()
                    .allowAddresses((address, use) -> address.getAddress().isLoopbackAddress())
                    .build();

    private static WasmComponent component;

    @BeforeAll
    static void buildComponent(@TempDir Path temp) {
        component = Guests.build("tcp", temp);
    }

    @Test
    void aGuestServesAHostClient() throws Exception {
        try (var wasi = wasi(LOOPBACK)) {
            ComponentInstance guest = instantiate(wasi);
            int port = listen(guest);

            CompletableFuture<String> reply =
                    CompletableFuture.supplyAsync(() -> exchange(port, "hello"));
            guest.export("echo-one").apply();

            assertEquals("hello", reply.get());
        }
    }

    @Test
    void aGuestRequestsFromAHostServer() throws Exception {
        try (var wasi = wasi(LOOPBACK);
                ServerSocket server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            CompletableFuture<Void> serving = CompletableFuture.runAsync(() -> shout(server));

            Object reply = request(instantiate(wasi), server.getLocalPort(), "ping");

            assertEquals("PING", reply);
            serving.get();
        }
    }

    /** The message and the reply each take many reads and writes. */
    @Test
    void twoGuestsExchangeMoreThanOneReadHolds() throws Exception {
        String message = "0123456789abcdef".repeat(2048);

        try (var wasi = wasi(LOOPBACK)) {
            ComponentInstance server = instantiate(wasi);
            int port = listen(server);

            CompletableFuture<Void> serving =
                    CompletableFuture.runAsync(() -> server.export("echo-one").apply());
            Object reply = request(instantiate(wasi), port, message);

            assertEquals(message, reply);
            serving.get();
        }
    }

    @Test
    void aRefusedConnectionReachesTheGuest() throws IOException {
        try (var wasi = wasi(LOOPBACK)) {
            assertEquals(code(ErrorCode.CONNECTION_REFUSED), connectError(wasi, freePort()));
        }
    }

    @Test
    void aGuestIsDeniedTcpByDefault() throws IOException {
        try (var wasi = WasiPreview2.builder().build()) {
            assertEquals(code(ErrorCode.ACCESS_DENIED), connectError(wasi, freePort()));
        }
    }

    @Test
    void aGuestIsDeniedAnAddressThePolicyRefuses() throws IOException {
        NetworkPolicy bindOnly =
                NetworkPolicy.builder()
                        .allowTcp()
                        .allowAddresses((address, use) -> use == AddressUse.TCP_BIND)
                        .build();

        try (var wasi = wasi(bindOnly)) {
            assertEquals(code(ErrorCode.ACCESS_DENIED), connectError(wasi, freePort()));
        }
    }

    private static WasiPreview2 wasi(NetworkPolicy policy) {
        return WasiPreview2.builder().withNetworkPolicy(policy).build();
    }

    private static ComponentInstance instantiate(WasiPreview2 wasi) {
        return wasi.instantiate(new ComponentStore(), component).instance();
    }

    private static int listen(ComponentInstance guest) {
        return ((Number) guest.export("listen").apply()[0]).intValue();
    }

    private static Object request(ComponentInstance guest, int port, String message) {
        return guest.export("request").apply(port, message)[0];
    }

    private static int connectError(WasiPreview2 wasi, int port) {
        return ((Number) instantiate(wasi).export("connect-error").apply(port)[0]).intValue();
    }

    /** Sends {@code message}, stops sending, and returns the whole reply. */
    private static String exchange(int port, String message) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
            socket.getOutputStream().write(message.getBytes(UTF_8));
            socket.shutdownOutput();
            return new String(socket.getInputStream().readAllBytes(), UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Accepts one connection and replies with what it sends, in upper case. */
    private static void shout(ServerSocket server) {
        try (Socket socket = server.accept()) {
            String message = new String(socket.getInputStream().readAllBytes(), UTF_8);
            socket.getOutputStream().write(message.toUpperCase(Locale.ROOT).getBytes(UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    private static int code(ErrorCode code) {
        return code.ordinal();
    }
}
