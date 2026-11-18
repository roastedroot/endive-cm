package io.roastedroot.typewire.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.roastedroot.typewire.types.WasmComponent;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import run.endive.runtime.Instance;
import run.endive.runtime.InterpreterMachine;
import run.endive.wasm.WasmModule;

/** A store can supply the builder every core instance starts from. */
public class CoreInstanceBuilderTests {

    @Test
    public void everyCoreModuleStartsFromTheSuppliedBuilder() {
        List<WasmModule> requested = new ArrayList<>();
        List<Instance> machined = new ArrayList<>();
        var store =
                ComponentStore.withCoreInstances(
                        module -> {
                            requested.add(module);
                            return Instance.builder(module)
                                    .withMachineFactory(
                                            instance -> {
                                                machined.add(instance);
                                                return new InterpreterMachine(instance);
                                            });
                        });

        var instance = ComponentLinker.builder().build().instantiate(store, component(), Map.of());

        assertEquals(1, requested.size());
        assertEquals(1, machined.size());
        assertSame(requested.get(0), machined.get(0).module());
        assertEquals(42L, instance.export("answer").apply()[0]);
    }

    @Test
    public void closingTheStoreClosesEveryCoreInstance() {
        List<String> closed = new ArrayList<>();
        var store =
                ComponentStore.withCoreInstances(
                        module ->
                                Instance.builder(module)
                                        .withMachineFactory(
                                                instance ->
                                                        new InterpreterMachine(instance) {
                                                            @Override
                                                            public void close() {
                                                                closed.add("machine");
                                                            }
                                                        }));
        ComponentLinker.builder().build().instantiate(store, component(), Map.of());

        assertEquals(1, store.coreInstances().size());
        store.close();

        assertEquals(List.of("machine"), closed);
        assertEquals(0, store.coreInstances().size());
    }

    private static WasmComponent component() {
        try (InputStream is =
                CoreInstanceBuilderTests.class.getResourceAsStream(
                        "/core-instance-builder/answer.wat")) {
            assertNotNull(is);
            return TestComponents.fromWat(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
