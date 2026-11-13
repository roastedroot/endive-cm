package run.endive.cm.wasi.p2.sockets.udp;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.PortUnreachableException;
import java.net.StandardSocketOptions;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import run.endive.cm.runtime.Tuple2;
import run.endive.cm.runtime.Tuple8;
import run.endive.cm.wasi.p2.bindings.wasi.io.poll.Pollable;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.ErrorCode;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.ErrorCodeException;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.IpAddressFamily;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.IpSocketAddress;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.Ipv6SocketAddress;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.udp.IncomingDatagram;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.udp.IncomingDatagramStream;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.udp.OutgoingDatagram;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.udp.OutgoingDatagramStream;
import run.endive.cm.wasi.p2.io.poll.HostPoll;
import run.endive.cm.wasi.p2.sockets.NetworkPolicy;
import run.endive.cm.wasi.p2.sockets.network.HopLimits;
import run.endive.cm.wasi.p2.sockets.network.HostNetwork;
import run.endive.cm.wasi.p2.sockets.network.IpAddresses;
import run.endive.runtime.TrapException;

class HostUdpSocketTest {

    private static final HostNetwork NETWORK = new HostNetwork(NetworkPolicy.allowAll());
    private static final InetSocketAddress LOCAL = new InetSocketAddress("127.0.0.1", 5353);
    private static final InetSocketAddress PEER = new InetSocketAddress("192.0.2.1", 53);
    private static final InetSocketAddress OTHER = new InetSocketAddress("192.0.2.2", 53);

    private final HostPoll poll = new HostPoll();
    private final TestUdpChannel channel = new TestUdpChannel();
    private final HostUdpSocket socket = new HostUdpSocket(poll, IpAddressFamily.IPV4, channel);

    @Test
    void aBindFinishesOnceTheChannelIsBound() {
        socket.startBind(NETWORK, address(LOCAL));
        Pollable ready = socket.subscribe();

        assertFalse(ready.ready());
        assertEquals(ErrorCode.WOULD_BLOCK, errorOf(socket::finishBind));

        channel.bound.complete(null);

        assertTrue(ready.ready());
        socket.finishBind();
        assertEquals(address(LOCAL), socket.localAddress());
    }

    @Test
    void aFailedBindLeavesTheSocketUnboundToTryAgain() {
        socket.startBind(NETWORK, address(LOCAL));
        channel.bound.completeExceptionally(new BindException("Address already in use"));

        assertEquals(ErrorCode.ADDRESS_IN_USE, errorOf(socket::finishBind));
        assertEquals(ErrorCode.INVALID_STATE, errorOf(socket::localAddress));
        assertEquals(ErrorCode.NOT_IN_PROGRESS, errorOf(socket::finishBind));
    }

    @Test
    void aSocketIsBoundOnce() {
        bind();

        assertEquals(
                ErrorCode.INVALID_STATE, errorOf(() -> socket.startBind(NETWORK, address(LOCAL))));
    }

    @Test
    void aSocketIsBoundOnlyToAnAddressOfItsFamily() {
        HostUdpSocket ipv6 = new HostUdpSocket(poll, IpAddressFamily.IPV6, new TestUdpChannel());
        IpSocketAddress mapped =
                new IpSocketAddress.Ipv6(
                        new Ipv6SocketAddress(
                                5353, 0L, Tuple8.of(0, 0, 0, 0, 0, 0xffff, 0x7f00, 1), 0L));

        assertEquals(
                ErrorCode.INVALID_ARGUMENT, errorOf(() -> ipv6.startBind(NETWORK, address(LOCAL))));
        assertEquals(ErrorCode.INVALID_ARGUMENT, errorOf(() -> ipv6.startBind(NETWORK, mapped)));
    }

    @Test
    void thePolicyDecidesWhichAddressesMayBeBound() {
        HostNetwork connectOnly =
                new HostNetwork(
                        NetworkPolicy.builder()
                                .allowUdp()
                                .allowAddresses(
                                        (address, use) ->
                                                use == NetworkPolicy.AddressUse.UDP_CONNECT)
                                .build());

        assertEquals(
                ErrorCode.ACCESS_DENIED,
                errorOf(() -> socket.startBind(connectOnly, address(LOCAL))));
        socket.startBind(NETWORK, address(LOCAL));
    }

    @Test
    void onlyABoundSocketStreams() {
        assertEquals(ErrorCode.INVALID_STATE, errorOf(() -> socket.stream(null)));

        socket.startBind(NETWORK, address(LOCAL));

        assertEquals(ErrorCode.INVALID_STATE, errorOf(() -> socket.stream(null)));
    }

    @Test
    void streamsWithoutARemoteSendAndReceiveAnywhere() {
        bind();
        Tuple2<IncomingDatagramStream, OutgoingDatagramStream> streams = socket.stream(null);
        channel.connects.get(0).complete(null);

        assertEquals(ErrorCode.INVALID_STATE, errorOf(socket::remoteAddress));
        permit(streams.f1());
        assertEquals(BigInteger.ONE, streams.f1().send(List.of(datagram("ping", PEER))));
        assertEquals(List.of("ping -> " + PEER), sent());

        channel.arrive("one", PEER);
        channel.arrive("two", OTHER);

        assertEquals(List.of("one <- " + PEER, "two <- " + OTHER), received(streams.f0(), 10));
        assertEquals(Arrays.asList((InetSocketAddress) null), channel.connectedTo);
    }

    @Test
    void streamsLimitedToARemoteUseOnlyIt() {
        bind();
        Tuple2<IncomingDatagramStream, OutgoingDatagramStream> streams =
                socket.stream(address(PEER));
        channel.connects.get(0).complete(null);

        assertEquals(address(PEER), socket.remoteAddress());
        assertEquals(List.of(PEER), channel.connectedTo);

        channel.arrive("stale", OTHER);
        channel.arrive("reply", PEER);
        assertEquals(List.of("reply <- " + PEER), received(streams.f0(), 10));

        permit(streams.f1());
        assertEquals(
                BigInteger.TWO,
                streams.f1().send(List.of(datagram("a", null), datagram("b", PEER))));
        permit(streams.f1());
        assertEquals(
                ErrorCode.INVALID_ARGUMENT,
                errorOf(() -> streams.f1().send(List.of(datagram("c", OTHER)))));
        assertEquals(List.of("a -> " + PEER, "b -> " + PEER), sent());
    }

    @Test
    void streamsWaitUntilTheChannelIsLimited() {
        bind();
        Tuple2<IncomingDatagramStream, OutgoingDatagramStream> streams =
                socket.stream(address(PEER));
        Pollable incoming = streams.f0().subscribe();
        Pollable outgoing = streams.f1().subscribe();

        assertFalse(incoming.ready());
        assertFalse(outgoing.ready());
        assertEquals(BigInteger.ZERO, streams.f1().checkSend());

        channel.connects.get(0).complete(null);

        assertTrue(outgoing.ready());
        assertEquals(
                BigInteger.valueOf(HostOutgoingDatagramStream.MAX_PERMITS),
                streams.f1().checkSend());
        assertFalse(incoming.ready());
        channel.arrive("hello", PEER);
        assertTrue(incoming.ready());
    }

    @Test
    void aFailureToLimitTheChannelReachesTheStreams() {
        bind();
        Tuple2<IncomingDatagramStream, OutgoingDatagramStream> streams =
                socket.stream(address(PEER));

        channel.connects.get(0).completeExceptionally(new NoRouteToHostException());

        assertTrue(streams.f0().subscribe().ready());
        assertEquals(
                ErrorCode.REMOTE_UNREACHABLE, errorOf(() -> streams.f0().receive(BigInteger.ONE)));
        assertEquals(ErrorCode.REMOTE_UNREACHABLE, errorOf(streams.f1()::checkSend));
    }

    @Test
    void aRemoteIsASpecificAddressAndPort() {
        bind();

        assertEquals(
                ErrorCode.INVALID_ARGUMENT,
                errorOf(() -> socket.stream(address(new InetSocketAddress("0.0.0.0", 53)))));
        assertEquals(
                ErrorCode.INVALID_ARGUMENT,
                errorOf(() -> socket.stream(address(new InetSocketAddress("192.0.2.1", 0)))));
        assertEquals(
                ErrorCode.INVALID_ARGUMENT,
                errorOf(() -> socket.stream(address(new InetSocketAddress("::1", 53)))));
        assertEquals(List.of(), channel.connectedTo);
    }

    @Test
    void thePolicyDecidesWhereDatagramsGo() {
        HostNetwork bindOnly =
                new HostNetwork(
                        NetworkPolicy.builder()
                                .allowUdp()
                                .allowAddresses(
                                        (address, use) -> use == NetworkPolicy.AddressUse.UDP_BIND)
                                .build());
        socket.startBind(bindOnly, address(LOCAL));
        channel.bound.complete(null);
        socket.finishBind();

        assertEquals(ErrorCode.ACCESS_DENIED, errorOf(() -> socket.stream(address(PEER))));

        Tuple2<IncomingDatagramStream, OutgoingDatagramStream> streams = socket.stream(null);
        channel.connects.get(0).complete(null);
        permit(streams.f1());

        assertEquals(
                ErrorCode.ACCESS_DENIED,
                errorOf(() -> streams.f1().send(List.of(datagram("x", PEER)))));
        assertEquals(List.of(), channel.sent);
    }

    @Test
    void streamingAgainNeedsTheEarlierStreamsDropped() {
        bind();
        Tuple2<IncomingDatagramStream, OutgoingDatagramStream> first = socket.stream(address(PEER));
        first.f0().drop();

        assertThrows(TrapException.class, () -> socket.stream(null));

        first.f1().drop();
        socket.stream(null);

        assertEquals(ErrorCode.INVALID_STATE, errorOf(socket::remoteAddress));
        assertEquals(Arrays.asList(PEER, null), channel.connectedTo);
    }

    @Test
    void sendingNeedsAPermitForEachDatagram() {
        OutgoingDatagramStream out = streams().f1();
        List<OutgoingDatagram> two = List.of(datagram("a", PEER), datagram("b", PEER));

        assertThrows(TrapException.class, () -> out.send(two));
        channel.capacity = 1;
        out.checkSend();
        assertThrows(TrapException.class, () -> out.send(two));

        channel.capacity = 2;
        out.checkSend();
        assertEquals(BigInteger.TWO, out.send(two));
        assertThrows(TrapException.class, () -> out.send(List.of(datagram("c", PEER))));
        assertEquals(BigInteger.ZERO, out.send(List.of()));
    }

    @Test
    void aBadDatagramEndsTheSendAfterThoseBeforeIt() {
        OutgoingDatagramStream out = streams().f1();

        permit(out);
        assertEquals(
                BigInteger.ONE,
                out.send(List.of(datagram("a", PEER), datagram("b", null), datagram("c", PEER))));
        permit(out);
        assertEquals(
                ErrorCode.INVALID_ARGUMENT, errorOf(() -> out.send(List.of(datagram("d", null)))));
        assertEquals(List.of("a -> " + PEER), sent());
    }

    @Test
    void aDatagramLargerThanIpCarriesIsTooLarge() {
        OutgoingDatagramStream out = streams().f1();
        byte[] largest = new byte[HostOutgoingDatagramStream.MAX_IPV4_PAYLOAD];
        byte[] tooLarge = new byte[HostOutgoingDatagramStream.MAX_IPV4_PAYLOAD + 1];

        permit(out);
        assertEquals(
                BigInteger.ONE, out.send(List.of(new OutgoingDatagram(largest, address(PEER)))));
        permit(out);
        assertEquals(
                ErrorCode.DATAGRAM_TOO_LARGE,
                errorOf(() -> out.send(List.of(new OutgoingDatagram(tooLarge, address(PEER))))));
    }

    @Test
    void aFailedSendIsReportedByTheNextCheckSend() {
        OutgoingDatagramStream out = streams().f1();
        channel.sendFailure = new PortUnreachableException();

        assertEquals(ErrorCode.CONNECTION_REFUSED, errorOf(out::checkSend));
        assertEquals(HostOutgoingDatagramStream.MAX_PERMITS, permit(out));
    }

    @Test
    void receiveTakesAtMostWhatIsAskedWithoutWaiting() {
        IncomingDatagramStream in = streams().f0();
        channel.arrive("one", PEER);
        channel.arrive("two", PEER);

        assertEquals(List.of(), received(in, 0));
        assertEquals(List.of("one <- " + PEER), received(in, 1));
        assertEquals(List.of("two <- " + PEER), received(in, 5));
        assertEquals(List.of(), received(in, 5));
    }

    @Test
    void aReceiveFailureAfterSomeDatagramsIsReportedNext() {
        IncomingDatagramStream in = streams().f0();
        channel.arrive("one", PEER);
        channel.fail(new PortUnreachableException());
        channel.arrive("two", PEER);

        assertEquals(List.of("one <- " + PEER), received(in, 5));
        assertTrue(in.subscribe().ready());
        assertEquals(ErrorCode.CONNECTION_REFUSED, errorOf(() -> in.receive(BigInteger.TEN)));
        assertEquals(List.of("two <- " + PEER), received(in, 5));
    }

    @Test
    void droppingTheSocketClosesTheChannelAndEndsItsStreams() {
        Tuple2<IncomingDatagramStream, OutgoingDatagramStream> streams = streams();

        socket.drop();

        assertTrue(channel.closed);
        assertTrue(streams.f0().subscribe().ready());
        assertTrue(streams.f1().subscribe().ready());
        assertEquals(ErrorCode.INVALID_STATE, errorOf(() -> streams.f0().receive(BigInteger.ONE)));
        assertEquals(ErrorCode.INVALID_STATE, errorOf(streams.f1()::checkSend));
        assertEquals(ErrorCode.INVALID_STATE, errorOf(socket::receiveBufferSize));
    }

    @Test
    void bufferSizesAreReadAndWrittenOnTheChannel() {
        socket.setReceiveBufferSize(BigInteger.valueOf(4096));
        socket.setSendBufferSize(BigInteger.valueOf(Long.MAX_VALUE));

        assertEquals(BigInteger.valueOf(4096), socket.receiveBufferSize());
        assertEquals(Integer.MAX_VALUE, channel.options.get(StandardSocketOptions.SO_SNDBUF));
        assertEquals(
                ErrorCode.INVALID_ARGUMENT,
                errorOf(() -> socket.setReceiveBufferSize(BigInteger.ZERO)));
    }

    @Test
    void theHopLimitIsReadAndWrittenOnTheChannel() {
        socket.setUnicastHopLimit((short) 255);

        assertEquals((short) 255, socket.unicastHopLimit());
        assertEquals(255, channel.options.get(HopLimits.OPTION));
        assertEquals(
                ErrorCode.INVALID_ARGUMENT, errorOf(() -> socket.setUnicastHopLimit((short) 0)));
    }

    @Test
    void aHopLimitTheHostLeftUnsetIsItsDefault() {
        channel.options.put(HopLimits.OPTION, -1);

        assertEquals(HopLimits.DEFAULT, socket.unicastHopLimit());
    }

    @Test
    void aHopLimitTheHostLacksIsNotSupported() {
        channel.options.remove(HopLimits.OPTION);

        assertEquals(ErrorCode.NOT_SUPPORTED, errorOf(socket::unicastHopLimit));
        assertEquals(ErrorCode.NOT_SUPPORTED, errorOf(() -> socket.setUnicastHopLimit((short) 64)));
    }

    @Test
    void theHopLimitOfADroppedSocketIsInvalid() {
        socket.drop();

        assertEquals(ErrorCode.INVALID_STATE, errorOf(socket::unicastHopLimit));
        assertEquals(ErrorCode.INVALID_STATE, errorOf(() -> socket.setUnicastHopLimit((short) 64)));
    }

    private void bind() {
        socket.startBind(NETWORK, address(LOCAL));
        channel.bound.complete(null);
        socket.finishBind();
    }

    /** Streams with no remote, ready to use. */
    private Tuple2<IncomingDatagramStream, OutgoingDatagramStream> streams() {
        bind();
        Tuple2<IncomingDatagramStream, OutgoingDatagramStream> streams = socket.stream(null);
        channel.connects.get(0).complete(null);
        return streams;
    }

    private static int permit(OutgoingDatagramStream out) {
        return out.checkSend().intValueExact();
    }

    private List<String> sent() {
        return channel.sent.stream()
                .map(
                        packet ->
                                new String(packet.getData(), 0, packet.getLength(), UTF_8)
                                        + " -> "
                                        + packet.getSocketAddress())
                .collect(Collectors.toList());
    }

    private static List<String> received(IncomingDatagramStream in, int max) {
        return in.receive(BigInteger.valueOf(max)).stream()
                .map(HostUdpSocketTest::describe)
                .collect(Collectors.toList());
    }

    private static String describe(IncomingDatagram datagram) {
        return new String(datagram.data(), UTF_8)
                + " <- "
                + IpAddresses.toInet(datagram.remoteAddress());
    }

    private static OutgoingDatagram datagram(String data, InetSocketAddress to) {
        return new OutgoingDatagram(data.getBytes(UTF_8), to == null ? null : address(to));
    }

    private static IpSocketAddress address(InetSocketAddress address) {
        return IpAddresses.fromInet(address);
    }

    private static ErrorCode errorOf(Executable call) {
        return assertThrows(ErrorCodeException.class, call).error();
    }
}
