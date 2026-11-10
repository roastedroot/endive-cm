package run.endive.cm.wasi.p2.sockets.instancenetwork;

import java.util.Objects;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.instancenetwork.Host;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.Network;
import run.endive.cm.wasi.p2.sockets.NetworkPolicy;
import run.endive.cm.wasi.p2.sockets.network.HostNetwork;

/**
 * The host side of {@code wasi:sockets/instance-network}, which hands each guest a network granting
 * what {@code policy} allows.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/instance-network.wit">wasi:sockets/instance-network</a>
 */
public class HostInstanceNetwork implements Host {

    private final HostNetwork network;

    public HostInstanceNetwork(NetworkPolicy policy) {
        this.network = new HostNetwork(Objects.requireNonNull(policy, "policy"));
    }

    /**
     * The same network on every call, since connecting needs the network a socket was bound with.
     *
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/instance-network.wit">wasi:sockets/instance-network, instance-network</a>
     */
    @Override
    public Network instanceNetwork() {
        return network;
    }
}
