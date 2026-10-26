package run.endive.cm.wasi.p2.sockets.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.util.concurrent.Future;
import java.io.IOException;
import java.net.BindException;
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
import java.util.concurrent.TimeUnit;
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
 * before it is bound. Netty cannot keep a socket bound without listening on it, so listening closes
 * that channel and binds a listening one to the same address. Another process could take the
 * address in between.
 *
 * <p>A connection reads into its input stream until the stream holds {@value #STREAM_CAPACITY}
 * bytes, and resumes once the guest has read half of them. What the guest writes is handed to
 * Netty on the event loop, and counts as delivered once Netty has written it to the socket.
 * Closing the socket delivers what the guest wrote first.
 */
final class NettyTcpChannel implements TcpChannel {

    /** How many times listening tries to bind the address its socket released. */
    private static final int RELEASE_ATTEMPTS = 100;

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
    private final CompletableFuture<Void> registered;
    private final BufferedHostInputStream input;
    private final BufferedHostOutputStream output;

    private final Queue<NettyTcpChannel> waiting = new ConcurrentLinkedQueue<>();
    private volatile CompletableFuture<Void> acceptReadiness = new CompletableFuture<>();
    private volatile Throwable acceptFailure;
    private volatile ServerSocketChannel server;
    private volatile CompletableFuture<Void> serverRegistered;
    private volatile InetSocketAddress listenAddress;
    private volatile Map<SocketOption<?>, Object> inherited;
    private volatile int backlog;
    private volatile boolean closed;
    private boolean receiveShut;
    private boolean sendShut;

    private NettyTcpChannel(NettyTcp tcp, IpAddressFamily family, SocketChannel socket) {
        this.tcp = tcp;
        this.family = family;
        this.socket = socket;
        this.input =
                new BufferedHostInputStream(
                        tcp.poll(), STREAM_CAPACITY, () -> socket.config().setAutoRead(true));
        this.output = new BufferedHostOutputStream(tcp.poll(), STREAM_CAPACITY, this::deliverLater);
        socket.config().setOption(ChannelOption.ALLOW_HALF_CLOSURE, true);
        socket.config().setOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, 0);
        socket.pipeline().addLast(new Connection());
        this.registered = completable(tcp.group().register(socket));
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

    /** Binds without listening, letting a recently closed connection's address be reused. */
    @Override
    public CompletableFuture<Void> bind(InetSocketAddress local) {
        if (local.getPort() != 0) {
            socket.config().setOption(ChannelOption.SO_REUSEADDR, true);
        }
        return registered.thenCompose(ignored -> completable(socket.bind(local)));
    }

    /** Connects with no timeout of its own, so the host's applies. */
    @Override
    public CompletableFuture<Void> connect(InetSocketAddress remote) {
        return registered.thenCompose(ignored -> completable(socket.connect(remote)));
    }

    /**
     * Moves the bound address to a listening channel, which takes over the options the accepted
     * connections inherit.
     */
    @Override
    public CompletableFuture<Void> listen(int backlog) {
        InetSocketAddress address = (InetSocketAddress) socket.localAddress();
        Map<SocketOption<?>, Object> options = new ConcurrentHashMap<>();
        for (SocketOption<?> option : INHERITED) {
            Object value = read(socket, option);
            if (value != null) {
                options.put(option, value);
            }
        }
        ServerSocketChannel listener;
        try {
            listener = tcp.channels().serverSocket(protocolFamily(family));
        } catch (ChannelException e) {
            return CompletableFuture.failedFuture(unwrapped(e));
        }
        listener.config().setOption(ChannelOption.SO_REUSEADDR, true);
        listener.config().setOption(ChannelOption.SO_BACKLOG, backlog);
        listener.pipeline().addLast(new Acceptor());
        this.backlog = backlog;
        this.inherited = options;
        this.listenAddress = address;
        this.server = listener;
        CompletableFuture<Void> registering =
                completable(socket.close())
                        .thenCompose(ignored -> completable(tcp.group().register(listener)));
        this.serverRegistered = registering;
        return registering.thenCompose(
                ignored -> bindReleased(listener, address, RELEASE_ATTEMPTS));
    }

    @Override
    public TcpChannel accept() throws IOException {
        NettyTcpChannel next = waiting.poll();
        Throwable failure = next == null ? acceptFailure : null;
        if (failure != null) {
            acceptFailure = null;
        }
        if (waiting.size() < backlog) {
            server.config().setAutoRead(true);
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

    @Override
    public InetSocketAddress localAddress() {
        ServerSocketChannel listener = server;
        InetSocketAddress address =
                listener == null
                        ? (InetSocketAddress) socket.localAddress()
                        : (InetSocketAddress) listener.localAddress();
        return inFamily(address == null && listener != null ? listenAddress : address);
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return inFamily((InetSocketAddress) socket.remoteAddress());
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
                                .addListener(written -> socket.shutdownOutput());
                    });
        }
    }

    /** Closes the socket once what the guest wrote is delivered. */
    @Override
    public void close() {
        close(true);
    }

    private void close(boolean graceful) {
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
                connection.close(graceful);
            }
        }
        registered.whenComplete(
                (ignored, failure) -> {
                    if (failure != null) {
                        socket.unsafe().closeForcibly();
                    } else if (graceful) {
                        onEventLoop(
                                () -> {
                                    deliver();
                                    socket.writeAndFlush(Unpooled.EMPTY_BUFFER)
                                            .addListener(ChannelFutureListener.CLOSE);
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
     * Binds {@code listener} to the address the socket's own channel held. A NIO selector releases
     * a closed channel's address only when it next selects, so a bind failing meanwhile is retried
     * a millisecond later.
     */
    private CompletableFuture<Void> bindReleased(
            ServerSocketChannel listener, InetSocketAddress address, int attempts) {
        CompletableFuture<Void> bound = new CompletableFuture<>();
        listener.bind(address)
                .addListener(
                        done -> {
                            if (done.isSuccess()) {
                                bound.complete(null);
                            } else if (done.cause() instanceof BindException
                                    && attempts > 1
                                    && !closed) {
                                listener.eventLoop()
                                        .schedule(
                                                () ->
                                                        completeWith(
                                                                bindReleased(
                                                                        listener,
                                                                        address,
                                                                        attempts - 1),
                                                                bound),
                                                1,
                                                TimeUnit.MILLISECONDS);
                            } else {
                                bound.completeExceptionally(done.cause());
                            }
                        });
        return bound;
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

    /** Completes {@code target} as {@code source} completes. */
    private static void completeWith(
            CompletableFuture<Void> source, CompletableFuture<Void> target) {
        source.whenComplete(
                (ignored, failure) -> {
                    if (failure == null) {
                        target.complete(null);
                    } else {
                        target.completeExceptionally(failure);
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
                input.close();
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

    /** Queues each accepted connection until the guest accepts it. */
    private final class Acceptor extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            SocketChannel child = (SocketChannel) msg;
            NettyTcpChannel connection = new NettyTcpChannel(tcp, family, child);
            inherited.forEach((option, value) -> inherit(child, option, value));
            connection.registered.whenComplete(
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
                        if (waiting.size() >= backlog) {
                            ctx.channel().config().setAutoRead(false);
                        }
                    });
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            acceptFailure = cause;
            acceptReadiness.complete(null);
        }

        @SuppressWarnings("unchecked")
        private <T> void inherit(SocketChannel child, SocketOption<T> option, Object value) {
            ChannelOption<T> netty = tcp.channels().option(option);
            if (netty != null) {
                child.config().setOption(netty, (T) value);
            }
        }
    }
}
