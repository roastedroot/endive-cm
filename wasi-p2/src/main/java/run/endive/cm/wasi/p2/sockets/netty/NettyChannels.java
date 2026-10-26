package run.endive.cm.wasi.p2.sockets.netty;

import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelOption;
import io.netty.channel.IoEventLoopGroup;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueIoHandler;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.channel.socket.nio.NioChannelOption;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.uring.IoUringChannelOption;
import io.netty.channel.uring.IoUringDatagramChannel;
import io.netty.channel.uring.IoUringIoHandler;
import io.netty.channel.uring.IoUringServerSocketChannel;
import io.netty.channel.uring.IoUringSocketChannel;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.channels.spi.SelectorProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import jdk.net.ExtendedSocketOptions;

/**
 * The Netty channels from which the network creates its sockets, and the Netty options standing
 * for the socket options a guest sets. They have to suit the event loop group that runs the
 * network.
 *
 * <p>A transport that cannot open a channel of the family asked for opens one taking both IPv4 and
 * IPv6.
 */
final class NettyChannels {

    /** The socket options every transport supports. */
    private static final Map<SocketOption<?>, ChannelOption<?>> STANDARD_OPTIONS =
            Map.of(
                    StandardSocketOptions.SO_KEEPALIVE, ChannelOption.SO_KEEPALIVE,
                    StandardSocketOptions.SO_RCVBUF, ChannelOption.SO_RCVBUF,
                    StandardSocketOptions.SO_SNDBUF, ChannelOption.SO_SNDBUF);

    private final Function<SocketProtocolFamily, ? extends SocketChannel> socket;
    private final Function<SocketProtocolFamily, ? extends ServerSocketChannel> serverSocket;
    private final ChannelFactory<? extends DatagramChannel> datagram;
    private final Map<SocketOption<?>, ChannelOption<?>> options;

    private NettyChannels(
            Function<SocketProtocolFamily, ? extends SocketChannel> socket,
            Function<SocketProtocolFamily, ? extends ServerSocketChannel> serverSocket,
            ChannelFactory<? extends DatagramChannel> datagram,
            Map<SocketOption<?>, ChannelOption<?>> options) {
        this.socket = Objects.requireNonNull(socket, "socket");
        this.serverSocket = Objects.requireNonNull(serverSocket, "serverSocket");
        this.datagram = Objects.requireNonNull(datagram, "datagram");
        this.options = options;
    }

    /**
     * Channels supporting only the socket options every transport supports.
     *
     * @param socket creates the channel of a TCP connection of a family, or of the host's default
     *     family when given {@code null}
     * @param serverSocket creates the channel of a listening TCP socket of a family
     * @param datagram creates the channel of a UDP socket
     */
    static NettyChannels of(
            Function<SocketProtocolFamily, ? extends SocketChannel> socket,
            Function<SocketProtocolFamily, ? extends ServerSocketChannel> serverSocket,
            ChannelFactory<? extends DatagramChannel> datagram) {
        return new NettyChannels(socket, serverSocket, datagram, STANDARD_OPTIONS);
    }

    /** Creates the channel of a TCP connection of the host's default family. */
    ChannelFactory<? extends SocketChannel> socket() {
        return () -> socket.apply(null);
    }

    SocketChannel socket(SocketProtocolFamily family) {
        return socket.apply(family);
    }

    ServerSocketChannel serverSocket(SocketProtocolFamily family) {
        return serverSocket.apply(family);
    }

    ChannelFactory<? extends DatagramChannel> datagram() {
        return datagram;
    }

    /** The Netty option standing for {@code option}, or {@code null} when the transport has none. */
    @SuppressWarnings("unchecked")
    <T> ChannelOption<T> option(SocketOption<T> option) {
        return (ChannelOption<T>) options.get(option);
    }

    /**
     * The channels of the transport {@code group} runs, among those Netty ships. A native
     * transport is considered only once its classes are on the class path.
     *
     * @throws IllegalArgumentException when {@code group} runs another transport
     */
    static NettyChannels forGroup(IoEventLoopGroup group) {
        if (group.isIoType(NioIoHandler.class)) {
            return Nio.CHANNELS;
        }
        if (isPresent("io.netty.channel.epoll.EpollIoHandler") && Epoll.runs(group)) {
            return Epoll.CHANNELS;
        }
        if (isPresent("io.netty.channel.uring.IoUringIoHandler") && IoUring.runs(group)) {
            return IoUring.CHANNELS;
        }
        if (isPresent("io.netty.channel.kqueue.KQueueIoHandler") && KQueue.runs(group)) {
            return KQueue.CHANNELS;
        }
        throw new IllegalArgumentException(
                "The network runs on the scheduler, a Netty event loop group whose transport is"
                    + " none of NIO, epoll, io_uring, or kqueue. Use one of those, or a scheduler"
                    + " outside Netty to give the network a NIO group of its own. Got "
                        + group.getClass().getName()
                        + ".");
    }

    private static boolean isPresent(String className) {
        try {
            Class.forName(className, false, NettyChannels.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** The standard options, with those for keep-alive timing a transport adds. */
    private static Map<SocketOption<?>, ChannelOption<?>> withKeepAlive(
            ChannelOption<Integer> idle,
            ChannelOption<Integer> interval,
            ChannelOption<Integer> count) {
        Map<SocketOption<?>, ChannelOption<?>> options = new HashMap<>(STANDARD_OPTIONS);
        options.put(ExtendedSocketOptions.TCP_KEEPIDLE, idle);
        options.put(ExtendedSocketOptions.TCP_KEEPINTERVAL, interval);
        options.put(ExtendedSocketOptions.TCP_KEEPCOUNT, count);
        return Map.copyOf(options);
    }

    private static final class Nio {

        static final NettyChannels CHANNELS =
                new NettyChannels(
                        family ->
                                family == null
                                        ? new NioSocketChannel()
                                        : new NioSocketChannel(SelectorProvider.provider(), family),
                        family -> new NioServerSocketChannel(SelectorProvider.provider(), family),
                        NioDatagramChannel::new,
                        withKeepAlive(
                                NioChannelOption.of(ExtendedSocketOptions.TCP_KEEPIDLE),
                                NioChannelOption.of(ExtendedSocketOptions.TCP_KEEPINTERVAL),
                                NioChannelOption.of(ExtendedSocketOptions.TCP_KEEPCOUNT)));

        private Nio() {}
    }

    private static final class Epoll {

        static final NettyChannels CHANNELS =
                new NettyChannels(
                        family ->
                                family == null
                                        ? new EpollSocketChannel()
                                        : new EpollSocketChannel(family),
                        EpollServerSocketChannel::new,
                        EpollDatagramChannel::new,
                        withKeepAlive(
                                EpollChannelOption.TCP_KEEPIDLE,
                                EpollChannelOption.TCP_KEEPINTVL,
                                EpollChannelOption.TCP_KEEPCNT));

        private Epoll() {}

        static boolean runs(IoEventLoopGroup group) {
            return group.isIoType(EpollIoHandler.class);
        }
    }

    private static final class IoUring {

        static final NettyChannels CHANNELS =
                new NettyChannels(
                        family -> new IoUringSocketChannel(),
                        family -> new IoUringServerSocketChannel(),
                        IoUringDatagramChannel::new,
                        withKeepAlive(
                                IoUringChannelOption.TCP_KEEPIDLE,
                                IoUringChannelOption.TCP_KEEPINTVL,
                                IoUringChannelOption.TCP_KEEPCNT));

        private IoUring() {}

        static boolean runs(IoEventLoopGroup group) {
            return group.isIoType(IoUringIoHandler.class);
        }
    }

    /** Netty offers no keep-alive timing for kqueue. */
    private static final class KQueue {

        static final NettyChannels CHANNELS =
                new NettyChannels(
                        family ->
                                family == null
                                        ? new KQueueSocketChannel()
                                        : new KQueueSocketChannel(family),
                        family -> new KQueueServerSocketChannel(),
                        KQueueDatagramChannel::new,
                        STANDARD_OPTIONS);

        private KQueue() {}

        static boolean runs(IoEventLoopGroup group) {
            return group.isIoType(KQueueIoHandler.class);
        }
    }
}
