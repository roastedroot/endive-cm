package run.endive.cm.wasi.p2.sockets.ipnamelookup;

import java.net.IDN;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.ipnamelookup.Host;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.ipnamelookup.ResolveAddressStream;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.ErrorCode;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.ErrorCodeException;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.Network;
import run.endive.cm.wasi.p2.io.poll.HostPoll;
import run.endive.cm.wasi.p2.sockets.NameResolver;
import run.endive.cm.wasi.p2.sockets.network.ErrorCodes;
import run.endive.cm.wasi.p2.sockets.network.HostNetwork;
import run.endive.cm.wasi.p2.sockets.network.IpAddresses;

/**
 * The host side of {@code wasi:sockets/ip-name-lookup}, which hands each name to a {@link
 * NameResolver}.
 *
 * <p>An IP address is parsed without asking the resolver. A domain name is converted to ASCII
 * first, so the resolver never sees Unicode. A network whose policy does not allow name lookup is
 * refused with {@code access-denied}.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/ip-name-lookup.wit">wasi:sockets/ip-name-lookup</a>
 */
public class HostIpNameLookup implements Host {

    private static final Pattern IPV4 =
            Pattern.compile("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})");

    /**
     * An IPv6 address without a zone. The JDK parses text starting this way as an address and
     * looks up anything else as a name.
     */
    private static final Pattern IPV6 = Pattern.compile("(?=.*:)[0-9A-Fa-f:][0-9A-Fa-f:.]*");

    /** The longest domain name DNS carries, leaving out the trailing dot. */
    private static final int MAX_NAME = 253;

    private final HostPoll poll;
    private final NameResolver resolver;

    /**
     * @param poll the poll a stream subscribes through
     * @param resolver what resolves each domain name
     */
    public HostIpNameLookup(HostPoll poll, NameResolver resolver) {
        this.poll = Objects.requireNonNull(poll, "poll");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    /**
     * Starts resolving {@code name}, which never blocks. A policy denying name lookup fails it with
     * {@code permanent-resolver-failure}, as wasmtime does, since the interface lists no {@code
     * access-denied}.
     *
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/ip-name-lookup.wit">wasi:sockets/ip-name-lookup, resolve-addresses</a>
     */
    @Override
    public ResolveAddressStream resolveAddresses(Network network, String name) {
        if (!HostNetwork.of(network).policy().allowsIpNameLookup()) {
            throw ErrorCodes.error(ErrorCode.PERMANENT_RESOLVER_FAILURE);
        }
        InetAddress literal = ipLiteral(name);
        if (literal != null) {
            return new HostResolveAddressStream(
                    poll, CompletableFuture.completedFuture(List.of(literal)));
        }
        return new HostResolveAddressStream(poll, resolve(asciiName(name)));
    }

    private CompletableFuture<List<InetAddress>> resolve(String name) {
        try {
            return Objects.requireNonNull(resolver.resolveAll(name), "lookup");
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /** The address {@code name} spells, or {@code null} when it is not an IP address. */
    private static InetAddress ipLiteral(String name) {
        boolean bracketed = name.startsWith("[") && name.endsWith("]");
        if (bracketed || name.indexOf(':') >= 0) {
            String ipv6 = bracketed ? name.substring(1, name.length() - 1) : name;
            if (!IPV6.matcher(ipv6).matches()) {
                throw invalid();
            }
            try {
                // The JDK parses this as an IPv6 address and never looks it up.
                return IpAddresses.unmapped(InetAddress.getByName(ipv6));
            } catch (UnknownHostException e) {
                throw invalid();
            }
        }
        Matcher ipv4 = IPV4.matcher(name);
        if (!ipv4.matches()) {
            return null;
        }
        byte[] bytes = new byte[4];
        for (int i = 0; i < bytes.length; i++) {
            int octet = Integer.parseInt(ipv4.group(i + 1));
            if (octet > 255) {
                throw invalid();
            }
            bytes[i] = (byte) octet;
        }
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException e) {
            throw new IllegalStateException("4 bytes are always an IPv4 address", e);
        }
    }

    /**
     * {@code name} converted to ASCII with IDNA, refused unless every label holds only letters,
     * digits, hyphens, and underscores.
     */
    private static String asciiName(String name) {
        String ascii;
        try {
            ascii = IDN.toASCII(name, IDN.ALLOW_UNASSIGNED);
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
        String labels = ascii.endsWith(".") ? ascii.substring(0, ascii.length() - 1) : ascii;
        if (labels.isEmpty() || labels.length() > MAX_NAME) {
            throw invalid();
        }
        for (String label : labels.split("\\.", -1)) {
            if (label.isEmpty()) {
                throw invalid();
            }
            for (int i = 0; i < label.length(); i++) {
                char c = label.charAt(i);
                boolean allowed =
                        (c >= 'a' && c <= 'z')
                                || (c >= 'A' && c <= 'Z')
                                || (c >= '0' && c <= '9')
                                || c == '-'
                                || c == '_';
                if (!allowed) {
                    throw invalid();
                }
            }
        }
        return ascii;
    }

    private static ErrorCodeException invalid() {
        return ErrorCodes.error(ErrorCode.INVALID_ARGUMENT);
    }
}
