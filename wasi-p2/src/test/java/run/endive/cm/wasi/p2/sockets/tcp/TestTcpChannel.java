package run.endive.cm.wasi.p2.sockets.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import jdk.net.ExtendedSocketOptions;
import run.endive.cm.wasi.p2.io.poll.HostPoll;
import run.endive.cm.wasi.p2.io.streams.BufferedHostInputStream;
import run.endive.cm.wasi.p2.io.streams.BufferedHostOutputStream;
import run.endive.cm.wasi.p2.io.streams.HostInputStream;
import run.endive.cm.wasi.p2.io.streams.HostOutputStream;

/**
 * A channel whose operations complete only when a test completes them, recording what the socket
 * asked of it.
 */
final class TestTcpChannel implements TcpChannel {

    final CompletableFuture<Void> bound = new CompletableFuture<>();
    final CompletableFuture<Void> connected = new CompletableFuture<>();
    final CompletableFuture<Void> listening = new CompletableFuture<>();
    final CompletableFuture<Void> terminated = new CompletableFuture<>();
    final Queue<TestTcpChannel> waiting = new ArrayDeque<>();
    final List<String> calls = new ArrayList<>();
    final Map<SocketOption<?>, Object> options = new HashMap<>();
    InetSocketAddress local;
    InetSocketAddress remote;
    boolean closed;

    private final HostPoll poll;
    private CompletableFuture<Void> acceptReadiness = new CompletableFuture<>();
    private HostInputStream input;
    private HostOutputStream output;

    TestTcpChannel(HostPoll poll) {
        this.poll = poll;
        options.put(StandardSocketOptions.SO_KEEPALIVE, false);
        options.put(ExtendedSocketOptions.TCP_KEEPIDLE, 7200);
        options.put(ExtendedSocketOptions.TCP_KEEPINTERVAL, 75);
        options.put(ExtendedSocketOptions.TCP_KEEPCOUNT, 9);
        options.put(StandardSocketOptions.SO_RCVBUF, 65536);
        options.put(StandardSocketOptions.SO_SNDBUF, 65536);
    }

    /** Queues a connection to be accepted. */
    TestTcpChannel arrive(InetSocketAddress peer) {
        TestTcpChannel connection = new TestTcpChannel(poll);
        connection.local = local;
        connection.remote = peer;
        waiting.add(connection);
        acceptReadiness.complete(null);
        return connection;
    }

    @Override
    public CompletableFuture<Void> bind(InetSocketAddress local) {
        calls.add("bind " + local);
        bound.thenRun(() -> this.local = local);
        return bound;
    }

    @Override
    public CompletableFuture<Void> connect(InetSocketAddress remote) {
        calls.add("connect " + remote);
        connected.thenRun(() -> this.remote = remote);
        return connected;
    }

    @Override
    public CompletableFuture<Void> listen(int backlog) {
        calls.add("listen " + backlog);
        return listening;
    }

    @Override
    public TcpChannel accept() {
        TestTcpChannel next = waiting.poll();
        if (waiting.isEmpty() && acceptReadiness.isDone()) {
            acceptReadiness = new CompletableFuture<>();
        }
        return next;
    }

    @Override
    public CompletableFuture<?> acceptReadiness() {
        return acceptReadiness;
    }

    @Override
    public InetSocketAddress localAddress() {
        return local;
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return remote;
    }

    @Override
    public CompletableFuture<?> terminated() {
        return terminated;
    }

    @Override
    public HostInputStream input() {
        if (input == null) {
            input = new BufferedHostInputStream(poll);
        }
        return input;
    }

    @Override
    public HostOutputStream output() {
        if (output == null) {
            output = new BufferedHostOutputStream(poll, 1024, null);
        }
        return output;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOption(SocketOption<T> option) throws IOException {
        if (closed) {
            throw new IOException("closed");
        }
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
    public void shutdown(boolean receive, boolean send) {
        calls.add("shutdown " + receive + " " + send);
    }

    @Override
    public void close() {
        closed = true;
    }
}
