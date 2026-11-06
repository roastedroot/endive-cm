package run.endive.cm.wasi.p2.sockets.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.Future;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import jdk.net.ExtendedSocketOptions;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.IpAddressFamily;
import run.endive.cm.wasi.p2.io.streams.BufferedHostInputStream;
import run.endive.cm.wasi.p2.io.streams.BufferedHostOutputStream;
import run.endive.cm.wasi.p2.io.streams.HostInputStream;
import run.endive.cm.wasi.p2.io.streams.HostOutputStream;
import run.endive.cm.wasi.p2.sockets.network.IpAddresses;
import run.endive.cm.wasi.p2.sockets.tcp.TcpChannel;

/**
 * A TCP socket on a Netty channel of the network's transport.
 *
 * <p>The socket opens a channel for connecting when it is created, so its options can be set
 * before it is bound. The channel is bound at once, and registered only once it connects. Listening
 * moves the socket to a listening channel where the transport allows it. Otherwise listening closes
 * the channel and binds a listening one to the same address, so another process could take the
 * address in between.
 *
 * <p>A connection reads into its input stream until the stream holds {@value #STREAM_CAPACITY}
 * bytes, and resumes once the guest has read half of them. What the guest writes is handed to
 * Netty on the event loop, and counts as delivered once Netty has written it to the socket.
 * Closing the socket hands Netty what the guest wrote and closes at once, so only what the host's
 * send buffer takes still reaches the peer. A connection closes itself once it has shut down
 * sending and the peer has finished sending.
 */
final class NettyTcpChannel implements TcpChannel {

    /** How many bytes each direction of a connection buffers for the guest. */
    static final int STREAM_CAPACITY = 64 * 1024;

    /** The options a listening socket hands to the connections it accepts. */
    private static final List<SocketOption<?>> INHERITED =
            List.of(
                    StandardSocketOptions.SO_KEEPALIVE,
                    ExtendedSocketOptions.TCP_KEEPIDLE,
                    ExtendedSocketOptions.TCP_KEEPINTERVAL,
                    ExtendedSocketOptions.TCP_KEEPCOUNT,
                    StandardSocketOptions.SO_RCVBUF,
                    StandardSocketOptions.SO_SNDBUF);

    private final NettyTcp tcp;
    private final IpAddressFamily family;
    private final SocketChannel socket;
    private final CompletableFuture<Void> terminated;
    private final BufferedHostInputStream input;
    private final BufferedHostOutputStream output;

    private final Queue<NettyTcpChannel> waiting = new ConcurrentLinkedQueue<>();
    private volatile CompletableFuture<Void> acceptReadiness = new CompletableFuture<>();
    private volatile Throwable acceptFailure;
    private volatile CompletableFuture<Void> registered;
    private volatile InetSocketAddress bound;
    private volatile ServerSocketChannel server;
    private volatile CompletableFuture<Void> serverRegistered;
    private volatile Map<SocketOption<?>, Object> inherited;

    /** How many accepted connections wait for the guest, at most the host's own listen limit. */
    private volatile int backlog;

    private volatile boolean closed;
    private boolean receiveShut;
    private boolean sendShut;
    private boolean peerEnded;

    private NettyTcpChannel(NettyTcp tcp, IpAddressFamily family, SocketChannel socket) {
        this.tcp = tcp;
        this.family = family;
        this.socket = socket;
        this.input = new BufferedHostInputStream(tcp.poll(), STREAM_CAPACITY, this::resumeReading);
        this.output = new BufferedHostOutputStream(tcp.poll(), STREAM_CAPACITY, this::deliverLater);
        socket.config().setOption(ChannelOption.ALLOW_HALF_CLOSURE, true);
        socket.config().setOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, 0);
        socket.pipeline().addLast(new Connection());
        this.terminated = completable(socket.closeFuture());
        tcp.track(this);
    }

    /**
     * Opens a socket of {@code family}, which is not yet bound.
     *
     * @throws IOException when the host cannot create another socket
     */
    static NettyTcpChannel open(NettyTcp tcp, IpAddressFamily family) throws IOException {
        SocketChannel socket;
        try {
            socket = tcp.channels().socket(protocolFamily(family));
        } catch (ChannelException e) {
            throw unwrapped(e);
        }
        return new NettyTcpChannel(tcp, family, socket);
    }

    /**
     * Binds at once, completing before this returns. A recently closed connection's address may be
     * reused.
     */
    @Override
    public CompletableFuture<Void> bind(InetSocketAddress local) {
        try {
            if (local.getPort() != 0) {
                socket.config().setOption(ChannelOption.SO_REUSEADDR, true);
            }
            bound = tcp.channels().bind(socket, local);
            return CompletableFuture.completedFuture(null);
        } catch (ChannelException e) {
            return CompletableFuture.failedFuture(unwrapped(e));
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /** Connects with no timeout of its own, so the host's applies. */
    @Override
    public CompletableFuture<Void> connect(InetSocketAddress remote) {
        CompletableFuture<Void> registering = completable(tcp.group().register(socket));
        registered = registering;
        return registering.thenCompose(ignored -> completable(socket.connect(remote)));
    }

    /**
     * Listens on the bound socket, or on a socket bound to the same address where the transport
     * requires one. The listening channel takes over the options the accepted connections inherit.
     */
    @Override
    public CompletableFuture<Void> listen(int backlog) {
        Map<SocketOption<?>, Object> options = new ConcurrentHashMap<>();
        try {
            for (SocketOption<?> option : INHERITED) {
                Object value = read(socket, option);
                if (value != null) {
                    options.put(option, value);
                }
            }
        } catch (ChannelException e) {
            return CompletableFuture.failedFuture(unwrapped(e));
        }
        ServerSocketChannel listener;
        try {
            listener = tcp.channels().listen(socket, backlog);
        } catch (ChannelException e) {
            return CompletableFuture.failedFuture(unwrapped(e));
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
        boolean moved = listener != null;
        if (!moved) {
            socket.unsafe().closeForcibly();
            try {
                listener = tcp.channels().serverSocket(protocolFamily(family));
            } catch (ChannelException e) {
                return CompletableFuture.failedFuture(unwrapped(e));
            }
            listener.config().setOption(ChannelOption.SO_REUSEADDR, true);
            listener.config().setOption(ChannelOption.SO_BACKLOG, backlog);
        }
        listener.pipeline().addLast(new Acceptor());
        this.backlog = Math.min(backlog, NetUtil.SOMAXCONN);
        this.inherited = options;
        CompletableFuture<Void> registering = completable(tcp.group().register(listener));
        this.serverRegistered = registering;
        this.server = listener;
        if (moved) {
            return registering;
        }
        ServerSocketChannel unbound = listener;
        return registering.thenCompose(ignored -> completable(unbound.bind(bound)));
    }

    @Override
    public TcpChannel accept() throws IOException {
        NettyTcpChannel next = waiting.poll();
        Throwable failure = next == null ? acceptFailure : null;
        if (failure != null) {
            acceptFailure = null;
        }
        if (next != null) {
            next.resumeReading();
            updateAccepting();
        }
        if (waiting.isEmpty()) {
            awaitConnections();
        }
        if (failure != null) {
            throw failure instanceof IOException ? (IOException) failure : new IOException(failure);
        }
        return next;
    }

    @Override
    public CompletableFuture<?> acceptReadiness() {
        return acceptReadiness;
    }

    /** Asks the socket each time, since connecting may change the address Netty saw first. */
    @Override
    public InetSocketAddress localAddress() {
        ServerSocketChannel listener = server;
        InetSocketAddress address = null;
        if (listener != null) {
            address = (InetSocketAddress) listener.localAddress();
        } else if (registered != null) {
            address = (InetSocketAddress) socket.unsafe().localAddress();
        }
        return inFamily(address == null ? bound : address);
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return inFamily((InetSocketAddress) socket.remoteAddress());
    }

    @Override
    public CompletableFuture<?> terminated() {
        return terminated;
    }

    @Override
    public HostInputStream input() {
        return input;
    }

    @Override
    public HostOutputStream output() {
        return output;
    }

    /** A listening socket reads the options its connections inherit from those it keeps. */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOption(SocketOption<T> option) throws IOException {
        Map<SocketOption<?>, Object> options = inherited;
        T value;
        if (options != null && INHERITED.contains(option)) {
            value = (T) options.get(option);
        } else {
            try {
                value = read(socket, option);
            } catch (ChannelException e) {
                throw unwrapped(e);
            }
        }
        if (value == null) {
            throw new UnsupportedOperationException(option.name());
        }
        return value;
    }

    /** A listening socket keeps the options its connections inherit, to set on each of them. */
    @Override
    public <T> void setOption(SocketOption<T> option, T value) throws IOException {
        ChannelOption<T> netty = tcp.channels().option(option);
        Map<SocketOption<?>, Object> options = inherited;
        if (netty == null || (options != null && !options.containsKey(option))) {
            throw new UnsupportedOperationException(option.name());
        }
        if (options != null) {
            options.put(option, value);
            return;
        }
        boolean set;
        try {
            set = socket.config().setOption(netty, value);
        } catch (ChannelException e) {
            throw unwrapped(e);
        }
        if (!set) {
            throw new UnsupportedOperationException(option.name());
        }
    }

    /**
     * Shutting down receiving discards what the guest has not read. Shutting down sending delivers
     * what the guest wrote before sending the peer the end of the stream.
     */
    @Override
    public void shutdown(boolean receive, boolean send) {
        if (receive && !receiveShut) {
            receiveShut = true;
            input.drop();
            socket.shutdownInput();
        }
        if (send && !sendShut) {
            sendShut = true;
            output.close();
            onEventLoop(
                    () -> {
                        deliver();
                        socket.writeAndFlush(Unpooled.EMPTY_BUFFER)
                                .addListener(
                                        written ->
                                                socket.shutdownOutput()
                                                        .addListener(done -> closeIfShutDown()));
                    });
        }
    }

    /** Closes the socket at once, after handing Netty what the guest wrote. */
    @Override
    public void close() {
        close(true);
    }

    private void close(boolean deliverFirst) {
        if (closed) {
            return;
        }
        closed = true;
        tcp.forget(this);
        ServerSocketChannel listener = server;
        if (listener != null) {
            closeOnceRegistered(listener, serverRegistered);
            for (NettyTcpChannel connection = waiting.poll();
                    connection != null;
                    connection = waiting.poll()) {
                connection.close(deliverFirst);
            }
            return;
        }
        CompletableFuture<Void> registration = registered;
        if (registration == null) {
            socket.unsafe().closeForcibly();
            return;
        }
        registration.whenComplete(
                (ignored, failure) -> {
                    if (failure != null) {
                        socket.unsafe().closeForcibly();
                    } else if (deliverFirst) {
                        onEventLoop(
                                () -> {
                                    deliver();
                                    socket.close();
                                });
                    } else {
                        socket.close();
                    }
                });
    }

    /** Closes the socket at once, discarding what the guest wrote and has not been delivered. */
    void abort() {
        close(false);
    }

    /**
     * Closes a connection once it has shut down sending and the peer has finished sending, which
     * Netty leaves open while it allows half closure. The event loop calls this.
     */
    private void closeIfShutDown() {
        if (peerEnded && socket.isOutputShutdown()) {
            socket.close();
        }
    }

    /**
     * Accepts connections while fewer than the backlog wait. The listener's event loop decides, so
     * each decision sees the queue as it is once the decisions before it are made.
     */
    private void updateAccepting() {
        ServerSocketChannel listener = server;
        try {
            listener.eventLoop()
                    .execute(() -> listener.config().setAutoRead(waiting.size() < backlog));
        } catch (IllegalStateException | RejectedExecutionException e) {
            acceptFailure = e;
            acceptReadiness.complete(null);
        }
    }

    /** Makes {@link #acceptReadiness} pending, unless a connection or failure is waiting. */
    private void awaitConnections() {
        if (!acceptReadiness.isDone()) {
            return;
        }
        CompletableFuture<Void> pending = new CompletableFuture<>();
        acceptReadiness = pending;
        if (!waiting.isEmpty() || acceptFailure != null) {
            pending.complete(null);
        }
    }

    /**
     * Called on the guest's thread once it has accepted the connection or read enough of a full input
     * stream. Reading resumes on the event loop, so it follows the read that paused it.
     */
    private void resumeReading() {
        try {
            socket.eventLoop().execute(() -> socket.config().setAutoRead(true));
        } catch (IllegalStateException | RejectedExecutionException e) {
            input.fail(e);
        }
    }

    /** Called on the guest's thread whenever it writes, flushes, or drops the output stream. */
    private void deliverLater() {
        onEventLoop(this::deliver);
    }

    /** Hands Netty everything the guest has written, which the event loop has to call. */
    private void deliver() {
        byte[] bytes = output.drain();
        if (bytes.length == 0) {
            return;
        }
        socket.writeAndFlush(Unpooled.wrappedBuffer(bytes))
                .addListener(
                        written -> {
                            if (written.isSuccess()) {
                                output.markDelivered(bytes.length);
                            } else {
                                output.fail(written.cause());
                            }
                        });
    }

    private void onEventLoop(Runnable task) {
        try {
            socket.eventLoop().execute(task);
        } catch (IllegalStateException | RejectedExecutionException e) {
            output.fail(e);
        }
    }

    private InetSocketAddress inFamily(InetSocketAddress address) {
        return address == null ? null : IpAddresses.inFamily(address, family);
    }

    @SuppressWarnings("unchecked")
    private <T> T read(Channel channel, SocketOption<T> option) {
        ChannelOption<T> netty = tcp.channels().option(option);
        return netty == null ? null : channel.config().getOption(netty);
    }

    private static void closeOnceRegistered(Channel channel, CompletableFuture<Void> registration) {
        registration.whenComplete(
                (ignored, failure) -> {
                    if (failure == null) {
                        channel.close();
                    } else {
                        channel.unsafe().closeForcibly();
                    }
                });
    }

    private static SocketProtocolFamily protocolFamily(IpAddressFamily family) {
        return family == IpAddressFamily.IPV4
                ? SocketProtocolFamily.INET
                : SocketProtocolFamily.INET6;
    }

    /** What Netty wrapped, which tells the guest why the host failed. */
    private static IOException unwrapped(ChannelException e) {
        Throwable cause = e.getCause();
        if (cause instanceof RuntimeException) {
            throw (RuntimeException) cause;
        }
        return cause instanceof IOException ? (IOException) cause : new IOException(e);
    }

    private static CompletableFuture<Void> completable(Future<?> future) {
        CompletableFuture<Void> completable = new CompletableFuture<>();
        future.addListener(
                done -> {
                    if (done.isSuccess()) {
                        completable.complete(null);
                    } else if (done.isCancelled()) {
                        completable.cancel(false);
                    } else {
                        completable.completeExceptionally(done.cause());
                    }
                });
        return completable;
    }

    /** Moves what a connection receives into its input stream, and ends its streams. */
    private final class Connection extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf buffer = (ByteBuf) msg;
            try {
                if (!input.offer(ByteBufUtil.getBytes(buffer))) {
                    ctx.channel().config().setAutoRead(false);
                }
            } finally {
                buffer.release();
            }
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt == ChannelInputShutdownEvent.INSTANCE) {
                peerEnded = true;
                input.close();
                closeIfShutDown();
            }
            ctx.fireUserEventTriggered(evt);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            input.close();
            output.close();
            ctx.fireChannelInactive();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            input.fail(cause);
            output.fail(cause);
            ctx.close();
        }
    }

    /** Queues each accepted connection, which reads nothing until the guest accepts it. */
    private final class Acceptor extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            SocketChannel child = (SocketChannel) msg;
            NettyTcpChannel connection = new NettyTcpChannel(tcp, family, child);
            child.config().setAutoRead(false);
            inherited.forEach((option, value) -> inherit(child, option, value));
            CompletableFuture<Void> registering = completable(tcp.group().register(child));
            connection.registered = registering;
            registering.whenComplete(
                    (ignored, failure) -> {
                        if (failure != null) {
                            connection.abort();
                            return;
                        }
                        waiting.add(connection);
                        if (closed) {
                            connection.abort();
                            waiting.remove(connection);
                            return;
                        }
                        acceptReadiness.complete(null);
                        updateAccepting();
                    });
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            acceptFailure = cause;
            acceptReadiness.complete(null);
        }

        /** A value the host refuses for the connection leaves it the host's default. */
        @SuppressWarnings("unchecked")
        private <T> void inherit(SocketChannel child, SocketOption<T> option, Object value) {
            ChannelOption<T> netty = tcp.channels().option(option);
            if (netty == null) {
                return;
            }
            try {
                child.config().setOption(netty, (T) value);
            } catch (ChannelException e) {
                // The connection keeps the host's default.
            }
        }
    }
}
