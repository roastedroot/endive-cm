package run.endive.cm.wasi.p2.sockets.network;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import run.endive.cm.runtime.Tuple4;
import run.endive.cm.runtime.Tuple8;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.IpAddress;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.IpAddressFamily;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.IpSocketAddress;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.Ipv4SocketAddress;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.Ipv6SocketAddress;

/**
 * Converts between the addresses {@code wasi:sockets} carries and those of {@link java.net}.
 *
 * <p>An IPv6 address keeps its family in both directions, even one that maps an IPv4 address. Java
 * has no IPv6 flow information, so it is dropped on the way in and is zero on the way out.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/network.wit">wasi:sockets/network</a>
 */
public final class IpAddresses {

    private IpAddresses() {}

    public static InetSocketAddress toInet(IpSocketAddress address) {
        if (address instanceof IpSocketAddress.Ipv4) {
            Ipv4SocketAddress ipv4 = ((IpSocketAddress.Ipv4) address).value();
            return new InetSocketAddress(ipv4Address(ipv4.address()), ipv4.port());
        }
        Ipv6SocketAddress ipv6 = ((IpSocketAddress.Ipv6) address).value();
        return new InetSocketAddress(
                ipv6Address(ipv6.address(), ipv6.scopeId().intValue()), ipv6.port());
    }

    public static InetAddress toInet(IpAddress address) {
        if (address instanceof IpAddress.Ipv4) {
            return ipv4Address(((IpAddress.Ipv4) address).value());
        }
        return ipv6Address(((IpAddress.Ipv6) address).value(), 0);
    }

    public static IpSocketAddress fromInet(InetSocketAddress address) {
        InetAddress ip = address.getAddress();
        if (ip instanceof Inet4Address) {
            return new IpSocketAddress.Ipv4(
                    new Ipv4SocketAddress(address.getPort(), ipv4Tuple(ip.getAddress())));
        }
        return new IpSocketAddress.Ipv6(
                new Ipv6SocketAddress(
                        address.getPort(),
                        0L,
                        ipv6Tuple(ip.getAddress()),
                        Integer.toUnsignedLong(((Inet6Address) ip).getScopeId())));
    }

    public static IpAddress fromInet(InetAddress address) {
        if (address instanceof Inet4Address) {
            return new IpAddress.Ipv4(ipv4Tuple(address.getAddress()));
        }
        return new IpAddress.Ipv6(ipv6Tuple(address.getAddress()));
    }

    public static IpAddressFamily familyOf(IpSocketAddress address) {
        return address instanceof IpSocketAddress.Ipv4
                ? IpAddressFamily.IPV4
                : IpAddressFamily.IPV6;
    }

    public static IpAddressFamily familyOf(InetAddress address) {
        return address instanceof Inet4Address ? IpAddressFamily.IPV4 : IpAddressFamily.IPV6;
    }

    /** Whether {@code address} is an IPv6 address mapping an IPv4 one, as in {@code ::ffff:a.b.c.d}. */
    public static boolean isIpv4Mapped(InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return false;
        }
        byte[] bytes = address.getAddress();
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff;
    }

    /** The IPv4 address {@code address} maps, or {@code address} itself when it maps none. */
    public static InetAddress unmapped(InetAddress address) {
        if (!isIpv4Mapped(address)) {
            return address;
        }
        try {
            return InetAddress.getByAddress(Arrays.copyOfRange(address.getAddress(), 12, 16));
        } catch (UnknownHostException e) {
            throw new IllegalStateException("4 bytes are always an IPv4 address", e);
        }
    }

    /**
     * {@code address} as an address of {@code family}, for a socket whose channel takes both
     * families. An IPv4-mapped address is unmapped for IPv4, and an IPv4 address is mapped for
     * IPv6.
     */
    public static InetSocketAddress inFamily(InetSocketAddress address, IpAddressFamily family) {
        InetAddress ip = address.getAddress();
        if (family == IpAddressFamily.IPV4 && isIpv4Mapped(ip)) {
            return new InetSocketAddress(unmapped(ip), address.getPort());
        }
        if (family == IpAddressFamily.IPV6 && ip instanceof Inet4Address) {
            byte[] bytes = new byte[16];
            bytes[10] = (byte) 0xff;
            bytes[11] = (byte) 0xff;
            System.arraycopy(ip.getAddress(), 0, bytes, 12, 4);
            try {
                return new InetSocketAddress(
                        Inet6Address.getByAddress(null, bytes, -1), address.getPort());
            } catch (UnknownHostException e) {
                throw new IllegalStateException("16 bytes are always an IPv6 address", e);
            }
        }
        return address;
    }

    private static InetAddress ipv4Address(Tuple4<Short, Short, Short, Short> address) {
        byte[] bytes = {
            address.f0().byteValue(),
            address.f1().byteValue(),
            address.f2().byteValue(),
            address.f3().byteValue()
        };
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException e) {
            throw new IllegalStateException("4 bytes are always an IPv4 address", e);
        }
    }

    /**
     * Builds an {@link Inet6Address} directly, since {@link InetAddress#getByAddress(byte[])} turns
     * a mapped address into IPv4. A scope of zero is left unset, so the address prints without one.
     */
    private static Inet6Address ipv6Address(
            Tuple8<Integer, Integer, Integer, Integer, Integer, Integer, Integer, Integer> address,
            int scopeId) {
        int[] segments = {
            address.f0(),
            address.f1(),
            address.f2(),
            address.f3(),
            address.f4(),
            address.f5(),
            address.f6(),
            address.f7()
        };
        byte[] bytes = new byte[16];
        for (int i = 0; i < segments.length; i++) {
            bytes[2 * i] = (byte) (segments[i] >>> 8);
            bytes[2 * i + 1] = (byte) segments[i];
        }
        try {
            return Inet6Address.getByAddress(null, bytes, scopeId == 0 ? -1 : scopeId);
        } catch (UnknownHostException e) {
            throw new IllegalStateException("16 bytes are always an IPv6 address", e);
        }
    }

    private static Tuple4<Short, Short, Short, Short> ipv4Tuple(byte[] bytes) {
        return Tuple4.of(
                (short) (bytes[0] & 0xff),
                (short) (bytes[1] & 0xff),
                (short) (bytes[2] & 0xff),
                (short) (bytes[3] & 0xff));
    }

    private static Tuple8<Integer, Integer, Integer, Integer, Integer, Integer, Integer, Integer>
            ipv6Tuple(byte[] bytes) {
        return Tuple8.of(
                segment(bytes, 0),
                segment(bytes, 1),
                segment(bytes, 2),
                segment(bytes, 3),
                segment(bytes, 4),
                segment(bytes, 5),
                segment(bytes, 6),
                segment(bytes, 7));
    }

    private static int segment(byte[] bytes, int index) {
        return ((bytes[2 * index] & 0xff) << 8) | (bytes[2 * index + 1] & 0xff);
    }
}
