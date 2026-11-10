package run.endive.cm.wasi.p2.sockets;

import java.util.concurrent.ScheduledExecutorService;
import run.endive.cm.wasi.p2.io.poll.HostPoll;
import run.endive.cm.wasi.p2.sockets.netty.NettyNetwork;

/**
 * Creates the {@link HostSockets} a network policy calls for. A guest granted nothing gets sockets
 * that need no dependencies. A guest granted anything gets sockets running on Netty, which has to
 * be on the class path.
 */
public final class HostSocketsFactory {

    /** A class from each Netty artifact {@code wasi:sockets} needs. */
    private static final String[] NETTY_CLASSES = {
        "io.netty.channel.nio.NioIoHandler", "io.netty.resolver.dns.DnsNameResolver"
    };

    private HostSocketsFactory() {}

    /**
     * @param policy what the guest may do on the network
     * @param poll the poll everything this creates subscribes through
     * @param scheduler the host's scheduler, which the caller shuts down, or {@code null} for none
     * @param nameResolver what resolves the names the guest looks up, or {@code null} for Netty's
     * @throws IllegalStateException if the policy grants something and Netty is missing
     * @throws IllegalArgumentException if the policy grants something and {@code scheduler} is a
     *     Netty event loop group whose transport is not one Netty ships
     */
    public static HostSockets create(
            NetworkPolicy policy,
            HostPoll poll,
            ScheduledExecutorService scheduler,
            NameResolver nameResolver) {
        if (!grantsNetwork(policy)) {
            return new DeniedNetwork();
        }
        requireNetty();
        return NettyNetwork.start(poll, policy, scheduler, nameResolver);
    }

    /** Whether {@code policy} grants anything, which is when the guest needs Netty. */
    private static boolean grantsNetwork(NetworkPolicy policy) {
        return policy.allowsTcp() || policy.allowsUdp() || policy.allowsIpNameLookup();
    }

    /**
     * Fails with what to add when Netty is missing, before any class needing it is loaded. This
     * class names no Netty type for the same reason.
     */
    private static void requireNetty() {
        ClassNotFoundException missing = NettyPresence.MISSING;
        if (missing != null) {
            throw new IllegalStateException(
                    "Granting the guest network access needs Netty 4.2, which is missing "
                            + missing.getMessage()
                            + ". Add io.netty:netty-transport and io.netty:netty-resolver-dns"
                            + " to the class path, or deny the guest network access.",
                    missing);
        }
    }

    /** Looks for Netty once, the first time a guest is granted network access. */
    private static final class NettyPresence {

        /** Why the first missing Netty class could not be found, or {@code null} for none. */
        static final ClassNotFoundException MISSING = findMissing();

        private NettyPresence() {}

        private static ClassNotFoundException findMissing() {
            for (String required : NETTY_CLASSES) {
                try {
                    Class.forName(required, false, HostSocketsFactory.class.getClassLoader());
                } catch (ClassNotFoundException e) {
                    return e;
                }
            }
            return null;
        }
    }
}
