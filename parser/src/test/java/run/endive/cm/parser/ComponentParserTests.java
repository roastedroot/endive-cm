package run.endive.cm.parser;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import run.endive.cm.types.Case;
import run.endive.cm.types.ComponentDecl;
import run.endive.cm.types.ComponentSection;
import run.endive.cm.types.ComponentType;
import run.endive.cm.types.CoreAlias;
import run.endive.cm.types.CoreImportDecl;
import run.endive.cm.types.CoreSort;
import run.endive.cm.types.CoreType;
import run.endive.cm.types.CoreTypeSection;
import run.endive.cm.types.CustomSection;
import run.endive.cm.types.EnumType;
import run.endive.cm.types.ExportDecl;
import run.endive.cm.types.ExternDesc;
import run.endive.cm.types.FlagsType;
import run.endive.cm.types.FuncType;
import run.endive.cm.types.ImportDecl;
import run.endive.cm.types.InstanceDecl;
import run.endive.cm.types.InstanceType;
import run.endive.cm.types.LabelValType;
import run.endive.cm.types.ListType;
import run.endive.cm.types.ModuleDecl;
import run.endive.cm.types.ModuleType;
import run.endive.cm.types.OptionType;
import run.endive.cm.types.PrimValType;
import run.endive.cm.types.RecordType;
import run.endive.cm.types.ResultType;
import run.endive.cm.types.TupleType;
import run.endive.cm.types.Type;
import run.endive.cm.types.TypeBound;
import run.endive.cm.types.TypeSection;
import run.endive.cm.types.ValType;
import run.endive.cm.types.VariantType;
import run.endive.cm.types.WasmComponent;
import run.endive.wasm.types.FunctionImport;
import run.endive.wasm.types.FunctionType;
import run.endive.wasm.types.UnknownCustomSection;

public class ComponentParserTests {

    private static final class TestConfiguration {
        private final String testLocation;
        private final Map<Integer, WasmComponent> expectedComponents;
        private final int[] skipCases;

        private TestConfiguration(
                String testLocation,
                Map<Integer, WasmComponent> expectedComponents,
                int[] skipCases) {
            this.testLocation = testLocation;
            this.expectedComponents = expectedComponents;
            this.skipCases = skipCases;
        }

        @Override
        public String toString() {
            return "TestConfiguration{"
                    + "testLocation='"
                    + testLocation
                    + '\''
                    + ", expectedComponents="
                    + expectedComponents.keySet()
                    + ", skipCases="
                    + Arrays.toString(skipCases)
                    + '}';
        }
    }

    static java.util.stream.Stream<Arguments> testLocations() {
        var wasmToolsDefinedTypesWast =
                new TestConfiguration(
                        "wasm-tools/definedtypes.wast",
                        WasmToolsDefinedTypesAssertions.expectedComponents,
                        new int[0]);
        var wasmToolsTypesWast =
                new TestConfiguration(
                        "wasm-tools/types.wast",
                        WasmToolsTypesAssertions.expectedComponents,
                        new int[0]);
        return java.util.stream.Stream.of(
                Arguments.arguments(wasmToolsDefinedTypesWast),
                Arguments.arguments(wasmToolsTypesWast));
    }

    @ParameterizedTest
    @MethodSource("testLocations")
    void allSpecTests(TestConfiguration config) throws IOException {
        var testDir = Path.of("src/test/resources/spec-tests");
        var testFile = testDir.resolve(config.testLocation);
        try (var wast = Files.newInputStream(testFile);
                var wastTests = JsonFromWast.exec(wast)) {
            wastTests.runTests(config.expectedComponents, true, config.skipCases);
        } catch (Throwable e) {
            if (e instanceof WastTests.CommandException) {
                var ce = (WastTests.CommandException) e;
                var outFile = testDir.resolve("./failed.wasm");
                Files.write(outFile, ce.testContents());
            }
            fail(e);
        }
    }

    private static final class WasmToolsDefinedTypesAssertions {
        private static final Map<Integer, WasmComponent> expectedComponents = Map.of(0, case0());

        private static WasmComponent case0() {
            var typeSection =
                    TypeSection.builder()
                            // $A1 bool (0)
                            .addType(Type.of(PrimValType.BOOL))
                            // $A2 u8 (1)
                            .addType(Type.of(PrimValType.U8))
                            // $A3 s8 (2)
                            .addType(Type.of(PrimValType.S8))
                            // $A4 u16 (3)
                            .addType(Type.of(PrimValType.U16))
                            // $A5 s16 (4)
                            .addType(Type.of(PrimValType.S16))
                            // $A6 u32 (5)
                            .addType(Type.of(PrimValType.U32))
                            // $A7 s32 (6)
                            .addType(Type.of(PrimValType.S32))
                            // $A8 u64 (7)
                            .addType(Type.of(PrimValType.U64))
                            // $A9 s64 (8)
                            .addType(Type.of(PrimValType.S64))
                            // $A10a f32 (9)
                            .addType(Type.of(PrimValType.F32))
                            // $A11a f64 (10)
                            .addType(Type.of(PrimValType.F64))
                            // $A10b float32 (11)
                            .addType(Type.of(PrimValType.F32))
                            // $A11b float64 (12)
                            .addType(Type.of(PrimValType.F64))
                            // $A12 char (13)
                            .addType(Type.of(PrimValType.CHAR))
                            // $A13 string (14)
                            .addType(Type.of(PrimValType.STRING))
                            // $A14b (record (field "x" (tuple char))) (15, 16)
                            .addType(
                                    Type.of(
                                            TupleType.builder()
                                                    .addElementType(
                                                            ValType.builder()
                                                                    .withPrimValType(
                                                                            PrimValType.CHAR)
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
                            // $A14c (record (field "x" $A1)) (17)
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
                            // $A15a (variant (case "x")) (18)
                            .addType(
                                    Type.of(
                                            VariantType.builder()
                                                    .addCase(Case.builder().withLabel("x").build())
                                                    .build()))
                            // $A15b (variant (case "x" $A1)) (19)
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
                            // $A15c (variant (case "x") (case "y" string) (case "z" string)) (20)
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
                            // $A15d (variant (case "x") (case "y" string) (case "z" string)) (21)
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
                            // $A16a (list (tuple u8)) (22, 23)
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
                                                            ValType.builder()
                                                                    .withTypeIdx(22)
                                                                    .build())
                                                    .build()))
                            // $A16b (list $A3) (24)
                            .addType(
                                    Type.of(
                                            ListType.builder()
                                                    .withElementType(
                                                            ValType.builder()
                                                                    .withTypeIdx(2)
                                                                    .build())
                                                    .build()))
                            // $A17a (tuple u8) (25)
                            .addType(
                                    Type.of(
                                            TupleType.builder()
                                                    .addElementType(
                                                            ValType.builder()
                                                                    .withPrimValType(PrimValType.U8)
                                                                    .build())
                                                    .build()))
                            // $A17b (tuple $A4) (26)
                            .addType(
                                    Type.of(
                                            TupleType.builder()
                                                    .addElementType(
                                                            ValType.builder()
                                                                    .withTypeIdx(3)
                                                                    .build())
                                                    .build()))
                            // $A18b (flags "x") (27)
                            .addType(Type.of(FlagsType.builder().addLabel("x").build()))
                            // $A19b (enum "x") (28)
                            .addType(Type.of(EnumType.builder().addLabel("x").build()))
                            // $A21a (option (tuple u32)) (29, 30)
                            .addType(
                                    Type.of(
                                            TupleType.builder()
                                                    .addElementType(
                                                            ValType.builder()
                                                                    .withPrimValType(
                                                                            PrimValType.U32)
                                                                    .build())
                                                    .build()))
                            .addType(
                                    Type.of(
                                            OptionType.builder()
                                                    .withValType(
                                                            ValType.builder()
                                                                    .withTypeIdx(29)
                                                                    .build())
                                                    .build()))
                            // $A21b (option $A6) (31)
                            .addType(
                                    Type.of(
                                            OptionType.builder()
                                                    .withValType(
                                                            ValType.builder()
                                                                    .withTypeIdx(5)
                                                                    .build())
                                                    .build()))
                            // $A22a (result) (32)
                            .addType(Type.of(ResultType.builder().build()))
                            // $A22b (result $A7) (33)
                            .addType(
                                    Type.of(
                                            ResultType.builder()
                                                    .withOk(
                                                            ValType.builder()
                                                                    .withTypeIdx(6)
                                                                    .build())
                                                    .build()))
                            // $A22c (result (error $A8)) (34)
                            .addType(
                                    Type.of(
                                            ResultType.builder()
                                                    .withError(
                                                            ValType.builder()
                                                                    .withTypeIdx(7)
                                                                    .build())
                                                    .build()))
                            // $A22d (result $A9 (error $A10a)) (35)
                            .addType(
                                    Type.of(
                                            ResultType.builder()
                                                    .withOk(
                                                            ValType.builder()
                                                                    .withTypeIdx(8)
                                                                    .build())
                                                    .withError(
                                                            ValType.builder()
                                                                    .withTypeIdx(9)
                                                                    .build())
                                                    .build()))
                            .build();

            return WasmComponent.builder()
                    .addCoreCustomSection(componentNameCustomSection())
                    .addTypeSection(typeSection)
                    .build();
        }
    }

    private static final class WasmToolsTypesAssertions {

        private static final Map<Integer, WasmComponent> expectedComponents =
                Map.of(
                        17, case17(),
                        29, case29(),
                        30, case30(),
                        31, case31(),
                        32, case32(),
                        33, case33(),
                        34, case34(),
                        40, case40());

        private static WasmComponent case17() {
            var funcType = FunctionType.of(new ArrayList<>(), new ArrayList<>());

            var alias =
                    CoreAlias.builder().withSort(CoreSort.TYPE).withCount(1).withIndex(0).build();

            var moduleDecl = ModuleDecl.builder().withAlias(alias).build();
            var moduleType = ModuleType.builder().addModuleDecl(moduleDecl).build();
            var coreModuleType = CoreType.of(moduleType);

            var coreTypeSection =
                    CoreTypeSection.builder()
                            .addFunctionType(funcType)
                            .addCoreType(coreModuleType)
                            .build();
            return WasmComponent.builder()
                    .addCoreCustomSection(componentNameCustomSection())
                    .addCoreTypeSection(coreTypeSection)
                    .build();
        }

        private static WasmComponent case29() {
            var listElementType = ValType.builder().withPrimValType(PrimValType.U8).build();
            var listType = ListType.builder().withElementType(listElementType).build();
            var tupleType =
                    TupleType.builder()
                            .addElementType(ValType.builder().withTypeIdx(0).build())
                            .addElementType(
                                    ValType.builder().withPrimValType(PrimValType.U32).build())
                            .build();

            var funcType =
                    FuncType.builder().withResult(ValType.builder().withTypeIdx(1).build()).build();

            var typeSection =
                    TypeSection.builder()
                            .addType(Type.of(listType))
                            .addType(Type.of(tupleType))
                            .addType(Type.of(funcType))
                            .build();
            return WasmComponent.builder()
                    .addCoreCustomSection(componentNameCustomSection())
                    .addTypeSection(typeSection)
                    .build();
        }

        private static WasmComponent case30() {
            var coreFuncType = FunctionType.of(new ArrayList<>(), new ArrayList<>());
            var alias =
                    CoreAlias.builder().withSort(CoreSort.TYPE).withCount(1).withIndex(0).build();

            var moduleDecl1 = ModuleDecl.builder().withAlias(alias).build();

            var coreImport = new FunctionImport("", "", 0);
            var importDecl = CoreImportDecl.builder().withCoreImport(coreImport).build();
            var moduleDecl2 = ModuleDecl.builder().withImportDecl(importDecl).build();

            var moduleType =
                    ModuleType.builder()
                            .addModuleDecl(moduleDecl1)
                            .addModuleDecl(moduleDecl2)
                            .build();

            var coreTypeSection =
                    CoreTypeSection.builder()
                            .addFunctionType(coreFuncType)
                            .addCoreType(CoreType.of(moduleType))
                            .build();
            return WasmComponent.builder()
                    .addCoreCustomSection(componentNameCustomSection())
                    .addCoreTypeSection(coreTypeSection)
                    .build();
        }

        private static WasmComponent case31() {
            var coreFuncType = FunctionType.of(new ArrayList<>(), new ArrayList<>());
            var alias =
                    CoreAlias.builder().withSort(CoreSort.TYPE).withCount(1).withIndex(0).build();

            var moduleDecl1 = ModuleDecl.builder().withAlias(alias).build();

            var coreImport = new FunctionImport("", "", 0);
            var importDecl = CoreImportDecl.builder().withCoreImport(coreImport).build();
            var moduleDecl2 = ModuleDecl.builder().withImportDecl(importDecl).build();

            var moduleType =
                    ModuleType.builder()
                            .addModuleDecl(moduleDecl1)
                            .addModuleDecl(moduleDecl2)
                            .build();

            var coreTypeSection =
                    CoreTypeSection.builder()
                            .addFunctionType(coreFuncType)
                            .addCoreType(CoreType.of(moduleType))
                            .build();

            var innerComponent =
                    WasmComponent.builder()
                            .addCoreCustomSection(componentNameCustomSection())
                            .addCoreTypeSection(coreTypeSection)
                            .build();

            var componentSection = ComponentSection.builder().withComponent(innerComponent).build();
            return WasmComponent.builder()
                    .addCoreCustomSection(componentNameCustomSection())
                    .addComponentSection(componentSection)
                    .build();
        }

        private static WasmComponent case32() {
            var coreFuncType = FunctionType.of(new ArrayList<>(), new ArrayList<>());
            var outerCoreTypeSection =
                    CoreTypeSection.builder().addFunctionType(coreFuncType).build();
            var alias =
                    CoreAlias.builder().withSort(CoreSort.TYPE).withCount(2).withIndex(0).build();

            var moduleDecl1 = ModuleDecl.builder().withAlias(alias).build();

            var coreImport = new FunctionImport("", "", 0);
            var importDecl = CoreImportDecl.builder().withCoreImport(coreImport).build();
            var moduleDecl2 = ModuleDecl.builder().withImportDecl(importDecl).build();

            var moduleType =
                    ModuleType.builder()
                            .addModuleDecl(moduleDecl1)
                            .addModuleDecl(moduleDecl2)
                            .build();

            var innerCoreTypeSection =
                    CoreTypeSection.builder().addCoreType(CoreType.of(moduleType)).build();

            var innerComponent =
                    WasmComponent.builder()
                            .addCoreCustomSection(componentNameCustomSection())
                            .addCoreTypeSection(innerCoreTypeSection)
                            .build();

            var componentSection = ComponentSection.builder().withComponent(innerComponent).build();
            return WasmComponent.builder()
                    .addCoreCustomSection(componentNameCustomSection())
                    .addCoreTypeSection(outerCoreTypeSection)
                    .addComponentSection(componentSection)
                    .build();
        }

        private static WasmComponent case33() {

            var instanceDecl1 = InstanceDecl.of(Type.of(PrimValType.STRING));

            var exportDecl =
                    ExportDecl.builder()
                            .withName("a")
                            .withExternDesc(
                                    ExternDesc.builder()
                                            .withKind(ExternDesc.Kind.TYPE)
                                            .withTypeBound(
                                                    TypeBound.builder()
                                                            .withKind(TypeBound.Kind.EQ)
                                                            .withTypeIdx(0)
                                                            .build())
                                            .build())
                            .build();

            var instanceType =
                    InstanceType.builder()
                            .addInstanceDecl(instanceDecl1)
                            .addInstanceDecl(InstanceDecl.of(exportDecl))
                            .build();

            var typeSection = TypeSection.builder().addType(Type.of(instanceType)).build();

            return WasmComponent.builder().addTypeSection(typeSection).build();
        }

        private static WasmComponent case34() {
            var instanceDecl = InstanceDecl.of(Type.of(PrimValType.STRING));

            var importDecl =
                    ImportDecl.builder()
                            .withName("a")
                            .withExternDesc(
                                    ExternDesc.builder()
                                            .withKind(ExternDesc.Kind.TYPE)
                                            .withTypeBound(
                                                    TypeBound.builder()
                                                            .withKind(TypeBound.Kind.EQ)
                                                            .withTypeIdx(0)
                                                            .build())
                                            .build())
                            .build();

            var exportDecl =
                    ExportDecl.builder()
                            .withName("b")
                            .withExternDesc(
                                    ExternDesc.builder()
                                            .withKind(ExternDesc.Kind.TYPE)
                                            .withTypeBound(
                                                    TypeBound.builder()
                                                            .withKind(TypeBound.Kind.EQ)
                                                            .withTypeIdx(0)
                                                            .build())
                                            .build())
                            .build();

            var componentType =
                    ComponentType.builder()
                            .addComponentDecl(ComponentDecl.of(instanceDecl))
                            .addComponentDecl(ComponentDecl.of(importDecl))
                            .addComponentDecl(ComponentDecl.of(InstanceDecl.of(exportDecl)))
                            .build();

            var typeSection = TypeSection.builder().addType(Type.of(componentType)).build();

            return WasmComponent.builder().addTypeSection(typeSection).build();
        }

        private static WasmComponent case40() {
            var coreFuncType = FunctionType.of(new ArrayList<>(), new ArrayList<>());
            var outerCoreTypeSection =
                    CoreTypeSection.builder().addFunctionType(coreFuncType).build();

            var alias =
                    CoreAlias.builder().withSort(CoreSort.TYPE).withCount(2).withIndex(0).build();

            var moduleDecl = ModuleDecl.builder().withAlias(alias).build();
            var moduleType = ModuleType.builder().addModuleDecl(moduleDecl).build();

            var innerCoreTypeSection =
                    CoreTypeSection.builder().addCoreType(CoreType.of(moduleType)).build();

            var innerComponent =
                    WasmComponent.builder()
                            .addCoreCustomSection(componentNameCustomSection())
                            .addCoreTypeSection(innerCoreTypeSection)
                            .build();

            var componentSection = ComponentSection.builder().withComponent(innerComponent).build();

            return WasmComponent.builder()
                    .addCoreCustomSection(componentNameCustomSection())
                    .addCoreTypeSection(outerCoreTypeSection)
                    .addComponentSection(componentSection)
                    .build();
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
