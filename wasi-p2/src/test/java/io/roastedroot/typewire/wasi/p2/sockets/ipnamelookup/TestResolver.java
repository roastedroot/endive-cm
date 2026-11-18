package io.roastedroot.typewire.wasi.p2.sockets.ipnamelookup;

import io.roastedroot.typewire.wasi.p2.sockets.NameResolver;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/** A resolver answering each name it is asked through a function. */
final class TestResolver implements NameResolver {

    private final Function<String, CompletableFuture<List<InetAddress>>> answers;
    private final List<String> asked = new CopyOnWriteArrayList<>();
    private final List<CompletableFuture<List<InetAddress>>> lookups = new CopyOnWriteArrayList<>();

    TestResolver(Function<String, CompletableFuture<List<InetAddress>>> answers) {
        this.answers = answers;
    }

    /** A resolver that fails the test if it is asked anything. */
    static TestResolver unused() {
        return new TestResolver(
                name -> {
                    throw new AssertionError("resolver asked for " + name);
                });
    }

    static CompletableFuture<List<InetAddress>> found(String... literals) {
        return CompletableFuture.completedFuture(addresses(literals));
    }

    static CompletableFuture<List<InetAddress>> failed(Throwable cause) {
        return CompletableFuture.failedFuture(cause);
    }

    static CompletableFuture<List<InetAddress>> pending() {
        return new CompletableFuture<>();
    }

    /** Addresses found after {@code millis}, so a guest has to wait for them. */
    static CompletableFuture<List<InetAddress>> foundAfter(long millis, String... literals) {
        return CompletableFuture.supplyAsync(
                () -> addresses(literals),
                CompletableFuture.delayedExecutor(millis, TimeUnit.MILLISECONDS));
    }

    /** Every name asked, in order. */
    List<String> asked() {
        return asked;
    }

    /** The lookup the host is waiting on for each name asked, in order. */
    List<CompletableFuture<List<InetAddress>>> lookups() {
        return lookups;
    }

    @Override
    public CompletableFuture<List<InetAddress>> resolveAll(String name) {
        asked.add(name);
        CompletableFuture<List<InetAddress>> lookup = answers.apply(name);
        lookups.add(lookup);
        return lookup;
    }

    private static List<InetAddress> addresses(String... literals) {
        try {
            InetAddress[] addresses = new InetAddress[literals.length];
            for (int i = 0; i < literals.length; i++) {
                addresses[i] = InetAddress.getByName(literals[i]);
            }
            return List.of(addresses);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
