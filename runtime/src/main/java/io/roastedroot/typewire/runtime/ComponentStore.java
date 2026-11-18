package io.roastedroot.typewire.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import run.endive.runtime.Machine;
import run.endive.wasm.WasmModule;

/**
 * The top-level thing a component runtime hands out, and the boundary of what can interact.
 *
 * <p>A store holds any number of {@link ComponentInstance}s at any nesting depth, and only
 * instances within one store may be wired to each other. A resource handle indexes some instance's
 * table and a resource type is compared by identity, so neither means anything in another store.
 *
 * <p>What lives here is what outlives any one instantiation. A component's index spaces and exports
 * belong to the instantiation that built them, not to the store.
 *
 * @see <a href="https://github.com/WebAssembly/component-model/blob/706074c96bc14cfc58469e1bdc452bb4d91921c7/design/mvp/Concurrency.md#threads-and-tasks">Concurrency.md, stores and instances</a>
 */
public final class ComponentStore implements AutoCloseable {

    private final List<ComponentInstance> instances = new ArrayList<>();
    private final List<run.endive.runtime.Instance> builtCores = new ArrayList<>();
    private final Function<run.endive.runtime.Instance, Machine> machineFactory;
    private final Function<WasmModule, run.endive.runtime.Instance.Builder> coreBuilders;

    public ComponentStore() {
        this(null);
    }

    /**
     * @param machineFactory how core modules instantiated into this store execute, {@code null} for
     *     the engine's default
     */
    public ComponentStore(Function<run.endive.runtime.Instance, Machine> machineFactory) {
        this(machineFactory, null);
    }

    private ComponentStore(
            Function<run.endive.runtime.Instance, Machine> machineFactory,
            Function<WasmModule, run.endive.runtime.Instance.Builder> coreBuilders) {
        this.machineFactory = machineFactory;
        this.coreBuilders = coreBuilders;
    }

    /**
     * A store whose core instances start from builders {@code coreInstances} supplies,
     * one per core module instantiated.
     * An engine that needs its own memory, table, or global types configures them on the builder.
     * The linker adds the imports and builds it.
     */
    public static ComponentStore withCoreInstances(
            Function<WasmModule, run.endive.runtime.Instance.Builder> coreInstances) {
        return new ComponentStore(null, Objects.requireNonNull(coreInstances, "coreInstances"));
    }

    /** Called once per {@link ComponentInstance}, from its constructor. */
    void register(ComponentInstance instance) {
        instances.add(instance);
    }

    /** Every instance in this store, at any nesting depth, in creation order. */
    public List<ComponentInstance> instances() {
        return Collections.unmodifiableList(instances);
    }

    /** Called by the linker for every core instance it builds in this store. */
    void registerCore(run.endive.runtime.Instance core) {
        builtCores.add(core);
    }

    /** Every core module instance built in this store, in creation order. */
    public List<run.endive.runtime.Instance> coreInstances() {
        return Collections.unmodifiableList(builtCores);
    }

    /**
     * Closes every core instance built in this store, last first,
     * which releases what their machines hold outside the Java heap.
     * The store's instances must not be called afterwards.
     */
    @Override
    public void close() {
        RuntimeException failure = null;
        for (int i = builtCores.size() - 1; i >= 0; i--) {
            try {
                builtCores.get(i).close();
            } catch (RuntimeException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        builtCores.clear();
        if (failure != null) {
            throw failure;
        }
    }

    /** The builder a core instance of {@code module} starts from. */
    run.endive.runtime.Instance.Builder coreInstanceBuilder(WasmModule module) {
        if (coreBuilders != null) {
            return coreBuilders.apply(module);
        }
        var builder = run.endive.runtime.Instance.builder(module);
        if (machineFactory != null) {
            builder.withMachineFactory(machineFactory);
        }
        return builder;
    }

    /**
     * Rejects a value that came from another store. Resource identities and handle indices are
     * store-relative, so wiring one across would silently mean something else.
     */
    void requireOwns(ComponentInstance instance) {
        if (instance != null && instance.store() != this) {
            throw new LinkageException("component instance belongs to a different component store");
        }
    }
}
