package run.endive.cm.wasi.p2.sockets.netty;

import io.netty.channel.IoEventLoopGroup;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.IpAddressFamily;
import run.endive.cm.wasi.p2.sockets.udp.UdpChannel;

/**
 * Opens the network's UDP sockets on its event loop group, and closes every socket still open when
 * the network closes.
 */
final class NettyUdp implements UdpChannel.Factory, AutoCloseable {

    private final IoEventLoopGroup group;
    private final NettyChannels channels;
    private final Set<NettyUdpChannel> open = ConcurrentHashMap.newKeySet();

    NettyUdp(IoEventLoopGroup group, NettyChannels channels) {
        this.group = group;
        this.channels = channels;
    }

    @Override
    public UdpChannel open(IpAddressFamily family) throws IOException {
        return NettyUdpChannel.open(this, family);
    }

    /** Closes every socket the guest left open, discarding what they have not sent. */
    @Override
    public void close() {
        for (NettyUdpChannel channel : List.copyOf(open)) {
            channel.close();
        }
    }

    IoEventLoopGroup group() {
        return group;
    }

    NettyChannels channels() {
        return channels;
    }

    void track(NettyUdpChannel channel) {
        open.add(channel);
    }

    void forget(NettyUdpChannel channel) {
        open.remove(channel);
    }

    /** How many sockets are open, for a test checking that each is closed. */
    int openCount() {
        return open.size();
    }
}
