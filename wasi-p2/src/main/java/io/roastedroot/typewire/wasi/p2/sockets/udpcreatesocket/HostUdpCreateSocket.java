package io.roastedroot.typewire.wasi.p2.sockets.udpcreatesocket;

import io.roastedroot.typewire.wasi.p2.bindings.wasi.sockets.network.ErrorCode;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.sockets.network.IpAddressFamily;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.sockets.udp.UdpSocket;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.sockets.udpcreatesocket.Host;
import io.roastedroot.typewire.wasi.p2.io.poll.HostPoll;
import io.roastedroot.typewire.wasi.p2.sockets.NetworkPolicy;
import io.roastedroot.typewire.wasi.p2.sockets.network.ErrorCodes;
import io.roastedroot.typewire.wasi.p2.sockets.udp.HostUdpSocket;
import io.roastedroot.typewire.wasi.p2.sockets.udp.UdpChannel;
import java.io.IOException;
import java.util.Objects;

/**
 * The host side of {@code wasi:sockets/udp-create-socket}, which opens a socket on the host's
 * transport for a guest whose policy allows UDP.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/udp-create-socket.wit">wasi:sockets/udp-create-socket</a>
 */
public class HostUdpCreateSocket implements Host {

    private final HostPoll poll;
    private final NetworkPolicy policy;
    private final UdpChannel.Factory channels;

    /**
     * @param poll the poll each socket subscribes through
     * @param policy what the guest may do on the network
     * @param channels what opens each socket's channel
     */
    public HostUdpCreateSocket(HostPoll poll, NetworkPolicy policy, UdpChannel.Factory channels) {
        this.poll = Objects.requireNonNull(poll, "poll");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.channels = Objects.requireNonNull(channels, "channels");
    }

    /**
     * Creates an unbound socket, refused with {@code access-denied} unless the policy allows UDP.
     *
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/udp-create-socket.wit">wasi:sockets/udp-create-socket, create-udp-socket</a>
     */
    @Override
    public UdpSocket createUdpSocket(IpAddressFamily addressFamily) {
        if (!policy.allowsUdp()) {
            throw ErrorCodes.error(ErrorCode.ACCESS_DENIED);
        }
        try {
            return new HostUdpSocket(poll, addressFamily, channels.open(addressFamily));
        } catch (IOException | UnsupportedOperationException e) {
            throw ErrorCodes.failed(e);
        }
    }
}
