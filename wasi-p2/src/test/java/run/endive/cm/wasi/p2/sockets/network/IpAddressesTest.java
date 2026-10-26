package run.endive.cm.wasi.p2.sockets.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;
import run.endive.cm.runtime.Tuple4;
import run.endive.cm.runtime.Tuple8;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.IpAddress;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.IpAddressFamily;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.IpSocketAddress;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.Ipv4SocketAddress;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.Ipv6SocketAddress;

class IpAddressesTest {

    private static final IpSocketAddress IPV4 =
            new IpSocketAddress.Ipv4(
                    new Ipv4SocketAddress(
                            8080, Tuple4.of((short) 192, (short) 168, (short) 0, (short) 255)));

    private static final IpSocketAddress IPV6 =
            new IpSocketAddress.Ipv6(
                    new Ipv6SocketAddress(
                            443, 0L, Tuple8.of(0x2001, 0xdb8, 0, 0, 0, 0, 0xff00, 0xffff), 0L));

    @Test
    void anIpv4SocketAddressConvertsBothWays() throws UnknownHostException {
        InetSocketAddress inet = IpAddresses.toInet(IPV4);

        assertEquals(new InetSocketAddress(InetAddress.getByName("192.168.0.255"), 8080), inet);
        assertEquals(IPV4, IpAddresses.fromInet(inet));
    }

    @Test
    void anIpv6SocketAddressConvertsBothWays() throws UnknownHostException {
        InetSocketAddress inet = IpAddresses.toInet(IPV6);

        assertEquals(
                new InetSocketAddress(InetAddress.getByName("2001:db8::ff00:ffff"), 443), inet);
        assertEquals(IPV6, IpAddresses.fromInet(inet));
    }

    @Test
    void anIpv6ScopeConvertsBothWays() {
        IpSocketAddress scoped =
                new IpSocketAddress.Ipv6(
                        new Ipv6SocketAddress(80, 0L, Tuple8.of(0xfe80, 0, 0, 0, 0, 0, 0, 1), 3L));

        InetSocketAddress inet = IpAddresses.toInet(scoped);

        assertEquals(3, ((Inet6Address) inet.getAddress()).getScopeId());
        assertEquals(scoped, IpAddresses.fromInet(inet));
    }

    @Test
    void flowInformationIsDropped() {
        IpSocketAddress flowing =
                new IpSocketAddress.Ipv6(
                        new Ipv6SocketAddress(80, 7L, Tuple8.of(0, 0, 0, 0, 0, 0, 0, 1), 0L));

        IpSocketAddress back = IpAddresses.fromInet(IpAddresses.toInet(flowing));

        assertEquals(0L, ((IpSocketAddress.Ipv6) back).value().flowInfo());
    }

    @Test
    void anIpv4MappedAddressStaysIpv6() {
        IpAddress mapped = new IpAddress.Ipv6(Tuple8.of(0, 0, 0, 0, 0, 0xffff, 0x7f00, 1));

        InetAddress inet = IpAddresses.toInet(mapped);

        assertInstanceOf(Inet6Address.class, inet);
        assertTrue(IpAddresses.isIpv4Mapped(inet));
        assertEquals(mapped, IpAddresses.fromInet(inet));
    }

    @Test
    void aMappedAddressUnmapsToIpv4() throws UnknownHostException {
        InetAddress mapped =
                IpAddresses.toInet(new IpAddress.Ipv6(Tuple8.of(0, 0, 0, 0, 0, 0xffff, 0x7f00, 1)));

        assertEquals(InetAddress.getByName("127.0.0.1"), IpAddresses.unmapped(mapped));
        assertEquals(
                InetAddress.getByName("::1"), IpAddresses.unmapped(InetAddress.getByName("::1")));
    }

    @Test
    void onlyAMappedAddressIsIpv4Mapped() throws UnknownHostException {
        assertFalse(IpAddresses.isIpv4Mapped(InetAddress.getByName("127.0.0.1")));
        assertFalse(IpAddresses.isIpv4Mapped(InetAddress.getByName("::1")));
        assertFalse(IpAddresses.isIpv4Mapped(InetAddress.getByName("::7f00:1")));
    }

    @Test
    void anIpAddressConvertsBothWays() throws UnknownHostException {
        IpAddress ipv4 = new IpAddress.Ipv4(Tuple4.of((short) 10, (short) 0, (short) 0, (short) 1));

        assertEquals(InetAddress.getByName("10.0.0.1"), IpAddresses.toInet(ipv4));
        assertEquals(ipv4, IpAddresses.fromInet(InetAddress.getByName("10.0.0.1")));
    }

    @Test
    void eachAddressHasItsFamily() throws UnknownHostException {
        assertEquals(IpAddressFamily.IPV4, IpAddresses.familyOf(IPV4));
        assertEquals(IpAddressFamily.IPV6, IpAddresses.familyOf(IPV6));
        assertEquals(IpAddressFamily.IPV4, IpAddresses.familyOf(InetAddress.getByName("10.0.0.1")));
        assertEquals(IpAddressFamily.IPV6, IpAddresses.familyOf(InetAddress.getByName("::1")));
    }

    @Test
    void anAddressIsPutInTheFamilyOfItsSocket() throws UnknownHostException {
        InetSocketAddress ipv4 = new InetSocketAddress(InetAddress.getByName("192.0.2.1"), 80);
        InetSocketAddress mapped = IpAddresses.inFamily(ipv4, IpAddressFamily.IPV6);

        assertInstanceOf(Inet6Address.class, mapped.getAddress());
        assertTrue(IpAddresses.isIpv4Mapped(mapped.getAddress()));
        assertEquals(80, mapped.getPort());
        assertEquals(ipv4, IpAddresses.inFamily(mapped, IpAddressFamily.IPV4));
        assertEquals(ipv4, IpAddresses.inFamily(ipv4, IpAddressFamily.IPV4));
        assertEquals(mapped, IpAddresses.inFamily(mapped, IpAddressFamily.IPV6));
    }
}
