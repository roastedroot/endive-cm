package run.endive.cm.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.Test;
import run.endive.cm.tools.ComponentValidate;
import run.endive.cm.types.Case;
import run.endive.cm.types.CustomSection;
import run.endive.cm.types.EnumType;
import run.endive.cm.types.FlagsType;
import run.endive.cm.types.LabelValType;
import run.endive.cm.types.ListType;
import run.endive.cm.types.OptionType;
import run.endive.cm.types.PrimValType;
import run.endive.cm.types.RecordType;
import run.endive.cm.types.ResultType;
import run.endive.cm.types.TupleType;
import run.endive.cm.types.Type;
import run.endive.cm.types.TypeSection;
import run.endive.cm.types.ValType;
import run.endive.cm.types.VariantType;
import run.endive.cm.types.WasmComponent;
import run.endive.wasm.types.UnknownCustomSection;

public class WasmToolsDefinedtypesAssertionsTest {

    @Test
    void case0() {
        var bytes = loadBytes("input.0.wasm");
        ComponentValidate.validate(new ByteArrayInputStream(bytes));
        var parser = ComponentParser.builder().build();
        var actual = parser.parse(() -> new ByteArrayInputStream(bytes));
        assertNotNull(actual);

        var expected = expectedCase0();
        assertThat(actual)
                .usingRecursiveComparison()
                .ignoringFieldsMatchingRegexes(".*customSection\\.bytes.*")
                .isEqualTo(expected);
    }

    private static WasmComponent expectedCase0() {
        var typeSection =
                TypeSection.builder()
                        .addType(Type.of(PrimValType.BOOL))
                        .addType(Type.of(PrimValType.U8))
                        .addType(Type.of(PrimValType.S8))
                        .addType(Type.of(PrimValType.U16))
                        .addType(Type.of(PrimValType.S16))
                        .addType(Type.of(PrimValType.U32))
                        .addType(Type.of(PrimValType.S32))
                        .addType(Type.of(PrimValType.U64))
                        .addType(Type.of(PrimValType.S64))
                        .addType(Type.of(PrimValType.F32))
                        .addType(Type.of(PrimValType.F64))
                        .addType(Type.of(PrimValType.F32))
                        .addType(Type.of(PrimValType.F64))
                        .addType(Type.of(PrimValType.CHAR))
                        .addType(Type.of(PrimValType.STRING))
                        .addType(
                                Type.of(
                                        TupleType.builder()
                                                .addElementType(
                                                        ValType.builder()
                                                                .withPrimValType(PrimValType.CHAR)
                                                                .build())
                                                .build()))
                        .addType(
                                Type.of(
                                        RecordType.builder()
                                                .addField(
                                                        LabelValType.builder()
                                                                .withLabel("x")
                                                                .withValType(
                                                                        ValType.builder()
                                                                                .withTypeIdx(15)
                                                                                .build())
                                                                .build())
                                                .build()))
                        .addType(
                                Type.of(
                                        RecordType.builder()
                                                .addField(
                                                        LabelValType.builder()
                                                                .withLabel("x")
                                                                .withValType(
                                                                        ValType.builder()
                                                                                .withTypeIdx(0)
                                                                                .build())
                                                                .build())
                                                .build()))
                        .addType(
                                Type.of(
                                        VariantType.builder()
                                                .addCase(Case.builder().withLabel("x").build())
                                                .build()))
                        .addType(
                                Type.of(
                                        VariantType.builder()
                                                .addCase(
                                                        Case.builder()
                                                                .withLabel("x")
                                                                .withValType(
                                                                        ValType.builder()
                                                                                .withTypeIdx(0)
                                                                                .build())
                                                                .build())
                                                .build()))
                        .addType(
                                Type.of(
                                        VariantType.builder()
                                                .addCase(Case.builder().withLabel("x").build())
                                                .addCase(
                                                        Case.builder()
                                                                .withLabel("y")
                                                                .withValType(
                                                                        ValType.builder()
                                                                                .withPrimValType(
                                                                                        PrimValType
                                                                                                .STRING)
                                                                                .build())
                                                                .build())
                                                .addCase(
                                                        Case.builder()
                                                                .withLabel("z")
                                                                .withValType(
                                                                        ValType.builder()
                                                                                .withPrimValType(
                                                                                        PrimValType
                                                                                                .STRING)
                                                                                .build())
                                                                .build())
                                                .build()))
                        .addType(
                                Type.of(
                                        VariantType.builder()
                                                .addCase(Case.builder().withLabel("x").build())
                                                .addCase(
                                                        Case.builder()
                                                                .withLabel("y")
                                                                .withValType(
                                                                        ValType.builder()
                                                                                .withPrimValType(
                                                                                        PrimValType
                                                                                                .STRING)
                                                                                .build())
                                                                .build())
                                                .addCase(
                                                        Case.builder()
                                                                .withLabel("z")
                                                                .withValType(
                                                                        ValType.builder()
                                                                                .withPrimValType(
                                                                                        PrimValType
                                                                                                .STRING)
                                                                                .build())
                                                                .build())
                                                .build()))
                        .addType(
                                Type.of(
                                        TupleType.builder()
                                                .addElementType(
                                                        ValType.builder()
                                                                .withPrimValType(PrimValType.U8)
                                                                .build())
                                                .build()))
                        .addType(
                                Type.of(
                                        ListType.builder()
                                                .withElementType(
                                                        ValType.builder().withTypeIdx(22).build())
                                                .build()))
                        .addType(
                                Type.of(
                                        ListType.builder()
                                                .withElementType(
                                                        ValType.builder().withTypeIdx(2).build())
                                                .build()))
                        .addType(
                                Type.of(
                                        TupleType.builder()
                                                .addElementType(
                                                        ValType.builder()
                                                                .withPrimValType(PrimValType.U8)
                                                                .build())
                                                .build()))
                        .addType(
                                Type.of(
                                        TupleType.builder()
                                                .addElementType(
                                                        ValType.builder().withTypeIdx(3).build())
                                                .build()))
                        .addType(Type.of(FlagsType.builder().addLabel("x").build()))
                        .addType(Type.of(EnumType.builder().addLabel("x").build()))
                        .addType(
                                Type.of(
                                        TupleType.builder()
                                                .addElementType(
                                                        ValType.builder()
                                                                .withPrimValType(PrimValType.U32)
                                                                .build())
                                                .build()))
                        .addType(
                                Type.of(
                                        OptionType.builder()
                                                .withValType(
                                                        ValType.builder().withTypeIdx(29).build())
                                                .build()))
                        .addType(
                                Type.of(
                                        OptionType.builder()
                                                .withValType(
                                                        ValType.builder().withTypeIdx(5).build())
                                                .build()))
                        .addType(Type.of(ResultType.builder().build()))
                        .addType(
                                Type.of(
                                        ResultType.builder()
                                                .withOk(ValType.builder().withTypeIdx(6).build())
                                                .build()))
                        .addType(
                                Type.of(
                                        ResultType.builder()
                                                .withError(ValType.builder().withTypeIdx(7).build())
                                                .build()))
                        .addType(
                                Type.of(
                                        ResultType.builder()
                                                .withOk(ValType.builder().withTypeIdx(8).build())
                                                .withError(ValType.builder().withTypeIdx(9).build())
                                                .build()))
                        .build();

        return WasmComponent.builder()
                .addCoreCustomSection(componentNameCustomSection())
                .addTypeSection(typeSection)
                .build();
    }

    private static byte[] loadBytes(String resourceName) {
        InputStream is =
                WasmToolsDefinedtypesAssertionsTest.class.getResourceAsStream(
                        "/wasm-tools-definedtypes/" + resourceName);
        assertNotNull(is, "Resource not found: " + resourceName);
        try {
            return is.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static CustomSection componentNameCustomSection() {
        return CustomSection.builder()
                .withCustomSection(
                        UnknownCustomSection.builder()
                                .withName("component-name")
                                .withBytes(new byte[0])
                                .build())
                .build();
    }
}
