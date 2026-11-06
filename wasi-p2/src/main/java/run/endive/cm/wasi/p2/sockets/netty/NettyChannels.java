package run.endive.cm.wasi.p2.sockets.netty;

import io.netty.channel.Channel;
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
import io.netty.channel.nio.AbstractNioChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.channel.socket.nio.NioChannelOption;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.unix.IntegerUnixChannelOption;
import io.netty.channel.unix.Socket;
import io.netty.channel.unix.UnixChannel;
import io.netty.channel.uring.IoUringChannelOption;
import io.netty.channel.uring.IoUringDatagramChannel;
import io.netty.channel.uring.IoUringIoHandler;
import io.netty.channel.uring.IoUringServerSocketChannel;
import io.netty.channel.uring.IoUringSocketChannel;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.channels.spi.SelectorProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import jdk.net.ExtendedSocketOptions;
import run.endive.cm.wasi.p2.sockets.tcp.TcpChannel;

/**
 * The Netty channels from which the network creates its sockets, and the Netty options standing
 * for the socket options a guest sets. They have to suit the event loop group that runs the
 * network.
 *
 * <p>A TCP socket's channel is bound before it is registered, since only then does the guest choose
 * whether it connects or listens. Epoll and kqueue listen on that same socket. NIO and io_uring
 * open a listening channel of their own, since Java and Netty offer them no way to listen on a
 * socket they opened for connecting.
 *
 * <p>WASI's IPv6 sockets take no IPv4 traffic, so the native transports set {@code IPV6_V6ONLY} on
 * them. Java offers NIO no way to set it, so on NIO an IPv6 socket still takes a port from IPv4's
 * wildcard address, and a listening one closes each IPv4 connection it accepts. Netty's io_uring
 * channels are IPv6 wherever the host has IPv6, so an IPv4 socket on io_uring is an IPv6 one used
 * only with IPv4 addresses. Netty binds it to IPv6's wildcard address when given IPv4's, so there
 * it takes the port from IPv6 too, and a listening one closes each IPv6 connection it accepts.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/tcp-create-socket.wit">wasi:sockets/tcp-create-socket, create-tcp-socket</a>
 */
final class NettyChannels {

    /** Binds a TCP socket's channel before it is registered. */
    @FunctionalInterface
    interface Binder {

        /** Binds {@code channel} to {@code local}, returning the address it is bound to. */
        InetSocketAddress bind(SocketChannel channel, InetSocketAddress local) throws IOException;
    }

    /** Listens on a bound TCP socket's channel, which is not registered. */
    @FunctionalInterface
    interface Listener {

        /**
         * A listening channel on the same socket as {@code channel}, which then belongs to it, or
         * {@code null} when the transport needs a socket of its own for listening.
         */
        ServerSocketChannel listen(SocketChannel channel, int backlog) throws IOException;
    }

    /** The socket options every transport supports. */
    private static final Map<SocketOption<?>, ChannelOption<?>> STANDARD_OPTIONS =
            Map.of(
                    StandardSocketOptions.SO_KEEPALIVE, ChannelOption.SO_KEEPALIVE,
                    StandardSocketOptions.SO_RCVBUF, ChannelOption.SO_RCVBUF,
                    StandardSocketOptions.SO_SNDBUF, ChannelOption.SO_SNDBUF);

    private final Function<SocketProtocolFamily, ? extends SocketChannel> socket;
    private final Function<SocketProtocolFamily, ? extends ServerSocketChannel> serverSocket;
    private final ChannelFactory<? extends DatagramChannel> datagram;
    private final Binder binder;
    private final Listener listener;
    private final Map<SocketOption<?>, ChannelOption<?>> options;
    private final Map<SocketProtocolFamily, ChannelOption<Integer>> hopLimits;

    private NettyChannels(
            Function<SocketProtocolFamily, ? extends SocketChannel> socket,
            Function<SocketProtocolFamily, ? extends ServerSocketChannel> serverSocket,
            ChannelFactory<? extends DatagramChannel> datagram,
            Binder binder,
            Listener listener,
            Map<SocketOption<?>, ChannelOption<?>> options,
            Map<SocketProtocolFamily, ChannelOption<Integer>> hopLimits) {
        this.socket = Objects.requireNonNull(socket, "socket");
        this.serverSocket = Objects.requireNonNull(serverSocket, "serverSocket");
        this.datagram = Objects.requireNonNull(datagram, "datagram");
        this.binder = binder;
        this.listener = listener;
        this.options = options;
        this.hopLimits = hopLimits;
    }

    /**
     * NIO channels supporting only the socket options every transport supports.
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
        return new NettyChannels(
                socket, serverSocket, datagram, Nio::bind, Nio::listen, STANDARD_OPTIONS, Map.of());
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

    /**
     * Binds {@code channel}, which is not registered, to {@code local}.
     *
     * @return the address the channel is bound to
     */
    InetSocketAddress bind(SocketChannel channel, InetSocketAddress local) throws IOException {
        return binder.bind(channel, local);
    }

    /**
     * Listens on the socket of {@code channel}, which is bound and not registered.
     *
     * @return a listening channel that now owns the socket, or {@code null} when the transport
     *     cannot listen on it
     */
    ServerSocketChannel listen(SocketChannel channel, int backlog) throws IOException {
        return listener.listen(channel, backlog);
    }

    /**
     * The Netty option standing for {@code option} on a socket of {@code family}, or {@code null}
     * when the transport has none.
     */
    @SuppressWarnings("unchecked")
    <T> ChannelOption<T> option(SocketOption<T> option, SocketProtocolFamily family) {
        if (option == TcpChannel.HOP_LIMIT) {
            return (ChannelOption<T>) hopLimits.get(family);
        }
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

    /**
     * Makes {@code channel} refuse IPv4 traffic when it is IPv6, closing it if the host refuses.
     *
     * @param v6Only the transport's option standing for {@code IPV6_V6ONLY}
     */
    private static <C extends Channel> C ipv6Only(
            C channel, SocketProtocolFamily family, ChannelOption<Integer> v6Only) {
        if (family != SocketProtocolFamily.INET6) {
            return channel;
        }
        try {
            channel.config().setOption(v6Only, 1);
        } catch (RuntimeException e) {
            channel.unsafe().closeForcibly();
            throw e;
        }
        return channel;
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
                        Nio::bind,
                        Nio::listen,
                        withKeepAlive(
                                NioChannelOption.of(ExtendedSocketOptions.TCP_KEEPIDLE),
                                NioChannelOption.of(ExtendedSocketOptions.TCP_KEEPINTERVAL),
                                NioChannelOption.of(ExtendedSocketOptions.TCP_KEEPCOUNT)),
                        Map.of());

        private Nio() {}

        static InetSocketAddress bind(SocketChannel channel, InetSocketAddress local)
                throws IOException {
            java.nio.channels.SocketChannel socket =
                    (java.nio.channels.SocketChannel)
                            ((AbstractNioChannel.NioUnsafe) channel.unsafe()).ch();
            socket.bind(local);
            return (InetSocketAddress) socket.getLocalAddress();
        }

        /** Java listens only on a socket opened for listening. */
        static ServerSocketChannel listen(SocketChannel channel, int backlog) {
            return null;
        }
    }

    /** The socket under the channel of a native transport. */
    private static final class Unix {

        private static final int IPPROTO_IP = 0;
        private static final int IPPROTO_IPV6 = 41;

        /** {@code IPV6_V6ONLY} on Linux. */
        static final ChannelOption<Integer> LINUX_V6_ONLY =
                new IntegerUnixChannelOption("IPV6_V6ONLY", IPPROTO_IPV6, 26);

        /** {@code IPV6_V6ONLY} on macOS and the BSDs. */
        static final ChannelOption<Integer> BSD_V6_ONLY =
                new IntegerUnixChannelOption("IPV6_V6ONLY", IPPROTO_IPV6, 27);

        /** {@code IP_TTL} and {@code IPV6_UNICAST_HOPS} on Linux. */
        static final Map<SocketProtocolFamily, ChannelOption<Integer>> LINUX_HOP_LIMITS =
                Map.of(
                        SocketProtocolFamily.INET,
                        new IntegerUnixChannelOption("IP_TTL", IPPROTO_IP, 2),
                        SocketProtocolFamily.INET6,
                        new IntegerUnixChannelOption("IPV6_UNICAST_HOPS", IPPROTO_IPV6, 16));

        /** {@code IP_TTL} and {@code IPV6_UNICAST_HOPS} on macOS and the BSDs. */
        static final Map<SocketProtocolFamily, ChannelOption<Integer>> BSD_HOP_LIMITS =
                Map.of(
                        SocketProtocolFamily.INET,
                        new IntegerUnixChannelOption("IP_TTL", IPPROTO_IP, 4),
                        SocketProtocolFamily.INET6,
                        new IntegerUnixChannelOption("IPV6_UNICAST_HOPS", IPPROTO_IPV6, 4));

        private Unix() {}

        static InetSocketAddress bind(SocketChannel channel, InetSocketAddress local)
                throws IOException {
            Socket socket = socketOf(channel);
            socket.bind(local);
            return socket.localAddress();
        }

        /** Listens on the socket, returning its descriptor. */
        static int listen(SocketChannel channel, int backlog) throws IOException {
            Socket socket = socketOf(channel);
            socket.listen(backlog);
            return socket.intValue();
        }

        private static Socket socketOf(Channel channel) {
            return (Socket) ((UnixChannel) channel).fd();
        }
    }

    private static final class Epoll {

        static final NettyChannels CHANNELS =
                new NettyChannels(
                        family ->
                                family == null
                                        ? new EpollSocketChannel()
                                        : ipv6Only(
                                                new EpollSocketChannel(family),
                                                family,
                                                Unix.LINUX_V6_ONLY),
                        family ->
                                ipv6Only(
                                        new EpollServerSocketChannel(family),
                                        family,
                                        Unix.LINUX_V6_ONLY),
                        EpollDatagramChannel::new,
                        Unix::bind,
                        (channel, backlog) ->
                                new EpollServerSocketChannel(Unix.listen(channel, backlog)),
                        withKeepAlive(
                                EpollChannelOption.TCP_KEEPIDLE,
                                EpollChannelOption.TCP_KEEPINTVL,
                                EpollChannelOption.TCP_KEEPCNT),
                        Unix.LINUX_HOP_LIMITS);

        private Epoll() {}

        static boolean runs(IoEventLoopGroup group) {
            return group.isIoType(EpollIoHandler.class);
        }
    }

    /**
     * Netty's io_uring channels take over no socket opened elsewhere, and are IPv6 wherever the host
     * has IPv6.
     */
    private static final class IoUring {

        static final NettyChannels CHANNELS =
                new NettyChannels(
                        family ->
                                ipv6Only(
                                        new IoUringSocketChannel(),
                                        requireFamily(family),
                                        Unix.LINUX_V6_ONLY),
                        family ->
                                ipv6Only(
                                        new IoUringServerSocketChannel(),
                                        requireFamily(family),
                                        Unix.LINUX_V6_ONLY),
                        IoUringDatagramChannel::new,
                        Unix::bind,
                        (channel, backlog) -> null,
                        withKeepAlive(
                                IoUringChannelOption.TCP_KEEPIDLE,
                                IoUringChannelOption.TCP_KEEPINTVL,
                                IoUringChannelOption.TCP_KEEPCNT),
                        Unix.LINUX_HOP_LIMITS);

        private IoUring() {}

        static boolean runs(IoEventLoopGroup group) {
            return group.isIoType(IoUringIoHandler.class);
        }

        /** {@code family}, refused when it is IPv6 and the host has no IPv6. */
        private static SocketProtocolFamily requireFamily(SocketProtocolFamily family) {
            if (family == SocketProtocolFamily.INET6 && !Socket.isIPv6Preferred()) {
                throw new UnsupportedOperationException("IPv6 not available");
            }
            return family;
        }
    }

    /** Netty offers no keep-alive timing for kqueue. */
    private static final class KQueue {

        static final NettyChannels CHANNELS =
                new NettyChannels(
                        family ->
                                family == null
                                        ? new KQueueSocketChannel()
                                        : ipv6Only(
                                                new KQueueSocketChannel(family),
                                                family,
                                                Unix.BSD_V6_ONLY),
                        family ->
                                ipv6Only(new KQueueServerSocketChannel(), family, Unix.BSD_V6_ONLY),
                        KQueueDatagramChannel::new,
                        Unix::bind,
                        (channel, backlog) ->
                                new KQueueServerSocketChannel(Unix.listen(channel, backlog)),
                        STANDARD_OPTIONS,
                        Unix.BSD_HOP_LIMITS);

        private KQueue() {}

        static boolean runs(IoEventLoopGroup group) {
            return group.isIoType(KQueueIoHandler.class);
        }
    }
}
