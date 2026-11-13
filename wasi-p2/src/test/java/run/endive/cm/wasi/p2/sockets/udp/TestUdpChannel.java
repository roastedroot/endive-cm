package run.endive.cm.wasi.p2.sockets.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import run.endive.cm.wasi.p2.sockets.network.HopLimits;

/**
 * A channel whose operations complete only when a test completes them, recording what the socket
 * asked of it.
 */
final class TestUdpChannel implements UdpChannel {

    final CompletableFuture<Void> bound = new CompletableFuture<>();
    final List<CompletableFuture<Void>> connects = new ArrayList<>();
    final List<InetSocketAddress> connectedTo = new ArrayList<>();
    final List<DatagramPacket> sent = new ArrayList<>();
    final Map<SocketOption<?>, Object> options = new HashMap<>();
    InetSocketAddress local;
    int capacity = 1000;
    IOException sendFailure;
    boolean closed;

    private final Queue<Object> received = new ArrayDeque<>();
    private CompletableFuture<Void> receiveReadiness = new CompletableFuture<>();

    TestUdpChannel() {
        options.put(StandardSocketOptions.SO_RCVBUF, 65536);
        options.put(StandardSocketOptions.SO_SNDBUF, 65536);
        options.put(HopLimits.OPTION, 64);
    }

    /** Queues a datagram from {@code sender} to be received. */
    void arrive(String data, InetSocketAddress sender) {
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        received.add(new DatagramPacket(bytes, bytes.length, sender));
        receiveReadiness.complete(null);
    }

    /** Queues a failure to be met receiving. */
    void fail(IOException failure) {
        received.add(failure);
        receiveReadiness.complete(null);
    }

    @Override
    public CompletableFuture<Void> bind(InetSocketAddress local) {
        bound.thenRun(() -> this.local = local);
        return bound;
    }

    @Override
    public CompletableFuture<Void> connect(InetSocketAddress remote) {
        CompletableFuture<Void> connected = new CompletableFuture<>();
        connects.add(connected);
        connectedTo.add(remote);
        return connected;
    }

    @Override
    public InetSocketAddress localAddress() {
        return local;
    }

    @Override
    public DatagramPacket receive() throws IOException {
        Object next = received.poll();
        if (received.isEmpty() && receiveReadiness.isDone()) {
            receiveReadiness = new CompletableFuture<>();
        }
        if (next instanceof IOException) {
            throw (IOException) next;
        }
        return (DatagramPacket) next;
    }

    @Override
    public CompletableFuture<?> receiveReadiness() {
        return receiveReadiness;
    }

    @Override
    public int sendCapacity() throws IOException {
        if (sendFailure != null) {
            IOException failure = sendFailure;
            sendFailure = null;
            throw failure;
        }
        return capacity;
    }

    @Override
    public CompletableFuture<?> sendReadiness() {
        return capacity > 0 || sendFailure != null
                ? CompletableFuture.completedFuture(null)
                : new CompletableFuture<>();
    }

    @Override
    public void send(DatagramPacket datagram) {
        sent.add(datagram);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOption(SocketOption<T> option) {
        if (!options.containsKey(option)) {
            throw new UnsupportedOperationException(option.name());
        }
        return (T) options.get(option);
    }

    @Override
    public <T> void setOption(SocketOption<T> option, T value) {
        if (!options.containsKey(option)) {
            throw new UnsupportedOperationException(option.name());
        }
        options.put(option, value);
    }

    @Override
    public void close() {
        closed = true;
    }
}
