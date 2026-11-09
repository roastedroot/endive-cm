package run.endive.cm.wasi.p2.sockets.udpcreatesocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.ErrorCode;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.ErrorCodeException;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.IpAddressFamily;
import run.endive.cm.wasi.p2.sockets.NetworkPolicy;

class HostUdpCreateSocketTest {

    @Test
    void aPolicyWithoutUdpIsRefusedBeforeAnythingElse() {
        HostUdpCreateSocket host =
                new HostUdpCreateSocket(NetworkPolicy.builder().allowTcp().build());

        assertEquals(ErrorCode.ACCESS_DENIED, errorOf(host));
    }

    @Test
    void udpIsNotSupportedYet() {
        HostUdpCreateSocket host = new HostUdpCreateSocket(NetworkPolicy.allowAll());

        assertEquals(ErrorCode.NOT_SUPPORTED, errorOf(host));
    }

    private static ErrorCode errorOf(HostUdpCreateSocket host) {
        return assertThrows(
                        ErrorCodeException.class, () -> host.createUdpSocket(IpAddressFamily.IPV4))
                .error();
    }
}
