package run.endive.cm.wasi.p2.sockets.netty;

import io.netty.channel.IoEventLoopGroup;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.IpAddressFamily;
import run.endive.cm.wasi.p2.io.poll.HostPoll;
import run.endive.cm.wasi.p2.sockets.tcp.TcpChannel;

/**
 * Opens the network's TCP sockets on its event loop group, and closes every socket still open when
 * the network closes.
 */
final class NettyTcp implements TcpChannel.Factory, AutoCloseable {

    private final HostPoll poll;
    private final IoEventLoopGroup group;
    private final NettyChannels channels;
    private final Set<NettyTcpChannel> open = ConcurrentHashMap.newKeySet();

    NettyTcp(HostPoll poll, IoEventLoopGroup group, NettyChannels channels) {
        this.poll = poll;
        this.group = group;
        this.channels = channels;
    }

    @Override
    public TcpChannel open(IpAddressFamily family) throws IOException {
        return NettyTcpChannel.open(this, family);
    }

    /** Closes every socket the guest left open, discarding what they have not delivered. */
    @Override
    public void close() {
        for (NettyTcpChannel channel : List.copyOf(open)) {
            channel.abort();
        }
    }

    HostPoll poll() {
        return poll;
    }

    IoEventLoopGroup group() {
        return group;
    }

    NettyChannels channels() {
        return channels;
    }

    void track(NettyTcpChannel channel) {
        open.add(channel);
    }

    void forget(NettyTcpChannel channel) {
        open.remove(channel);
    }

    /** How many sockets are open, for a test checking that each is closed. */
    int openCount() {
        return open.size();
    }
}
