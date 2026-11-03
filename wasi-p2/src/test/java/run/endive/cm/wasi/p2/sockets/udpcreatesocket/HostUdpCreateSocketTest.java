package run.endive.cm.wasi.p2.sockets.udpcreatesocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.ErrorCode;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.ErrorCodeException;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.IpAddressFamily;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.udp.UdpSocket;
import run.endive.cm.wasi.p2.io.poll.HostPoll;
import run.endive.cm.wasi.p2.sockets.NetworkPolicy;
import run.endive.cm.wasi.p2.sockets.udp.UdpChannel;

class HostUdpCreateSocketTest {

    private final List<IpAddressFamily> opened = new ArrayList<>();

    @Test
    void aSocketIsCreatedUnboundWithTheFamilyAsked() {
        UdpSocket socket =
                create(NetworkPolicy.allowAll(), this::open).createUdpSocket(IpAddressFamily.IPV6);

        assertEquals(IpAddressFamily.IPV6, socket.addressFamily());
        assertEquals(
                ErrorCode.INVALID_STATE,
                assertThrows(ErrorCodeException.class, socket::localAddress).error());
        assertEquals(List.of(IpAddressFamily.IPV6), opened);
    }

    @Test
    void aPolicyWithoutUdpIsRefusedBeforeAnythingIsOpened() {
        HostUdpCreateSocket host = create(NetworkPolicy.builder().allowTcp().build(), this::open);

        assertEquals(ErrorCode.ACCESS_DENIED, errorOf(host, IpAddressFamily.IPV4));
        assertEquals(List.of(), opened);
    }

    @Test
    void aHostOutOfSocketsReachesTheSocketLimit() {
        HostUdpCreateSocket host =
                create(
                        NetworkPolicy.allowAll(),
                        family -> {
                            throw new IOException("Too many open files");
                        });

        assertEquals(ErrorCode.NEW_SOCKET_LIMIT, errorOf(host, IpAddressFamily.IPV4));
    }

    /** The JDK opens no IPv6 socket on a host without IPv6. */
    @Test
    void aFamilyTheHostLacksIsNotSupported() {
        HostUdpCreateSocket host =
                create(
                        NetworkPolicy.allowAll(),
                        family -> {
                            throw new UnsupportedOperationException("Protocol family unavailable");
                        });

        assertEquals(ErrorCode.NOT_SUPPORTED, errorOf(host, IpAddressFamily.IPV6));
    }

    private UdpChannel open(IpAddressFamily family) {
        opened.add(family);
        return null;
    }

    private static HostUdpCreateSocket create(NetworkPolicy policy, UdpChannel.Factory channels) {
        return new HostUdpCreateSocket(new HostPoll(), policy, channels);
    }

    private static ErrorCode errorOf(HostUdpCreateSocket host, IpAddressFamily family) {
        return assertThrows(ErrorCodeException.class, () -> host.createUdpSocket(family)).error();
    }
}
