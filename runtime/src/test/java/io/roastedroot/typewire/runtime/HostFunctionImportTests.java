package io.roastedroot.typewire.runtime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.roastedroot.typewire.types.FuncType;
import io.roastedroot.typewire.types.LabelValType;
import io.roastedroot.typewire.types.PrimValType;
import io.roastedroot.typewire.types.ValType;
import io.roastedroot.typewire.types.WasmComponent;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A function the embedder supplies for a root import is checked against the type with which it is
 * declared.
 *
 * <p>Nothing else can make this check. A function passed to a nested component was compared against
 * that declaration when the enclosing binary was validated, but one arriving from the host has
 * never been compared against anything.
 *
 * <p>Parameter names are deliberately not part of it, because an embedder-supplied function has
 * none of its own to offer. Arity and types are checked as they are anywhere else.
 */
public class HostFunctionImportTests {

    private static final String IMPORTS_FUNC = "/component-type/host-func-import.wat";

    /** The declared type: {@code (func (param "x" u32) (result u32))}. */
    private static FuncType declared() {
        return funcType(PrimValType.U32, "x", PrimValType.U32);
    }

    @Test
    public void aFunctionMatchingItsDeclaredTypeLinks() {
        assertNotNull(link(declared()));
    }

    /** The names differ but an embedder-supplied function has none to compare, so this links. */
    @Test
    public void aFunctionWhoseParameterNameDiffersLinks() {
        assertNotNull(link(funcType(PrimValType.U32, "other", PrimValType.U32)));
    }

    @Test
    public void aFunctionWithTheWrongResultTypeIsRejected() {
        var thrown =
                assertThrows(
                        LinkageException.class,
                        () -> link(funcType(PrimValType.U64, "x", PrimValType.U32)));
        assertTrue(
                thrown.getMessage().contains("host-func"),
                "expected the import to be named, got: " + thrown.getMessage());
    }

    @Test
    public void aFunctionWithTheWrongParameterTypeIsRejected() {
        assertThrows(
                LinkageException.class,
                () -> link(funcType(PrimValType.U32, "x", PrimValType.STRING)));
    }

    @Test
    public void aFunctionWithTooFewParametersIsRejected() {
        assertThrows(LinkageException.class, () -> link(funcType(PrimValType.U32)));
    }

    @Test
    public void aFunctionWithTooManyParametersIsRejected() {
        assertThrows(
                LinkageException.class,
                () -> link(funcType(PrimValType.U32, "x", PrimValType.U32, "y", PrimValType.U32)));
    }

    /** A function type with {@code result}, then alternating parameter label and type. */
    private static FuncType funcType(PrimValType result, Object... params) {
        var builder = FuncType.builder().withResult(valType(result));
        for (int i = 0; i < params.length; i += 2) {
            builder.addParam(
                    LabelValType.builder()
                            .withLabel((String) params[i])
                            .withValType(valType((PrimValType) params[i + 1]))
                            .build());
        }
        return builder.build();
    }

    private static ValType valType(PrimValType prim) {
        return ValType.builder().withPrimValType(prim).build();
    }

    /** Instantiates the fixture with the host supplying a function of {@code funcType}. */
    private static ComponentInstance link(FuncType funcType) {
        var store = new ComponentStore();
        var host = ComponentInstance.builder(store);
        var function =
                ComponentFunctionInstance.builder()
                        .withInstance(host.instance())
                        .withTypeResolver(host.instance())
                        .withFuncType(funcType)
                        .withCall(args -> new Object[] {0L})
                        .withHostProvided(true)
                        .build();
        host.addExport("host-func", function);
        return ComponentLinker.builder()
                .build()
                .instantiate(store, component(), Map.of("host-func", function));
    }

    private static WasmComponent component() {
        try (InputStream is = HostFunctionImportTests.class.getResourceAsStream(IMPORTS_FUNC)) {
            assertNotNull(is, "Resource not found: " + IMPORTS_FUNC);
            return TestComponents.fromWat(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
