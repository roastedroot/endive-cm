package run.endive.cm.wasi.p2.sockets.tcpcreatesocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.ErrorCode;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.ErrorCodeException;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.IpAddressFamily;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.tcp.TcpSocket;
import run.endive.cm.wasi.p2.io.poll.HostPoll;
import run.endive.cm.wasi.p2.sockets.NetworkPolicy;
import run.endive.cm.wasi.p2.sockets.tcp.TcpChannel;

class HostTcpCreateSocketTest {

    private final List<IpAddressFamily> opened = new ArrayList<>();

    @Test
    void aSocketIsCreatedUnboundWithTheFamilyAsked() {
        TcpSocket socket =
                create(NetworkPolicy.allowAll(), this::open).createTcpSocket(IpAddressFamily.IPV6);

        assertEquals(IpAddressFamily.IPV6, socket.addressFamily());
        assertFalse(socket.isListening());
        assertEquals(List.of(IpAddressFamily.IPV6), opened);
    }

    @Test
    void aPolicyWithoutTcpIsRefusedBeforeAnythingIsOpened() {
        HostTcpCreateSocket host = create(NetworkPolicy.builder().allowUdp().build(), this::open);

        assertEquals(ErrorCode.ACCESS_DENIED, errorOf(host, IpAddressFamily.IPV4));
        assertEquals(List.of(), opened);
    }

    @Test
    void aHostOutOfSocketsReachesTheSocketLimit() {
        HostTcpCreateSocket host =
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
        HostTcpCreateSocket host =
                create(
                        NetworkPolicy.allowAll(),
                        family -> {
                            throw new UnsupportedOperationException("Protocol family unavailable");
                        });

        assertEquals(ErrorCode.NOT_SUPPORTED, errorOf(host, IpAddressFamily.IPV6));
    }

    private TcpChannel open(IpAddressFamily family) {
        opened.add(family);
        return null;
    }

    private static HostTcpCreateSocket create(NetworkPolicy policy, TcpChannel.Factory channels) {
        return new HostTcpCreateSocket(new HostPoll(), policy, channels);
    }

    private static ErrorCode errorOf(HostTcpCreateSocket host, IpAddressFamily family) {
        return assertThrows(ErrorCodeException.class, () -> host.createTcpSocket(family)).error();
    }
}
