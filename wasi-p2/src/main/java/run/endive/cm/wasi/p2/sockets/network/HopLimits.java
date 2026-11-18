package run.endive.cm.wasi.p2.sockets.network;

import java.net.SocketOption;

/** The hop limit of the packets a TCP or UDP socket sends. */
public final class HopLimits {

    /**
     * The hop limit as a socket option, {@code IP_TTL} on IPv4 and {@code IPV6_UNICAST_HOPS} on
     * IPv6. Java has no option standing for it.
     */
    public static final SocketOption<Integer> OPTION =
            new SocketOption<>() {
                @Override
                public String name() {
                    return "HOP_LIMIT";
                }

                @Override
                public Class<Integer> type() {
                    return Integer.class;
                }

                @Override
                public String toString() {
                    return name();
                }
            };

    /** The hop limit Linux and macOS use unless configured otherwise. */
    public static final short DEFAULT = 64;

    private HopLimits() {}

    /**
     * The hop limit {@code value} of {@link #OPTION} stands for. macOS reports -1 for an IPv6 socket
     * the guest has not set, which stands for {@link #DEFAULT}.
     */
    public static short of(int value) {
        return (short) (value < 0 ? DEFAULT : value);
    }
}
