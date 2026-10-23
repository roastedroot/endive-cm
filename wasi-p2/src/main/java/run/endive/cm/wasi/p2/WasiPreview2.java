package run.endive.cm.wasi.p2;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.stream.Collectors;
import run.endive.cm.abi.VariantValue;
import run.endive.cm.runtime.ComponentInstance;
import run.endive.cm.runtime.ComponentStore;
import run.endive.cm.runtime.InterfaceVersions;
import run.endive.cm.types.WasmComponent;
import run.endive.cm.wasi.p2.bindings.ImportsWorld;
import run.endive.cm.wasi.p2.cli.WasiExitException;
import run.endive.cm.wasi.p2.cli.environment.HostEnvironment;
import run.endive.cm.wasi.p2.cli.exit.HostExit;
import run.endive.cm.wasi.p2.cli.stderr.HostStderr;
import run.endive.cm.wasi.p2.cli.stdin.HostStdin;
import run.endive.cm.wasi.p2.cli.stdout.HostStdout;
import run.endive.cm.wasi.p2.cli.terminalstderr.HostTerminalStderr;
import run.endive.cm.wasi.p2.cli.terminalstdin.HostTerminalStdin;
import run.endive.cm.wasi.p2.cli.terminalstdout.HostTerminalStdout;
import run.endive.cm.wasi.p2.clocks.monotonicclock.HostMonotonicClock;
import run.endive.cm.wasi.p2.clocks.wallclock.HostWallClock;
import run.endive.cm.wasi.p2.filesystem.preopens.HostPreopens;
import run.endive.cm.wasi.p2.filesystem.types.HostTypes;
import run.endive.cm.wasi.p2.filesystem.types.OpenFiles;
import run.endive.cm.wasi.p2.io.poll.HostPoll;
import run.endive.cm.wasi.p2.random.insecure.HostInsecure;
import run.endive.cm.wasi.p2.random.insecureseed.HostInsecureSeed;
import run.endive.cm.wasi.p2.random.random.HostRandom;
import run.endive.cm.wasi.p2.sockets.HostSockets;
import run.endive.cm.wasi.p2.sockets.HostSocketsFactory;
import run.endive.cm.wasi.p2.sockets.NameResolver;
import run.endive.cm.wasi.p2.sockets.NetworkPolicy;
import run.endive.cm.wasi.p2.sockets.instancenetwork.HostInstanceNetwork;
import run.endive.cm.wasi.p2.sockets.netty.NettyNameResolver;

/**
 * The WASI preview 2 interfaces this host implements, ready to satisfy a component's imports.
 *
 * <p>Blocking calls such as {@code poll} wait on the thread the guest runs on, which may be a
 * virtual thread.
 *
 * <pre>{@code
 * try (var wasi = WasiPreview2.builder().build()) {
 *     ImportsWorld world = wasi.instantiate(new ComponentStore(), component);
 * }
 * }</pre>
 */
public final class WasiPreview2 implements ImportsWorld.Imports, AutoCloseable {

    /** The key every compatible 0.2 version of {@code wasi:cli/run} shares. */
    private static final String CLI_RUN = "wasi:cli/run@0.2";

    private final ScheduledExecutorService scheduler;
    private final boolean ownsScheduler;
    private final HostPoll poll;
    private final HostSockets sockets;
    private final HostMonotonicClock monotonicClock;
    private final HostWallClock wallClock;
    private final HostTypes types;
    private final OpenFiles files = new OpenFiles();
    private final HostPreopens preopens;
    private final HostRandom random;
    private final HostInsecure insecure;
    private final HostInsecureSeed insecureSeed;
    private final HostInstanceNetwork instanceNetwork;
    private final HostEnvironment environment;
    private final HostExit exit;
    private final HostStdin stdin;
    private final HostStdout stdout;
    private final HostStderr stderr;
    private final HostTerminalStdin terminalStdin;
    private final HostTerminalStdout terminalStdout;
    private final HostTerminalStderr terminalStderr;

    private WasiPreview2(Builder builder) {
        this.poll = new HostPoll();
        this.stdin = new HostStdin(poll, builder.stdin);
        this.stdout = new HostStdout(poll, builder.stdout);
        this.stderr = new HostStderr(poll, builder.stderr);
        this.terminalStdin = new HostTerminalStdin(builder.stdinTty);
        this.terminalStdout = new HostTerminalStdout(builder.stdoutTty);
        this.terminalStderr = new HostTerminalStderr(builder.stderrTty);
        this.sockets =
                HostSocketsFactory.create(
                        builder.networkPolicy, poll, builder.scheduler, builder.nameResolver);
        this.ownsScheduler = builder.scheduler == null && sockets.eventLoopGroup() == null;
        if (builder.scheduler != null) {
            this.scheduler = builder.scheduler;
        } else if (sockets.eventLoopGroup() != null) {
            this.scheduler = sockets.eventLoopGroup();
        } else {
            this.scheduler = defaultScheduler();
        }
        try {
            this.monotonicClock = new HostMonotonicClock(scheduler, poll);
            this.wallClock =
                    builder.clock == null
                            ? new HostWallClock()
                            : new HostWallClock(builder.clock, builder.clockResolution);
            this.types = new HostTypes();
            this.preopens = new HostPreopens(builder.directories, builder.readOnly, poll, files);
            this.random =
                    builder.random == null ? new HostRandom() : new HostRandom(builder.random);
            this.insecure =
                    builder.insecureRandom == null
                            ? new HostInsecure()
                            : new HostInsecure(builder.insecureRandom);
            this.insecureSeed =
                    builder.insecureSeed == null ? new HostInsecureSeed() : builder.insecureSeed;
            this.instanceNetwork = new HostInstanceNetwork(builder.networkPolicy);
            this.environment = new HostEnvironment(builder.arguments, builder.environment);
            this.exit = new HostExit();
        } catch (RuntimeException e) {
            close();
            throw e;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Instantiates {@code component}, satisfying its WASI imports with this host. */
    public ImportsWorld instantiate(ComponentStore store, WasmComponent component) {
        return ImportsWorld.instantiate(store, component, this);
    }

    /**
     * Instantiates {@code component} and calls the {@code run} it exports from {@code
     * wasi:cli/run}, giving back the exit code. That is 0 when {@code run} returns {@code ok}, 1
     * when it returns {@code err}, and the code the guest passes to {@code wasi:cli/exit} when it
     * exits. An exit abandons the guest's call partway, so the instance should not be called
     * again.
     *
     * @throws IllegalArgumentException when {@code component} does not export {@code wasi:cli/run}
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/cli/wit/run.wit">wasi:cli/run</a>
     */
    public int run(ComponentStore store, WasmComponent component) {
        try {
            ComponentInstance instance = instantiate(store, component).instance();
            Object[] status =
                    instance.exportedInstance(cliRun(instance.exportNames())).export("run").apply();
            return "ok".equals(((VariantValue) status[0]).label()) ? 0 : 1;
        } catch (WasiExitException e) {
            return e.exitCode();
        }
    }

    /**
     * The one name among {@code exportNames} compatible with {@code wasi:cli/run@0.2}, as {@link
     * InterfaceVersions} decides for the interfaces a component imports.
     *
     * @throws IllegalArgumentException when there is none, or more than one
     */
    static String cliRun(Collection<String> exportNames) {
        List<String> runs =
                exportNames.stream()
                        .filter(name -> CLI_RUN.equals(InterfaceVersions.compatibleKey(name)))
                        .collect(Collectors.toList());
        if (runs.size() != 1) {
            throw new IllegalArgumentException(
                    "the component exports " + exportNames + " rather than one wasi:cli/run");
        }
        return runs.get(0);
    }

    @Override
    public HostPoll poll() {
        return poll;
    }

    @Override
    public HostMonotonicClock monotonicClock() {
        return monotonicClock;
    }

    @Override
    public HostWallClock wallClock() {
        return wallClock;
    }

    @Override
    public HostTypes types() {
        return types;
    }

    @Override
    public HostPreopens preopens() {
        return preopens;
    }

    @Override
    public HostRandom random() {
        return random;
    }

    @Override
    public HostInsecure insecure() {
        return insecure;
    }

    @Override
    public HostInsecureSeed insecureSeed() {
        return insecureSeed;
    }

    @Override
    public HostInstanceNetwork instanceNetwork() {
        return instanceNetwork;
    }

    @Override
    public run.endive.cm.wasi.p2.bindings.wasi.sockets.ipnamelookup.Host ipNameLookup() {
        return sockets.ipNameLookup();
    }

    @Override
    public run.endive.cm.wasi.p2.bindings.wasi.sockets.tcpcreatesocket.Host tcpCreateSocket() {
        return sockets.tcpCreateSocket();
    }

    @Override
    public run.endive.cm.wasi.p2.bindings.wasi.sockets.udpcreatesocket.Host udpCreateSocket() {
        return sockets.udpCreateSocket();
    }

    @Override
    public HostEnvironment environment() {
        return environment;
    }

    @Override
    public HostExit exit() {
        return exit;
    }

    @Override
    public HostStdin stdin() {
        return stdin;
    }

    @Override
    public HostStdout stdout() {
        return stdout;
    }

    @Override
    public HostStderr stderr() {
        return stderr;
    }

    @Override
    public HostTerminalStdin terminalStdin() {
        return terminalStdin;
    }

    @Override
    public HostTerminalStdout terminalStdout() {
        return terminalStdout;
    }

    @Override
    public HostTerminalStderr terminalStderr() {
        return terminalStderr;
    }

    /**
     * Closes every file a guest left open, stops reading stdin, and closes what the network
     * created, then shuts down the scheduler. Anything the builder was given stays open.
     */
    @Override
    public void close() {
        stdin.close();
        files.close();
        sockets.close();
        if (ownsScheduler) {
            scheduler.shutdownNow();
        }
    }

    private static ScheduledExecutorService defaultScheduler() {
        ScheduledThreadPoolExecutor scheduler =
                new ScheduledThreadPoolExecutor(
                        1,
                        task -> {
                            Thread thread = new Thread(task, "endive-wasi-p2-scheduler");
                            thread.setDaemon(true);
                            return thread;
                        });
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    public static final class Builder {

        private ScheduledExecutorService scheduler;
        private Clock clock;
        private Duration clockResolution;
        private final Map<String, Path> directories = new LinkedHashMap<>();
        private final Set<String> readOnly = new HashSet<>();
        private Random random;
        private Random insecureRandom;
        private HostInsecureSeed insecureSeed;
        private List<String> arguments = List.of();
        private final Map<String, String> environment = new LinkedHashMap<>();
        private InputStream stdin;
        private boolean stdinTty;
        private OutputStream stdout = OutputStream.nullOutputStream();
        private boolean stdoutTty;
        private OutputStream stderr = OutputStream.nullOutputStream();
        private boolean stderrTty;
        private NetworkPolicy networkPolicy = NetworkPolicy.denyAll();
        private NameResolver nameResolver;

        private Builder() {}

        /**
         * The executor that runs timers, such as those behind {@code wasi:clocks} subscriptions.
         * Any {@link ScheduledExecutorService} works, including an event loop the embedder already
         * runs. When it is a Netty event loop group, the network runs on it as well. Otherwise a
         * guest granted the network gets a one-thread NIO group of its own. A guest must never run
         * on its threads, since a blocked guest would hold up the timers it waits for. By default,
         * one is created and then shut down on {@link #close()}. When set by the embedder, it is
         * their responsibility to shut it down.
         */
        public Builder withScheduler(ScheduledExecutorService scheduler) {
            this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
            return this;
        }

        /**
         * The clock {@code wasi:clocks/wall-clock} reads, which defaults to the system clock.
         *
         * @param resolution the time one tick of {@code clock} stands for
         */
        public Builder withWallClock(Clock clock, Duration resolution) {
            this.clock = Objects.requireNonNull(clock, "clock");
            this.clockResolution = Objects.requireNonNull(resolution, "resolution");
            return this;
        }

        /**
         * Grants the guest {@code host} as a preopened directory named {@code guest}. Any
         * {@link java.nio.file.FileSystem} works, so an in-memory one makes a sandbox that touches
         * no host files.
         *
         * <p>The guest cannot reach outside {@code host} by itself. Java checks a path and then
         * acts on it in separate steps, though, so nothing else may change {@code host} while an
         * untrusted guest uses it.
         *
         * @throws IllegalArgumentException when {@code host} is not a directory, or {@code guest}
         *     already names one
         */
        public Builder withDirectory(String guest, Path host) {
            return grant(guest, host, false);
        }

        /**
         * Grants the guest {@code host} as a preopened directory named {@code guest}, which it
         * may read but not change in any way.
         *
         * @throws IllegalArgumentException when {@code host} is not a directory, or {@code guest}
         *     already names one
         */
        public Builder withReadOnlyDirectory(String guest, Path host) {
            return grant(guest, host, true);
        }

        private Builder grant(String guest, Path host, boolean readOnly) {
            Objects.requireNonNull(guest, "guest");
            Objects.requireNonNull(host, "host");
            if (!Files.isDirectory(host)) {
                throw new IllegalArgumentException(host + " is not a directory");
            }
            if (directories.containsKey(guest)) {
                throw new IllegalArgumentException("a directory is already granted as " + guest);
            }
            directories.put(guest, host);
            if (readOnly) {
                this.readOnly.add(guest);
            }
            return this;
        }

        /**
         * The generator {@code wasi:random/random} draws from, which defaults to the {@link
         * java.util.concurrent.ThreadLocalRandom} of the thread running the guest, as in Endive's
         * WASI preview 1. Pass a {@link java.security.SecureRandom} for cryptographic strength. A
         * seeded generator makes every value predictable, so it only suits tests.
         */
        public Builder withRandom(Random random) {
            this.random = Objects.requireNonNull(random, "random");
            return this;
        }

        /**
         * The generator {@code wasi:random/insecure} draws from, which defaults to the {@link
         * java.util.concurrent.ThreadLocalRandom} of the thread running the guest.
         */
        public Builder withInsecureRandom(Random random) {
            this.insecureRandom = Objects.requireNonNull(random, "random");
            return this;
        }

        /**
         * The seed {@code wasi:random/insecure-seed} returns, whose halves hold the bits of {@code
         * first} and {@code second}. By default, one is drawn when the host is built.
         */
        public Builder withInsecureSeed(long first, long second) {
            this.insecureSeed = new HostInsecureSeed(first, second);
            return this;
        }

        /**
         * The arguments {@code wasi:cli/environment} hands the guest, which are none by default. A
         * program expects its own name first, as a shell passes it, so the arguments of a Java
         * {@code main} need one in front.
         */
        public Builder withArguments(List<String> arguments) {
            this.arguments = List.copyOf(arguments);
            return this;
        }

        /**
         * Adds a variable to the environment {@code wasi:cli/environment} hands the guest, which is
         * empty by default. The guest sees the variables in the order they were first added, and a
         * name added again keeps its last value.
         */
        public Builder withEnvironment(String name, String value) {
            environment.put(
                    Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"));
            return this;
        }

        /** The stream the guest reads as stdin, which is not a terminal. Stdin is empty by default. */
        public Builder withStdin(InputStream stdin) {
            return withStdin(stdin, false);
        }

        /**
         * The stream the guest reads as stdin. A thread reads it once the guest reads or polls stdin, and
         * it is never closed. Hosts given the same stream share that thread, as {@link HostStdin}
         * describes.
         *
         * @param tty whether the stream is a terminal, as {@code wasi:cli/terminal-stdin} reports
         */
        public Builder withStdin(InputStream stdin, boolean tty) {
            this.stdin = Objects.requireNonNull(stdin, "stdin");
            this.stdinTty = tty;
            return this;
        }

        /**
         * The stream the guest writes as stdout, which is not a terminal. Stdout is discarded by
         * default.
         */
        public Builder withStdout(OutputStream stdout) {
            return withStdout(stdout, false);
        }

        /**
         * The stream the guest writes as stdout, which is never closed.
         *
         * @param tty whether the stream is a terminal, as {@code wasi:cli/terminal-stdout} reports
         */
        public Builder withStdout(OutputStream stdout, boolean tty) {
            this.stdout = Objects.requireNonNull(stdout, "stdout");
            this.stdoutTty = tty;
            return this;
        }

        /**
         * The stream the guest writes as stderr, which is not a terminal. Stderr is discarded by
         * default.
         */
        public Builder withStderr(OutputStream stderr) {
            return withStderr(stderr, false);
        }

        /**
         * The stream the guest writes as stderr, which is never closed.
         *
         * @param tty whether the stream is a terminal, as {@code wasi:cli/terminal-stderr} reports
         */
        public Builder withStderr(OutputStream stderr, boolean tty) {
            this.stderr = Objects.requireNonNull(stderr, "stderr");
            this.stderrTty = tty;
            return this;
        }

        /**
         * Connects the guest to {@link System#in}, {@link System#out} and {@link System#err}, which
         * count as terminals when the JVM has a {@link System#console() console}. The JDK gives one
         * only when both stdin and stdout are terminals, so a redirected stderr still counts as one.
         * JDK 22 to 24 give one even when the streams are redirected, unless the JVM runs with
         * {@code -Djdk.console=java.base}. Calling {@link #withStdin(InputStream, boolean)}, {@link
         * #withStdout(OutputStream, boolean)} or {@link #withStderr(OutputStream, boolean)}
         * afterwards decides for that stream instead.
         */
        public Builder inheritSystemStreams() {
            boolean tty = System.console() != null;
            return withStdin(System.in, tty)
                    .withStdout(System.out, tty)
                    .withStderr(System.err, tty);
        }

        /**
         * What the guest may do on the network through {@code wasi:sockets}. By default, it may do
         * nothing. Granting it anything needs {@code io.netty:netty-transport} and {@code
         * io.netty:netty-resolver-dns} from Netty 4.2 on the class path.
         */
        public Builder withNetworkPolicy(NetworkPolicy policy) {
            this.networkPolicy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        /**
         * What resolves the names the guest looks up, which the embedder closes. {@link
         * NettyNameResolver#of} adapts a resolver from Netty.
         *
         * <p>By default, a Netty {@code DnsNameResolver} reads the host's resolver configuration
         * and hosts file, and runs on the network's event loop group. On macOS, it also needs
         * {@code io.netty:netty-resolver-dns-native-macos} to read the system's DNS servers, and
         * warns once without it. With no configuration to read, it asks Google's public DNS
         * servers.
         */
        public Builder withNameResolver(NameResolver resolver) {
            this.nameResolver = Objects.requireNonNull(resolver, "resolver");
            return this;
        }

        public WasiPreview2 build() {
            return new WasiPreview2(this);
        }
    }
}
