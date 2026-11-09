package run.endive.cm.wasi.p2.sockets.tcpcreatesocket;

import java.io.IOException;
import java.util.Objects;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.ErrorCode;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.IpAddressFamily;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.tcp.TcpSocket;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.tcpcreatesocket.Host;
import run.endive.cm.wasi.p2.io.poll.HostPoll;
import run.endive.cm.wasi.p2.sockets.NetworkPolicy;
import run.endive.cm.wasi.p2.sockets.network.ErrorCodes;
import run.endive.cm.wasi.p2.sockets.tcp.HostTcpSocket;
import run.endive.cm.wasi.p2.sockets.tcp.TcpChannel;

/**
 * The host side of {@code wasi:sockets/tcp-create-socket}, which opens a socket on the host's
 * transport for a guest whose policy allows TCP.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/tcp-create-socket.wit">wasi:sockets/tcp-create-socket</a>
 */
public class HostTcpCreateSocket implements Host {

    private final HostPoll poll;
    private final NetworkPolicy policy;
    private final TcpChannel.Factory channels;

    /**
     * @param poll the poll each socket subscribes through
     * @param policy what the guest may do on the network
     * @param channels what opens each socket's channel
     */
    public HostTcpCreateSocket(HostPoll poll, NetworkPolicy policy, TcpChannel.Factory channels) {
        this.poll = Objects.requireNonNull(poll, "poll");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.channels = Objects.requireNonNull(channels, "channels");
    }

    /**
     * Creates an unbound socket, refused with {@code access-denied} unless the policy allows TCP.
     *
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/tcp-create-socket.wit">wasi:sockets/tcp-create-socket, create-tcp-socket</a>
     */
    @Override
    public TcpSocket createTcpSocket(IpAddressFamily addressFamily) {
        if (!policy.allowsTcp()) {
            throw ErrorCodes.error(ErrorCode.ACCESS_DENIED);
        }
        try {
            return new HostTcpSocket(poll, addressFamily, channels.open(addressFamily));
        } catch (IOException | UnsupportedOperationException e) {
            throw ErrorCodes.failed(e);
        }
    }
}
