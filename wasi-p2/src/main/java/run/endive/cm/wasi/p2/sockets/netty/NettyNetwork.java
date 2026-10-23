package run.endive.cm.wasi.p2.sockets.netty;

import io.netty.channel.IoEventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.resolver.dns.DnsNameResolver;
import io.netty.resolver.dns.DnsNameResolverBuilder;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import run.endive.cm.wasi.p2.io.poll.HostPoll;
import run.endive.cm.wasi.p2.sockets.HostSockets;
import run.endive.cm.wasi.p2.sockets.NameResolver;
import run.endive.cm.wasi.p2.sockets.NetworkPolicy;
import run.endive.cm.wasi.p2.sockets.ipnamelookup.HostIpNameLookup;
import run.endive.cm.wasi.p2.sockets.tcpcreatesocket.HostTcpCreateSocket;
import run.endive.cm.wasi.p2.sockets.udpcreatesocket.HostUdpCreateSocket;

/**
 * The {@code wasi:sockets} interfaces for a guest granted network access, running on Netty.
 *
 * <p>Only {@link run.endive.cm.wasi.p2.sockets.HostSocketsFactory} creates one, after checking that
 * Netty is present. It runs on the host's scheduler when that is a Netty event loop group, and on a
 * one-thread NIO group of its own otherwise. It owns whatever the embedder left it to create, and
 * releases that on {@link #close()}.
 */
public final class NettyNetwork implements HostSockets {

    private final IoEventLoopGroup group;
    private final boolean ownsGroup;
    private final DnsNameResolver defaultNameResolver;
    private final HostIpNameLookup ipNameLookup;
    private final HostTcpCreateSocket tcpCreateSocket;
    private final HostUdpCreateSocket udpCreateSocket;

    private NettyNetwork(
            HostPoll poll,
            NetworkPolicy policy,
            IoEventLoopGroup group,
            boolean ownsGroup,
            NameResolver nameResolver,
            NettyChannels channels) {
        this.group = group;
        this.ownsGroup = ownsGroup;
        this.defaultNameResolver =
                nameResolver == null ? defaultNameResolver(group, channels) : null;
        this.ipNameLookup =
                new HostIpNameLookup(
                        poll,
                        nameResolver == null
                                ? new NettyNameResolver(defaultNameResolver)
                                : nameResolver);
        this.tcpCreateSocket =
                new HostTcpCreateSocket(
                        poll,
                        policy,
                        family -> {
                            throw new UnsupportedOperationException("TCP is not supported yet");
                        });
        this.udpCreateSocket = new HostUdpCreateSocket();
    }

    /**
     * @param poll the poll everything this creates subscribes through
     * @param policy what the guest may do on the network
     * @param scheduler the host's scheduler, which the caller shuts down, or {@code null} for none
     * @param nameResolver what resolves the names the guest looks up, or {@code null} for Netty's
     * @throws IllegalArgumentException if {@code scheduler} is a Netty event loop group whose
     *     transport is not one Netty ships
     */
    public static NettyNetwork start(
            HostPoll poll,
            NetworkPolicy policy,
            ScheduledExecutorService scheduler,
            NameResolver nameResolver) {
        return start(poll, policy, scheduler, nameResolver, null);
    }

    /**
     * @param channels what creates the network's channels, or {@code null} to suit the group
     */
    static NettyNetwork start(
            HostPoll poll,
            NetworkPolicy policy,
            ScheduledExecutorService scheduler,
            NameResolver nameResolver,
            NettyChannels channels) {
        if (scheduler instanceof IoEventLoopGroup) {
            IoEventLoopGroup group = (IoEventLoopGroup) scheduler;
            return new NettyNetwork(
                    poll,
                    policy,
                    group,
                    false,
                    nameResolver,
                    channels == null ? NettyChannels.forGroup(group) : channels);
        }
        IoEventLoopGroup group =
                new MultiThreadIoEventLoopGroup(
                        1,
                        new DefaultThreadFactory("endive-wasi-p2-network", true),
                        NioIoHandler.newFactory());
        try {
            return new NettyNetwork(
                    poll,
                    policy,
                    group,
                    true,
                    nameResolver,
                    channels == null ? NettyChannels.forGroup(group) : channels);
        } catch (RuntimeException e) {
            group.shutdownGracefully(0, 0, TimeUnit.SECONDS);
            throw e;
        }
    }

    @Override
    public ScheduledExecutorService eventLoopGroup() {
        return group;
    }

    @Override
    public run.endive.cm.wasi.p2.bindings.wasi.sockets.ipnamelookup.Host ipNameLookup() {
        return ipNameLookup;
    }

    @Override
    public run.endive.cm.wasi.p2.bindings.wasi.sockets.tcpcreatesocket.Host tcpCreateSocket() {
        return tcpCreateSocket;
    }

    @Override
    public run.endive.cm.wasi.p2.bindings.wasi.sockets.udpcreatesocket.Host udpCreateSocket() {
        return udpCreateSocket;
    }

    /** Closes the resolver and shuts down the group, unless the embedder gave them. */
    @Override
    public void close() {
        if (defaultNameResolver != null) {
            defaultNameResolver.close();
        }
        if (ownsGroup) {
            group.shutdownGracefully(0, 0, TimeUnit.SECONDS);
        }
    }

    private static DnsNameResolver defaultNameResolver(
            IoEventLoopGroup group, NettyChannels channels) {
        return new DnsNameResolverBuilder(group.next())
                .datagramChannelFactory(channels.datagram())
                .socketChannelFactory(channels.socket())
                .build();
    }
}
