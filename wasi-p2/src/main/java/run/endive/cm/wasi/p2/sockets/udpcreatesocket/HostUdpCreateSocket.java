package run.endive.cm.wasi.p2.sockets.udpcreatesocket;

import java.util.Objects;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.ErrorCode;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.ErrorCodeException;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.IpAddressFamily;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.udp.UdpSocket;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.udpcreatesocket.Host;
import run.endive.cm.wasi.p2.sockets.NetworkPolicy;

/**
 * The host side of {@code wasi:sockets/udp-create-socket}, which supports no sockets yet.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/udp-create-socket.wit">wasi:sockets/udp-create-socket</a>
 */
public class HostUdpCreateSocket implements Host {

    private final NetworkPolicy policy;

    /**
     * @param policy what the guest may do on the network
     */
    public HostUdpCreateSocket(NetworkPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /** Refused with {@code access-denied} unless the policy allows UDP. */
    @Override
    public UdpSocket createUdpSocket(IpAddressFamily addressFamily) {
        if (!policy.allowsUdp()) {
            throw new ErrorCodeException(ErrorCode.ACCESS_DENIED);
        }
        throw new ErrorCodeException(ErrorCode.NOT_SUPPORTED);
    }
}
