package io.roastedroot.typewire.wasi.p2.sockets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.roastedroot.typewire.wasi.p2.sockets.NetworkPolicy.AddressUse;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

class NetworkPolicyTest {

    private static final InetSocketAddress LOOPBACK = new InetSocketAddress("127.0.0.1", 8080);
    private static final InetSocketAddress REMOTE = new InetSocketAddress("192.0.2.1", 8080);

    @Test
    void theDefaultPolicyGrantsNothing() {
        NetworkPolicy policy = NetworkPolicy.denyAll();

        assertFalse(policy.allowsTcp());
        assertFalse(policy.allowsUdp());
        assertFalse(policy.allowsIpNameLookup());
        assertFalse(policy.allowsAddress(LOOPBACK, AddressUse.TCP_CONNECT));
    }

    @Test
    void allowingEverythingGrantsEveryProtocolAndAddress() {
        NetworkPolicy policy = NetworkPolicy.allowAll();

        assertTrue(policy.allowsTcp());
        assertTrue(policy.allowsUdp());
        assertTrue(policy.allowsIpNameLookup());
        assertTrue(policy.allowsAddress(REMOTE, AddressUse.UDP_OUTGOING_DATAGRAM));
    }

    @Test
    void aProtocolIsGrantedWithoutAnyAddress() {
        NetworkPolicy policy = NetworkPolicy.builder().allowTcp().build();

        assertTrue(policy.allowsTcp());
        assertFalse(policy.allowsUdp());
        assertFalse(policy.allowsAddress(LOOPBACK, AddressUse.TCP_BIND));
    }

    @Test
    void theAddressCheckSeesTheAddressAndItsUse() {
        NetworkPolicy policy =
                NetworkPolicy.builder()
                        .allowAddresses(
                                (address, use) ->
                                        address.getAddress().isLoopbackAddress()
                                                && use == AddressUse.TCP_CONNECT)
                        .build();

        assertTrue(policy.allowsAddress(LOOPBACK, AddressUse.TCP_CONNECT));
        assertFalse(policy.allowsAddress(LOOPBACK, AddressUse.TCP_BIND));
        assertFalse(policy.allowsAddress(REMOTE, AddressUse.TCP_CONNECT));
    }
}
