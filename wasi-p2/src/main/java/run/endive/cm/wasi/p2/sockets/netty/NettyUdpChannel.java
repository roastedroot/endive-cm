package run.endive.cm.wasi.p2.sockets.netty;

import static run.endive.cm.wasi.p2.sockets.netty.NettyChannelSupport.closeOnceRegistered;
import static run.endive.cm.wasi.p2.sockets.netty.NettyChannelSupport.completable;
import static run.endive.cm.wasi.p2.sockets.netty.NettyChannelSupport.completeWith;
import static run.endive.cm.wasi.p2.sockets.netty.NettyChannelSupport.isIpv4OnIpv6;
import static run.endive.cm.wasi.p2.sockets.netty.NettyChannelSupport.protocolFamily;
import static run.endive.cm.wasi.p2.sockets.netty.NettyChannelSupport.unwrapped;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.DatagramChannel;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.IpAddressFamily;
import run.endive.cm.wasi.p2.sockets.network.IpAddresses;
import run.endive.cm.wasi.p2.sockets.udp.UdpChannel;

/**
 * A UDP socket on a Netty datagram channel of the network's transport.
 *
 * <p>The socket keeps up to {@value #RECEIVE_CAPACITY} bytes of datagrams for the guest, and drops
 * those arriving while it is full, as a full receive buffer of the host would. Each datagram the
 * guest sends is handed to Netty on the event loop, and up to {@value #SEND_CAPACITY} may wait to
 * be sent.
 */
final class NettyUdpChannel implements UdpChannel {

    /** How many bytes of datagrams the socket keeps for the guest. */
    static final int RECEIVE_CAPACITY = 1024 * 1024;

    /** How many datagrams may wait to be sent. */
    static final int SEND_CAPACITY = 256;

    private final NettyUdp udp;
    private final IpAddressFamily family;
    private final DatagramChannel channel;
    private final CompletableFuture<Void> registered;

    private final Queue<Object> received = new ConcurrentLinkedQueue<>();
    private final AtomicInteger receivedBytes = new AtomicInteger();
    private volatile CompletableFuture<Void> receiveReadiness = new CompletableFuture<>();
    private final AtomicInteger sending = new AtomicInteger();
    private volatile CompletableFuture<Void> sendReadiness =
            CompletableFuture.completedFuture(null);
    private volatile Throwable sendFailure;
    private volatile boolean closed;

    private NettyUdpChannel(NettyUdp udp, IpAddressFamily family, DatagramChannel channel) {
        this.udp = udp;
        this.family = family;
        this.channel = channel;
        channel.pipeline().addLast(new Receiver());
        this.registered = completable(udp.group().register(channel));
        udp.track(this);
    }

    /**
     * Opens a socket of {@code family}, which is not yet bound.
     *
     * @throws IOException when the host cannot create another socket
     */
    static NettyUdpChannel open(NettyUdp udp, IpAddressFamily family) throws IOException {
        DatagramChannel channel;
        try {
            channel = udp.channels().datagram(protocolFamily(family));
        } catch (ChannelException e) {
            throw unwrapped(e);
        }
        return new NettyUdpChannel(udp, family, channel);
    }

    @Override
    public CompletableFuture<Void> bind(InetSocketAddress local) {
        return registered.thenCompose(ignored -> completable(channel.bind(local)));
    }

    @Override
    public CompletableFuture<Void> connect(InetSocketAddress remote) {
        CompletableFuture<Void> connected = new CompletableFuture<>();
        try {
            channel.eventLoop().execute(() -> completeWith(limit(remote), connected));
        } catch (IllegalStateException | RejectedExecutionException e) {
            connected.completeExceptionally(e);
        }
        return connected;
    }

    @Override
    public InetSocketAddress localAddress() {
        return inFamily((InetSocketAddress) channel.localAddress());
    }

    @Override
    public DatagramPacket receive() throws IOException {
        Object next = received.poll();
        if (received.isEmpty()) {
            awaitDatagrams();
        }
        if (next instanceof Throwable) {
            Throwable failure = (Throwable) next;
            throw failure instanceof IOException ? (IOException) failure : new IOException(failure);
        }
        DatagramPacket datagram = (DatagramPacket) next;
        if (datagram != null) {
            receivedBytes.addAndGet(-datagram.getLength());
        }
        return datagram;
    }

    @Override
    public CompletableFuture<?> receiveReadiness() {
        return receiveReadiness;
    }

    @Override
    public int sendCapacity() throws IOException {
        Throwable failure = sendFailure;
        if (failure != null) {
            sendFailure = null;
            throw failure instanceof IOException ? (IOException) failure : new IOException(failure);
        }
        int capacity = SEND_CAPACITY - sending.get();
        if (capacity <= 0) {
            awaitSendCapacity();
        }
        return Math.max(capacity, 0);
    }

    @Override
    public CompletableFuture<?> sendReadiness() {
        return sendReadiness;
    }

    @Override
    public void send(DatagramPacket datagram) {
        sending.incrementAndGet();
        ByteBuf content =
                Unpooled.wrappedBuffer(
                        datagram.getData(), datagram.getOffset(), datagram.getLength());
        InetSocketAddress recipient = (InetSocketAddress) datagram.getSocketAddress();
        try {
            channel.eventLoop()
                    .execute(
                            () ->
                                    channel.writeAndFlush(outgoing(content, recipient))
                                            .addListener(written -> sent(written.cause())));
        } catch (IllegalStateException | RejectedExecutionException e) {
            content.release();
            sent(e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOption(SocketOption<T> option) throws IOException {
        ChannelOption<T> netty = udp.channels().option(option, protocolFamily(family));
        T value;
        try {
            value = netty == null ? null : channel.config().getOption(netty);
        } catch (ChannelException e) {
            throw unwrapped(e);
        }
        if (value == null) {
            throw new UnsupportedOperationException(option.name());
        }
        return value;
    }

    @Override
    public <T> void setOption(SocketOption<T> option, T value) throws IOException {
        ChannelOption<T> netty = udp.channels().option(option, protocolFamily(family));
        boolean set;
        try {
            set = netty != null && channel.config().setOption(netty, value);
        } catch (ChannelException e) {
            throw unwrapped(e);
        }
        if (!set) {
            throw new UnsupportedOperationException(option.name());
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        udp.forget(this);
        closeOnceRegistered(channel, registered);
    }

    /**
     * Limits the channel to {@code remote}, or to no address when it is {@code null}, which the
     * event loop has to call. Any earlier limit is lifted first, since a limited channel takes no
     * other.
     */
    private CompletableFuture<Void> limit(InetSocketAddress remote) {
        CompletableFuture<Void> lifted =
                channel.isConnected()
                        ? completable(channel.disconnect())
                        : CompletableFuture.completedFuture(null);
        return remote == null
                ? lifted
                : lifted.thenCompose(ignored -> completable(channel.connect(remote)));
    }

    /**
     * What the channel writes to send {@code content}, which the event loop has to call. A channel
     * limited to a remote sends only to it.
     */
    private Object outgoing(ByteBuf content, InetSocketAddress recipient) {
        return channel.isConnected()
                ? content
                : new io.netty.channel.socket.DatagramPacket(content, recipient);
    }

    /** Counts a datagram as no longer waiting, keeping a failure for the next check. */
    private void sent(Throwable failure) {
        if (failure != null && !closed) {
            sendFailure = failure;
        }
        sending.decrementAndGet();
        sendReadiness.complete(null);
    }

    /**
     * Makes {@link #receiveReadiness} pending, unless a datagram or failure is waiting. One arriving
     * meanwhile completes either the old future or the new one, which the check after publishing
     * it catches.
     */
    private void awaitDatagrams() {
        if (!receiveReadiness.isDone()) {
            return;
        }
        CompletableFuture<Void> pending = new CompletableFuture<>();
        receiveReadiness = pending;
        if (!received.isEmpty()) {
            pending.complete(null);
        }
    }

    /** Makes {@link #sendReadiness} pending, unless a datagram finished sending meanwhile. */
    private void awaitSendCapacity() {
        if (!sendReadiness.isDone()) {
            return;
        }
        CompletableFuture<Void> pending = new CompletableFuture<>();
        sendReadiness = pending;
        if (sending.get() < SEND_CAPACITY || sendFailure != null) {
            pending.complete(null);
        }
    }

    private InetSocketAddress inFamily(InetSocketAddress address) {
        return address == null ? null : IpAddresses.inFamily(address, family);
    }

    /**
     * Keeps each datagram received, with its sender, until the guest receives it. An IPv6 socket
     * drops each datagram from an IPv4 sender, which only NIO delivers.
     */
    private final class Receiver extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            io.netty.channel.socket.DatagramPacket packet =
                    (io.netty.channel.socket.DatagramPacket) msg;
            try {
                int length = packet.content().readableBytes();
                if (isIpv4OnIpv6(family, packet.sender())
                        || receivedBytes.get() + length > RECEIVE_CAPACITY) {
                    return;
                }
                byte[] data = ByteBufUtil.getBytes(packet.content());
                receivedBytes.addAndGet(length);
                received.add(new DatagramPacket(data, length, inFamily(packet.sender())));
                receiveReadiness.complete(null);
            } finally {
                packet.release();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            received.add(cause);
            receiveReadiness.complete(null);
        }
    }
}
