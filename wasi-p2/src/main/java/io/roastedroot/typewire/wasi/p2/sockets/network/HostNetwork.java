package io.roastedroot.typewire.wasi.p2.sockets.network;

import io.roastedroot.typewire.wasi.p2.bindings.wasi.sockets.network.ErrorCode;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.sockets.network.Network;
import io.roastedroot.typewire.wasi.p2.sockets.NetworkPolicy;
import java.net.InetSocketAddress;
import java.util.Objects;

/**
 * The host side of the {@code wasi:sockets} {@code network} resource, which grants a guest what
 * its {@link NetworkPolicy} allows.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/network.wit">wasi:sockets/network</a>
 */
public class HostNetwork implements Network {

    private final NetworkPolicy policy;

    public HostNetwork(NetworkPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /**
     * The host network {@code network} stands for.
     *
     * @throws IllegalArgumentException when {@code network} was not created by this host
     */
    public static HostNetwork of(Network network) {
        if (!(network instanceof HostNetwork)) {
            throw new IllegalArgumentException(
                    "network " + network + " was not created by " + HostNetwork.class.getName());
        }
        return (HostNetwork) network;
    }

    public NetworkPolicy policy() {
        return policy;
    }

    /**
     * Checks that the guest may use {@code address} for {@code use}.
     *
     * @throws io.roastedroot.typewire.wasi.p2.bindings.wasi.sockets.network.ErrorCodeException with {@code
     *     access-denied} when the policy denies it
     */
    public void checkAddress(InetSocketAddress address, NetworkPolicy.AddressUse use) {
        if (!policy.allowsAddress(address, use)) {
            throw ErrorCodes.error(ErrorCode.ACCESS_DENIED);
        }
    }
}
