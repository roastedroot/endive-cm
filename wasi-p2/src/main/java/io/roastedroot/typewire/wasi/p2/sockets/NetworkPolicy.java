package io.roastedroot.typewire.wasi.p2.sockets;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * What a guest may do on the network. A guest is denied everything the embedder does not grant.
 *
 * <p>Creating a TCP or UDP socket needs that protocol to be allowed. Binding, connecting, and
 * sending a datagram also need the address to pass the address check.
 *
 * <pre>{@code
 * NetworkPolicy policy = NetworkPolicy.builder()
 *         .allowTcp()
 *         .allowAddresses((address, use) -> address.getAddress().isLoopbackAddress())
 *         .build();
 * }</pre>
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/network.wit">wasi:sockets/network</a>
 */
public final class NetworkPolicy {

    /** What a guest wants to use an address for. */
    public enum AddressUse {
        TCP_BIND,
        TCP_CONNECT,
        UDP_BIND,
        UDP_CONNECT,
        UDP_OUTGOING_DATAGRAM
    }

    private static final BiPredicate<InetSocketAddress, AddressUse> NO_ADDRESSES =
            (address, use) -> false;

    private static final NetworkPolicy DENY_ALL = builder().build();

    private final boolean tcp;
    private final boolean udp;
    private final boolean ipNameLookup;
    private final BiPredicate<InetSocketAddress, AddressUse> addresses;

    private NetworkPolicy(Builder builder) {
        this.tcp = builder.tcp;
        this.udp = builder.udp;
        this.ipNameLookup = builder.ipNameLookup;
        this.addresses = builder.addresses;
    }

    /** A policy that grants nothing, which is the default. */
    public static NetworkPolicy denyAll() {
        return DENY_ALL;
    }

    /** A policy that grants every protocol and address, as an unsandboxed process has. */
    public static NetworkPolicy allowAll() {
        return builder()
                .allowTcp()
                .allowUdp()
                .allowIpNameLookup()
                .allowAddresses((address, use) -> true)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean allowsTcp() {
        return tcp;
    }

    public boolean allowsUdp() {
        return udp;
    }

    public boolean allowsIpNameLookup() {
        return ipNameLookup;
    }

    /** Whether the guest may use {@code address} for {@code use}. */
    public boolean allowsAddress(InetSocketAddress address, AddressUse use) {
        return addresses.test(address, use);
    }

    public static final class Builder {

        private boolean tcp;
        private boolean udp;
        private boolean ipNameLookup;
        private BiPredicate<InetSocketAddress, AddressUse> addresses = NO_ADDRESSES;

        private Builder() {}

        /** Lets the guest create TCP sockets. */
        public Builder allowTcp() {
            this.tcp = true;
            return this;
        }

        /** Lets the guest create UDP sockets. */
        public Builder allowUdp() {
            this.udp = true;
            return this;
        }

        /** Lets the guest resolve host names to addresses. */
        public Builder allowIpNameLookup() {
            this.ipNameLookup = true;
            return this;
        }

        /**
         * Decides which addresses the guest may bind, connect, or send to. It may be called on any
         * thread the guest runs on. By default, every address is denied.
         */
        public Builder allowAddresses(BiPredicate<InetSocketAddress, AddressUse> check) {
            this.addresses = Objects.requireNonNull(check, "check");
            return this;
        }

        public NetworkPolicy build() {
            return new NetworkPolicy(this);
        }
    }
}
