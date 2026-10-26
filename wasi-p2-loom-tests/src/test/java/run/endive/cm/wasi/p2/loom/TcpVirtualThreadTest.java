package run.endive.cm.wasi.p2.loom;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import run.endive.cm.runtime.ComponentInstance;
import run.endive.cm.runtime.ComponentStore;
import run.endive.cm.types.WasmComponent;
import run.endive.cm.wasi.p2.Guests;
import run.endive.cm.wasi.p2.WasiPreview2;
import run.endive.cm.wasi.p2.sockets.NetworkPolicy;

class TcpVirtualThreadTest {

    /**
     * Guests serving over TCP wait for a connection on more virtual threads than there are
     * carriers, and guests on other virtual threads connect to them. That works only if each
     * waiting guest gives up its carrier.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void guestsTalkOverTcpOnVirtualThreads(@TempDir Path temp) throws Exception {
        WasmComponent component = Guests.build("tcp", temp);
        int pairs = 2 * Runtime.getRuntime().availableProcessors();
        NetworkPolicy loopback =
                NetworkPolicy.builder()
                        .allowTcp()
                        .allowAddresses((address, use) -> address.getAddress().isLoopbackAddress())
                        .build();

        try (var wasi = WasiPreview2.builder().withNetworkPolicy(loopback).build();
                ExecutorService virtualThreads = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> servers = new ArrayList<>();
            List<Integer> ports = new ArrayList<>();
            for (int i = 0; i < pairs; i++) {
                ComponentInstance server =
                        wasi.instantiate(new ComponentStore(), component).instance();
                ports.add(((Number) server.export("listen").apply()[0]).intValue());
                servers.add(virtualThreads.submit(() -> server.export("echo-one").apply()));
            }

            List<Future<Object[]>> replies = new ArrayList<>();
            for (int i = 0; i < pairs; i++) {
                int port = ports.get(i);
                String message = "guest " + i;
                replies.add(
                        virtualThreads.submit(
                                () ->
                                        wasi.instantiate(new ComponentStore(), component)
                                                .instance()
                                                .export("request")
                                                .apply(port, message)));
            }

            for (int i = 0; i < pairs; i++) {
                assertEquals("guest " + i, replies.get(i).get()[0]);
                servers.get(i).get();
            }
        }
    }
}
