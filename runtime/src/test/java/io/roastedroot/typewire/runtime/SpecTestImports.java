package io.roastedroot.typewire.runtime;

import io.roastedroot.typewire.abi.ResourceValue;
import io.roastedroot.typewire.types.FuncType;
import io.roastedroot.typewire.types.LabelValType;
import io.roastedroot.typewire.types.PrimValType;
import io.roastedroot.typewire.types.ValType;
import java.util.HashMap;
import java.util.Map;
import run.endive.runtime.TrapException;
import run.endive.tools.wasm.Wat2Wasm;
import run.endive.wasm.Parser;
import run.endive.wasm.WasmModule;

public final class SpecTestImports {

    private static final Object[] EMPTY_RESULT = new Object[0];

    private SpecTestImports() {}

    /**
     * The host definitions the spec suite imports, built into {@code store} so that they share
     * resource identities and handle tables with the components under test.
     */
    public static Map<String, Object> build(ComponentStore store) {
        Map<String, Object> imports = new HashMap<>();
        imports.put("host-return-two", buildHostReturnTwo(store));
        imports.put("foo", buildFoo(store));
        imports.put("src", buildSrc(store));
        imports.put("reexport", buildReexport(store));
        imports.put("provider", buildProvider(store));
        imports.put("host", buildHost(store));
        return Map.copyOf(imports);
    }

    /** A bare host function, imported under a name of its own rather than through an instance. */
    private static ComponentFunction buildHostReturnTwo(ComponentStore store) {
        return HostFunction.of(store, func().withResult(u32()).build(), args -> new Object[] {2});
    }

    private static ComponentInstance buildFoo(ComponentStore store) {
        return HostInstance.builder(store)
                .addModule("a-module", WasmModule.builder().build())
                .build();
    }

    private static ComponentInstance buildSrc(ComponentStore store) {
        return HostInstance.builder(store).addModule("m", WasmModule.builder().build()).build();
    }

    private static ComponentInstance buildReexport(ComponentStore store) {
        var builder = HostInstance.builder(store);
        for (int i = 0; i < 3; i++) {
            var moduleWat =
                    "(module\n"
                            + "  (global (export \"g\") i32 (i32.const "
                            + (i + 1)
                            + "))\n"
                            + ")";
            builder.addModule("m" + (i + 1), moduleFromWat(moduleWat));
        }
        return builder.build();
    }

    private static ComponentInstance buildProvider(ComponentStore store) {
        var moduleWat =
                "(module\n"
                        + "    (global (export \"g1\") i32 (i32.const 100))\n"
                        + "    (global (export \"g2\") i32 (i32.const 101))\n"
                        + "    (global (export \"g3\") i32 (i32.const 102))\n"
                        + "    (global (export \"g4\") i32 (i32.const 103))\n"
                        + ")";
        return HostInstance.builder(store).addModule("m", moduleFromWat(moduleWat)).build();
    }

    /**
     * The {@code host} instance the resource spec tests import. It holds one host-implemented
     * resource type, {@code resource1}, together with the constructor and methods those tests call,
     * plus a second resource type and a non-resource export used to check that mismatches are
     * caught.
     *
     * <p>{@code resource1} is <em>host</em>-defined, so its destructor is a Java callback rather
     * than a core function. That is what makes {@code drops} and {@code last-drop} observable: when
     * a guest calls {@code resource.drop} on an owned handle, the drop lands here.
     */
    private static ComponentInstance buildHost(ComponentStore store) {
        var builder = HostInstance.builder(store);
        var state = new Resource1State();

        HostResource resource1 = builder.declareResource(state::recordDrop);

        // A second, distinct resource type. Its identity differs from resource1's even though
        // the two declarations are identical, which is what the "mismatched resource types"
        // test exercises.
        HostResource resource2 = builder.declareResource(null);

        ValType ownR1 = resource1.own();
        ValType borrowR1 = resource1.borrow();

        FuncType constructor = func().addParam(param("r", u32())).withResult(ownR1).build();
        FuncType staticAssert =
                func().addParam(param("r", ownR1)).addParam(param("rep", u32())).build();
        FuncType u32Getter = func().withResult(u32()).build();
        FuncType simple =
                func().addParam(param("self", borrowR1)).addParam(param("rep", u32())).build();
        FuncType takeBorrow =
                func().addParam(param("self", borrowR1)).addParam(param("b", borrowR1)).build();
        FuncType takeOwn =
                func().addParam(param("self", borrowR1)).addParam(param("b", ownR1)).build();

        builder.addResource("resource1", resource1);
        builder.addResource("resource2", resource2);
        builder.addResource("resource1-again", resource1);

        // A function, deliberately not a resource, so that an import declaring it as one is
        // rejected with "expected resource found func", and callable, because another test
        // imports it for what it is and checks that it returns three.
        builder.addFunction("return-three", u32Getter, args -> new Object[] {3L});
        builder.addInstance("nested", buildNestedHost(store));
        builder.addModule("simple-module", moduleFromWat(SIMPLE_MODULE_WAT));

        builder.addFunction(
                "[constructor]resource1",
                constructor,
                args -> new Object[] {ResourceValue.owned(resource1.type(), rep(args[0]))});

        // Taking an `own` argument consumes the caller's handle during lifting, so these two
        // leave the guest's table one entry shorter and never see a destructor run, because a drop
        // is what `resource.drop` does, and that is what the counters below track.
        builder.addFunction(
                "[static]resource1.assert", staticAssert, args -> assertRep(args[0], args[1]));
        builder.addFunction("[method]resource1.take-own", takeOwn, args -> EMPTY_RESULT);

        builder.addFunction(
                "[static]resource1.last-drop",
                u32Getter,
                args -> new Object[] {(long) state.lastDrop});
        builder.addFunction(
                "[static]resource1.drops", u32Getter, args -> new Object[] {(long) state.drops});

        builder.addFunction(
                "[method]resource1.simple", simple, args -> assertRep(args[0], args[1]));
        // Borrowing proves itself by the lifting that got us here, since both handles were checked
        // against resource1 and marked lent for the duration of this call.
        builder.addFunction("[method]resource1.take-borrow", takeBorrow, args -> EMPTY_RESULT);

        return builder.build();
    }

    /**
     * An instance nested inside {@code host}, so that a component can import an instance that
     * itself exports one and reach through both to call {@code return-four}.
     */
    private static ComponentInstance buildNestedHost(ComponentStore store) {
        return HostInstance.builder(store)
                .addFunction(
                        "return-four", func().withResult(u32()).build(), args -> new Object[] {4L})
                .build();
    }

    /**
     * A core module {@code host} hands out whole, for components that instantiate it themselves
     * rather than calling into it. The two values are what the importing test asserts.
     */
    private static final String SIMPLE_MODULE_WAT =
            "(module\n"
                    + "  (func (export \"f\") (result i32) (i32.const 101))\n"
                    + "  (global (export \"g\") i32 (i32.const 100))\n"
                    + ")";

    /** Drop bookkeeping for {@code resource1}, observable through its static methods. */
    private static final class Resource1State {

        private int drops;
        private int lastDrop;

        void recordDrop(int rep) {
            drops++;
            lastDrop = rep;
        }
    }

    /** Checks a handle's representation against what the caller says it should be. */
    private static Object[] assertRep(Object handle, Object expected) {
        int actual = ((ResourceValue) handle).rep();
        int want = rep(expected);
        if (actual != want) {
            throw new TrapException("expected resource rep " + want + " but got " + actual);
        }
        return EMPTY_RESULT;
    }

    private static int rep(Object u32) {
        return ((Number) u32).intValue();
    }

    private static FuncType.Builder func() {
        return FuncType.builder();
    }

    private static ValType u32() {
        return ValType.builder().withPrimValType(PrimValType.U32).build();
    }

    private static LabelValType param(String label, ValType valType) {
        return LabelValType.builder().withLabel(label).withValType(valType).build();
    }

    private static WasmModule moduleFromWat(String wat) {
        return Parser.parse(Wat2Wasm.parse(wat));
    }
}
