package run.endive.cm.wasi.p2.sockets.udp;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
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
import run.endive.runtime.TrapException;

/**
 * A guest sending and receiving datagrams over loopback through {@code wasi:sockets/udp}, end to
 * end through the generated world, on the network's own event loop.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class UdpGuestTest {

    private static final NetworkPolicy LOOPBACK =
            NetworkPolicy.builder()
                    .allowUdp()
                    .allowAddresses((address, use) -> address.getAddress().isLoopbackAddress())
                    .build();

    private static WasmComponent component;

    @BeforeAll
    static void buildComponent(@TempDir Path temp) {
        component = Guests.build("udp", temp);
    }

    /** The datagram waits on the guest's socket until the guest receives it. */
    @Test
    void aGuestEchoesAHostDatagram() throws IOException {
        try (var wasi = wasi(LOOPBACK);
                DatagramSocket host = loopbackSocket()) {
            ComponentInstance guest = instantiate(wasi);
            int port = bind(guest);

            host.send(datagram("hello", new InetSocketAddress(host.getLocalAddress(), port)));
            guest.export("echo-one").apply();

            assertEquals("hello", receive(host));
        }
    }

    @Test
    void aGuestRequestsFromAHostServer() throws Exception {
        try (var wasi = wasi(LOOPBACK);
                DatagramSocket server = loopbackSocket()) {
            CompletableFuture<Void> serving = CompletableFuture.runAsync(() -> shout(server));

            Object reply = request(instantiate(wasi), server.getLocalPort(), "ping");

            assertEquals("PING", reply);
            serving.get();
        }
    }

    @Test
    void twoGuestsExchangeDatagrams() throws Exception {
        try (var wasi = wasi(LOOPBACK)) {
            ComponentInstance server = instantiate(wasi);
            int port = bind(server);

            CompletableFuture<Void> serving =
                    CompletableFuture.runAsync(() -> server.export("echo-one").apply());
            Object reply = request(instantiate(wasi), port, "between guests");

            assertEquals("between guests", reply);
            serving.get();
        }
    }

    /** The host refuses the datagram, which the guest's limited socket hears of as it receives. */
    @Test
    void aRefusedDatagramReachesTheGuest() throws IOException {
        try (var wasi = wasi(LOOPBACK)) {
            assertEquals(code(ErrorCode.CONNECTION_REFUSED), requestError(wasi, freePort()));
        }
    }

    @Test
    void aGuestIsDeniedUdpByDefault() throws IOException {
        try (var wasi = WasiPreview2.builder().build()) {
            assertEquals(code(ErrorCode.ACCESS_DENIED), requestError(wasi, freePort()));
        }
    }

    @Test
    void aGuestIsDeniedARemoteThePolicyRefuses() throws IOException {
        try (var wasi = wasi(allowing(AddressUse.UDP_BIND))) {
            assertEquals(code(ErrorCode.ACCESS_DENIED), requestError(wasi, freePort()));
        }
    }

    /** Echoing sends to the sender's address, which the policy has to allow as well. */
    @Test
    void aGuestIsDeniedSendingWhereThePolicyRefuses() throws IOException {
        try (var wasi = wasi(allowing(AddressUse.UDP_BIND));
                DatagramSocket host = loopbackSocket()) {
            ComponentInstance guest = instantiate(wasi);
            int port = bind(guest);

            host.send(datagram("hello", new InetSocketAddress(host.getLocalAddress(), port)));

            assertThrows(TrapException.class, () -> guest.export("echo-one").apply());
        }
    }

    private static WasiPreview2 wasi(NetworkPolicy policy) {
        return WasiPreview2.builder().withNetworkPolicy(policy).build();
    }

    private static NetworkPolicy allowing(AddressUse allowed) {
        return NetworkPolicy.builder()
                .allowUdp()
                .allowAddresses((address, use) -> use == allowed)
                .build();
    }

    private static ComponentInstance instantiate(WasiPreview2 wasi) {
        return wasi.instantiate(new ComponentStore(), component).instance();
    }

    private static int bind(ComponentInstance guest) {
        return ((Number) guest.export("bind").apply()[0]).intValue();
    }

    private static Object request(ComponentInstance guest, int port, String message) {
        return guest.export("request").apply(port, message)[0];
    }

    private static int requestError(WasiPreview2 wasi, int port) {
        return ((Number) instantiate(wasi).export("request-error").apply(port)[0]).intValue();
    }

    /** Receives one datagram, and replies to its sender with it in upper case. */
    private static void shout(DatagramSocket server) {
        try {
            DatagramPacket packet = new DatagramPacket(new byte[1024], 1024);
            server.receive(packet);
            String message = new String(packet.getData(), 0, packet.getLength(), UTF_8);
            server.send(
                    datagram(
                            message.toUpperCase(Locale.ROOT),
                            (InetSocketAddress) packet.getSocketAddress()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String receive(DatagramSocket socket) throws IOException {
        DatagramPacket packet = new DatagramPacket(new byte[1024], 1024);
        socket.receive(packet);
        return new String(packet.getData(), 0, packet.getLength(), UTF_8);
    }

    private static DatagramPacket datagram(String data, InetSocketAddress to) {
        byte[] bytes = data.getBytes(UTF_8);
        return new DatagramPacket(bytes, bytes.length, to);
    }

    /** A host socket on loopback, which gives up waiting to receive after 10 seconds. */
    private static DatagramSocket loopbackSocket() throws IOException {
        DatagramSocket socket = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        socket.setSoTimeout(10_000);
        return socket;
    }

    private static int freePort() throws IOException {
        try (DatagramSocket socket = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    private static int code(ErrorCode code) {
        return code.ordinal();
    }
}
