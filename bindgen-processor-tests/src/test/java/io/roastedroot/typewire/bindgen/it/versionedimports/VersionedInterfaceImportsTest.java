package io.roastedroot.typewire.bindgen.it.versionedimports;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.roastedroot.typewire.bindgen.it.Components;
import io.roastedroot.typewire.bindgen.it.versionedimports.example.interfaceimports.logging.Host;
import io.roastedroot.typewire.bindgen.it.versionedimports.example.interfaceimports.logging.Level;
import io.roastedroot.typewire.runtime.Bindgen;
import io.roastedroot.typewire.runtime.ComponentStore;
import io.roastedroot.typewire.types.WasmComponent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Runs a guest that calls a versioned interface through its generated Java host binding. */
@Bindgen(world = "versioned-with-imports", path = "wit/versioned-with-imports.wit")
public class VersionedInterfaceImportsTest {

    private static WasmComponent component;

    @BeforeAll
    static void buildComponent() {
        component =
                Components.build(
                        Components.bytes("/versioned-with-imports.wat"),
                        Components.text("/wit/versioned-with-imports.wit"),
                        "versioned-with-imports");
    }

    @Test
    void theGuestCallsTheVersionedImport() {
        Recorder recorder = new Recorder();

        VersionedWithImportsWorld.instantiate(new ComponentStore(), component, () -> recorder)
                .run();

        assertEquals(List.of(Level.WARN, Level.ERROR), recorder.levels);
        assertEquals(List.of("warn: starting", "error: done"), recorder.entries);
    }

    private static final class Recorder implements Host {

        private final List<Level> levels = new ArrayList<>();
        private final List<String> entries = new ArrayList<>();

        @Override
        public void log(Level level, String msg) {
            levels.add(level);
            entries.add(level.name().toLowerCase() + ": " + msg);
        }
    }
}
