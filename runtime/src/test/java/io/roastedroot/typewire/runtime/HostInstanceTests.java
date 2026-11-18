package io.roastedroot.typewire.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.roastedroot.typewire.abi.ByteList;
import io.roastedroot.typewire.abi.ResourceValue;
import io.roastedroot.typewire.types.FuncType;
import io.roastedroot.typewire.types.LabelValType;
import io.roastedroot.typewire.types.ListType;
import io.roastedroot.typewire.types.OwnType;
import io.roastedroot.typewire.types.PrimValType;
import io.roastedroot.typewire.types.RecordType;
import io.roastedroot.typewire.types.Type;
import io.roastedroot.typewire.types.ValType;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import run.endive.tools.wasm.Wat2Wasm;
import run.endive.wasm.WasmEngineException;
import run.endive.wasm.WasmModule;

/**
 * The facade an embedder builds host-provided imports through, which is also what generated
 * bindings target. It exists so that supplying an import needs no access to the index-space
 * mechanics behind {@link ComponentInstance.Builder}.
 */
public class HostInstanceTests {

    /**
     * The shape a world's top-level function import takes, and the first thing bindgen has to
     * produce. A bare function belongs to no instance the importer can name.
     */
    @Test
    public void aBareHostFunctionCarriesItsResultBack() {
        ComponentFunction name =
                HostFunction.of(
                        new ComponentStore(),
                        func().withResult(string()).build(),
                        args -> new Object[] {"world"});

        assertArrayEquals(new Object[] {"world"}, name.apply());
        assertTrue(name.hostProvided());
    }

    /** A host function satisfies a root import declared with the same type. */
    @Test
    public void aBareHostFunctionSatisfiesARootImport() {
        var store = new ComponentStore();
        ComponentFunction hostFunc =
                HostFunction.of(
                        store,
                        func().addParam(param("x", u32())).withResult(u32()).build(),
                        args -> new Object[] {0L});

        var component =
                TestComponents.parse(
                        Wat2Wasm.parse(
                                "(component\n"
                                        + "  (import \"host-func\" (func (param \"x\" u32) (result"
                                        + " u32)))\n"
                                        + ")"));

        assertNotNull(
                ComponentLinker.builder()
                        .build()
                        .instantiate(store, component, Map.of("host-func", hostFunc)));
    }

    /**
     * A function type may name a type this instance declares, which is why a host instance carries
     * an index space at all. The index has to resolve, or the function cannot be matched or called.
     */
    @Test
    public void aDeclaredTypeResolvesFromAFunctionThatNamesIt() {
        var builder = HostInstance.builder(new ComponentStore());
        ValType point =
                builder.declareType(
                        Type.of(
                                RecordType.builder()
                                        .addField(param("x", u32()))
                                        .addField(param("y", u32()))
                                        .build()));

        builder.addFunction(
                "origin-distance",
                func().addParam(param("p", point)).withResult(u32()).build(),
                args -> new Object[] {0L});

        ComponentInstance host = builder.build();
        ComponentFunction distance = host.export("origin-distance");

        assertNotNull(distance.resolvedFuncType());
        assertArrayEquals(new Object[] {0L}, distance.apply(Map.of("x", 3L, "y", 4L)));
    }

    /** Types land at consecutive indices, so a later one can name an earlier one. */
    @Test
    public void declaredTypesAreNumberedInOrder() {
        var builder = HostInstance.builder(new ComponentStore());

        assertEquals(0, builder.declareType(Type.of(RecordType.builder().build())).typeIdx());
        assertEquals(1, builder.declareType(Type.of(RecordType.builder().build())).typeIdx());
    }

    /** A declared type may be exported, so that an importer can alias it by name. */
    @Test
    public void aDeclaredTypeMayBeExported() {
        var builder = HostInstance.builder(new ComponentStore());
        Type point = Type.of(RecordType.builder().addField(param("x", u32())).build());

        ComponentInstance host = builder.addType("point", builder.declareType(point)).build();

        assertSame(point, ((ComponentInstance.TypeSlot) host.getExport("point")).type());
    }

    /** A primitive takes no slot in the index space, so there is nothing to export. */
    @Test
    public void aPrimitiveCannotBeExportedAsAType() {
        var builder = HostInstance.builder(new ComponentStore());

        assertThrows(IllegalArgumentException.class, () -> builder.addType("count", u32()));
    }

    /**
     * A named primitive such as {@code type instant = u64} is declared into the index space, so it
     * may be exported and a function naming it accepts the primitive's Java type.
     */
    @Test
    public void aNamedPrimitiveMayBeExportedAndNamedByAFunction() {
        var builder = HostInstance.builder(new ComponentStore());
        ValType instant = builder.declareType(Type.of(PrimValType.U64));
        builder.addType("instant", instant);
        builder.addFunction(
                "later",
                func().addParam(param("at", instant)).withResult(instant).build(),
                args -> new Object[] {((BigInteger) args[0]).add(BigInteger.ONE)});

        ComponentInstance host = builder.build();

        assertArrayEquals(
                new Object[] {BigInteger.TEN}, host.export("later").apply(BigInteger.valueOf(9)));
    }

    /** A {@code byte[]} crosses as a {@code list<u8>}, whether the element is inline or named. */
    @Test
    public void aByteArrayCrossesAsAListOfU8() {
        var builder = HostInstance.builder(new ComponentStore());
        ValType octet = builder.declareType(Type.of(PrimValType.U8));
        ValType inline = builder.declareType(Type.of(listOf(u8())));
        ValType named = builder.declareType(Type.of(listOf(octet)));
        builder.addFunction(
                "inline-length",
                func().addParam(param("bytes", inline)).withResult(u32()).build(),
                args -> new Object[] {(long) ByteList.toBytes(args[0]).length});
        builder.addFunction(
                "named-length",
                func().addParam(param("bytes", named)).withResult(u32()).build(),
                args -> new Object[] {(long) ByteList.toBytes(args[0]).length});

        ComponentInstance host = builder.build();

        assertArrayEquals(new Object[] {3L}, host.export("inline-length").apply(new byte[3]));
        assertArrayEquals(new Object[] {2L}, host.export("named-length").apply(new byte[2]));
    }

    /** A {@code byte[]} names its element, so it is refused for a list of anything but bytes. */
    @Test
    public void aByteArrayIsRefusedForAListOfAnythingElse() {
        var builder = HostInstance.builder(new ComponentStore());
        ValType strings = builder.declareType(Type.of(listOf(string())));
        builder.addFunction(
                "count",
                func().addParam(param("items", strings)).withResult(u32()).build(),
                args -> new Object[] {0L});

        ComponentFunction count = builder.build().export("count");

        assertThrows(WasmEngineException.class, () -> count.apply(new byte[1]));
        assertThrows(
                LinkageException.class,
                () ->
                        count.typed(
                                PrimitiveHostTypeDescriptor.forClass(Long.class),
                                ListHostTypeDescriptor.bytes()));
    }

    /**
     * Resource types are generative, so two declarations that read alike are still two types. The
     * spec suite leans on this to check that a handle minted from one is refused by the other.
     */
    @Test
    public void twoDeclaredResourcesAreDistinctTypes() {
        var builder = HostInstance.builder(new ComponentStore());

        HostResource first = builder.declareResource(null);
        HostResource second = builder.declareResource(null);

        assertNotSame(first.type(), second.type());
        assertNotSame(first.own(), second.own());
    }

    /**
     * A resource one instance uses from another stays the same type, which is what lets an
     * importer bind it to the declaring instance's resource with an {@code eq} bound.
     */
    @Test
    public void aResourceUsedFromAnotherInstanceIsTheSameType() {
        var store = new ComponentStore();
        var declaring = HostInstance.builder(store);
        HostResource declared = declaring.declareResource(null);
        var using = HostInstance.builder(store);

        HostResource used = using.useResource(declared);
        ComponentInstance host = using.addResource("conn", used).build();

        int owned = ((OwnType) host.slotAt(used.own().typeIdx()).type().defValType()).typeIdx();

        assertSame(declared.type(), used.type());
        assertSame(declared.type(), host.getExport("conn"));
        assertSame(declared.type(), host.resourceType(owned));
    }

    /** A resource may be exported under more than one name, naming one type both times. */
    @Test
    public void oneResourceExportedTwiceIsOneType() {
        var builder = HostInstance.builder(new ComponentStore());
        HostResource resource = builder.declareResource(null);

        ComponentInstance host =
                builder.addResource("conn", resource).addResource("conn-again", resource).build();

        assertSame(host.getExport("conn"), host.getExport("conn-again"));
    }

    /** The destructor runs when the host itself is handed a dropped representation. */
    @Test
    public void aResourceDestructorReceivesTheRepresentation() {
        var dropped = new int[] {-1};
        var builder = HostInstance.builder(new ComponentStore());
        HostResource resource = builder.declareResource(rep -> dropped[0] = rep);

        builder.addFunction(
                "make",
                func().addParam(param("rep", u32())).withResult(resource.own()).build(),
                args -> new Object[] {ResourceValue.owned(resource.type(), 7)});
        ComponentInstance host = builder.build();

        Object[] result = host.export("make").apply(3L);
        assertEquals(7, ((ResourceValue) result[0]).rep());
        assertEquals(-1, dropped[0]);
    }

    /** An instance nested inside another, through which an importer reaches. */
    @Test
    public void anInstanceMayExportAnotherInstance() {
        var store = new ComponentStore();
        ComponentInstance inner =
                HostInstance.builder(store)
                        .addFunction(
                                "deep", func().withResult(u32()).build(), args -> new Object[] {4L})
                        .build();

        ComponentInstance outer = HostInstance.builder(store).addInstance("nested", inner).build();

        assertSame(inner, outer.getExport("nested"));
    }

    /** A core module the importer instantiates itself rather than calls. */
    @Test
    public void anInstanceMayExportACoreModule() {
        WasmModule module = WasmModule.builder().build();

        ComponentInstance host =
                HostInstance.builder(new ComponentStore()).addModule("m", module).build();

        assertSame(module, host.getExport("m"));
    }

    /** Exports are only readable once the instance is sealed, and sealing happens once. */
    @Test
    public void anInstanceCannotBeBuiltTwice() {
        var builder = HostInstance.builder(new ComponentStore());
        builder.build();

        assertThrows(IllegalStateException.class, builder::build);
    }

    /** Only instances sharing a store may be wired together. */
    @Test
    public void anInstanceBelongsToTheStoreItWasBuiltIn() {
        var store = new ComponentStore();

        ComponentInstance host = HostInstance.builder(store).build();

        assertSame(store, host.store());
        assertEquals(List.of(host), store.instances());
    }

    private static FuncType.Builder func() {
        return FuncType.builder();
    }

    private static ListType listOf(ValType element) {
        return ListType.builder().withElementType(element).build();
    }

    private static ValType u8() {
        return ValType.builder().withPrimValType(PrimValType.U8).build();
    }

    private static ValType u32() {
        return ValType.builder().withPrimValType(PrimValType.U32).build();
    }

    private static ValType string() {
        return ValType.builder().withPrimValType(PrimValType.STRING).build();
    }

    private static LabelValType param(String label, ValType valType) {
        return LabelValType.builder().withLabel(label).withValType(valType).build();
    }
}
