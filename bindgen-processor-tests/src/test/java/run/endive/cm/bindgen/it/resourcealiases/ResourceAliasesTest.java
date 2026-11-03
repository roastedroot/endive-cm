package run.endive.cm.bindgen.it.resourcealiases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import run.endive.cm.bindgen.it.Components;
import run.endive.cm.bindgen.it.resourcealiases.example.resourcealiases.types.Fields;
import run.endive.cm.bindgen.it.resourcealiases.example.resourcealiases.types.Host;
import run.endive.cm.bindgen.it.resourcealiases.example.resourcealiases.types.Request;
import run.endive.cm.runtime.Bindgen;
import run.endive.cm.runtime.ComponentStore;
import run.endive.cm.types.WasmComponent;

/**
 * A world aliasing a resource inside one interface, written for this test because none of the
 * bindgen! examples does, while wasi:http names its fields as headers and trailers.
 *
 * <p>The guest hands a handle across under both names, so a passing call shows the alias is the
 * same resource type at link time and at run time.
 */
@Bindgen(world = "resource-aliases")
public class ResourceAliasesTest {

    private static WasmComponent component;

    @BeforeAll
    static void buildComponent() {
        component =
                Components.build(
                        Components.bytes("/resource-aliases.wat"),
                        Components.text("/wit/resource-aliases.wit"),
                        "resource-aliases");
    }

    /** A handle made as fields is accepted as headers, and comes back usable as fields. */
    @Test
    void anAliasedHandleCrossesUnderEitherName() {
        Types types = new Types();

        long total = instantiate(types).run();

        assertEquals(7, total);
    }

    /** Every name reaches the one object the host made. */
    @Test
    void anAliasedHandleArrivesAsTheObjectBehindIt() {
        Types types = new Types();

        instantiate(types).run();

        assertEquals(1, types.made.size());
        assertSame(types.made.get(0), types.requested.get(0));
        assertSame(types.made.get(0), types.counted.get(0));
    }

    private static ResourceAliasesWorld instantiate(Types types) {
        return ResourceAliasesWorld.instantiate(new ComponentStore(), component, types);
    }

    /** The host side of the one interface the world imports. */
    private static final class Types implements ResourceAliasesWorld.Imports, Host {

        private final List<Fields> made = new ArrayList<>();
        private final List<Fields> requested = new ArrayList<>();
        private final List<Fields> counted = new ArrayList<>();

        @Override
        public Host types() {
            return this;
        }

        @Override
        public Fields fields() {
            Entries entries = new Entries();
            made.add(entries);
            return entries;
        }

        @Override
        public Request request(Fields headers) {
            requested.add(headers);
            return () -> headers;
        }

        @Override
        public Long count(Fields h) {
            counted.add(h);
            return h.total();
        }
    }

    private static final class Entries implements Fields {

        private long total;

        @Override
        public void add(Long value) {
            total += value;
        }

        @Override
        public Long total() {
            return total;
        }
    }
}
