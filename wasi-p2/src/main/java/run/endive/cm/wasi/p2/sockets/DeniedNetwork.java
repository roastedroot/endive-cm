package run.endive.cm.wasi.p2.sockets;

import java.util.concurrent.ScheduledExecutorService;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.ipnamelookup.ResolveAddressStream;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.ErrorCode;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.IpAddressFamily;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.Network;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.tcp.TcpSocket;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.udp.UdpSocket;
import run.endive.cm.wasi.p2.sockets.network.ErrorCodes;

/**
 * The {@code wasi:sockets} interfaces for a guest granted no network access, which need no
 * additional dependencies. They refuse sockets with {@code access-denied} and name lookups with
 * {@code permanent-resolver-failure}, as wasmtime does.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/network.wit">wasi:sockets/network</a>
 */
final class DeniedNetwork
        implements HostSockets,
                run.endive.cm.wasi.p2.bindings.wasi.sockets.ipnamelookup.Host,
                run.endive.cm.wasi.p2.bindings.wasi.sockets.tcpcreatesocket.Host,
                run.endive.cm.wasi.p2.bindings.wasi.sockets.udpcreatesocket.Host {

    @Override
    public ScheduledExecutorService eventLoopGroup() {
        return null;
    }

    @Override
    public run.endive.cm.wasi.p2.bindings.wasi.sockets.ipnamelookup.Host ipNameLookup() {
        return this;
    }

    @Override
    public run.endive.cm.wasi.p2.bindings.wasi.sockets.tcpcreatesocket.Host tcpCreateSocket() {
        return this;
    }

    @Override
    public run.endive.cm.wasi.p2.bindings.wasi.sockets.udpcreatesocket.Host udpCreateSocket() {
        return this;
    }

    @Override
    public void close() {}

    @Override
    public ResolveAddressStream resolveAddresses(Network network, String name) {
        throw ErrorCodes.error(ErrorCode.PERMANENT_RESOLVER_FAILURE);
    }

    @Override
    public TcpSocket createTcpSocket(IpAddressFamily addressFamily) {
        throw ErrorCodes.error(ErrorCode.ACCESS_DENIED);
    }

    @Override
    public UdpSocket createUdpSocket(IpAddressFamily addressFamily) {
        throw ErrorCodes.error(ErrorCode.ACCESS_DENIED);
    }
}
