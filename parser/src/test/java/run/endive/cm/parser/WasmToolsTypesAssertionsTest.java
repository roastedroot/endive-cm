package run.endive.cm.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import run.endive.cm.tools.ComponentValidate;
import run.endive.cm.types.ComponentDecl;
import run.endive.cm.types.ComponentSection;
import run.endive.cm.types.ComponentType;
import run.endive.cm.types.CoreAlias;
import run.endive.cm.types.CoreImportDecl;
import run.endive.cm.types.CoreSort;
import run.endive.cm.types.CoreType;
import run.endive.cm.types.CoreTypeSection;
import run.endive.cm.types.CustomSection;
import run.endive.cm.types.ExportDecl;
import run.endive.cm.types.ExternDesc;
import run.endive.cm.types.FuncType;
import run.endive.cm.types.ImportDecl;
import run.endive.cm.types.InstanceDecl;
import run.endive.cm.types.InstanceType;
import run.endive.cm.types.ListType;
import run.endive.cm.types.ModuleDecl;
import run.endive.cm.types.ModuleType;
import run.endive.cm.types.PrimValType;
import run.endive.cm.types.TupleType;
import run.endive.cm.types.Type;
import run.endive.cm.types.TypeBound;
import run.endive.cm.types.TypeSection;
import run.endive.cm.types.ValType;
import run.endive.cm.types.WasmComponent;
import run.endive.wasm.types.FunctionImport;
import run.endive.wasm.types.FunctionType;
import run.endive.wasm.types.UnknownCustomSection;

public class WasmToolsTypesAssertionsTest {

    private static final Map<Integer, WasmComponent> EXPECTED_COMPONENTS =
            Map.of(
                    17, case17(),
                    29, case29(),
                    30, case30(),
                    31, case31(),
                    32, case32(),
                    33, case33(),
                    34, case34(),
                    40, case40());

    static java.util.stream.Stream<Arguments> cases() {
        return EXPECTED_COMPONENTS.entrySet().stream()
                .map(e -> Arguments.arguments(e.getKey(), e.getValue()));
    }

    @ParameterizedTest(name = "case {0}")
    @MethodSource("cases")
    void structuralAssertion(int caseIndex, WasmComponent expected) {
        var bytes = loadBytes("input." + caseIndex + ".wasm");
        ComponentValidate.validate(new ByteArrayInputStream(bytes));
        var parser = ComponentParser.builder().build();
        var actual = parser.parse(() -> new ByteArrayInputStream(bytes));
        assertNotNull(actual);

        assertThat(actual)
                .usingRecursiveComparison()
                .ignoringFieldsMatchingRegexes(".*customSection\\.bytes.*")
                .isEqualTo(expected);
    }

    private static WasmComponent case17() {
        var funcType = FunctionType.of(new ArrayList<>(), new ArrayList<>());

        var alias = CoreAlias.builder().withSort(CoreSort.TYPE).withCount(1).withIndex(0).build();

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
                        .addElementType(ValType.builder().withPrimValType(PrimValType.U32).build())
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
        var alias = CoreAlias.builder().withSort(CoreSort.TYPE).withCount(1).withIndex(0).build();

        var moduleDecl1 = ModuleDecl.builder().withAlias(alias).build();

        var coreImport = new FunctionImport("", "", 0);
        var importDecl = CoreImportDecl.builder().withCoreImport(coreImport).build();
        var moduleDecl2 = ModuleDecl.builder().withImportDecl(importDecl).build();

        var moduleType =
                ModuleType.builder().addModuleDecl(moduleDecl1).addModuleDecl(moduleDecl2).build();

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
        var alias = CoreAlias.builder().withSort(CoreSort.TYPE).withCount(1).withIndex(0).build();

        var moduleDecl1 = ModuleDecl.builder().withAlias(alias).build();

        var coreImport = new FunctionImport("", "", 0);
        var importDecl = CoreImportDecl.builder().withCoreImport(coreImport).build();
        var moduleDecl2 = ModuleDecl.builder().withImportDecl(importDecl).build();

        var moduleType =
                ModuleType.builder().addModuleDecl(moduleDecl1).addModuleDecl(moduleDecl2).build();

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
        var outerCoreTypeSection = CoreTypeSection.builder().addFunctionType(coreFuncType).build();
        var alias = CoreAlias.builder().withSort(CoreSort.TYPE).withCount(2).withIndex(0).build();

        var moduleDecl1 = ModuleDecl.builder().withAlias(alias).build();

        var coreImport = new FunctionImport("", "", 0);
        var importDecl = CoreImportDecl.builder().withCoreImport(coreImport).build();
        var moduleDecl2 = ModuleDecl.builder().withImportDecl(importDecl).build();

        var moduleType =
                ModuleType.builder().addModuleDecl(moduleDecl1).addModuleDecl(moduleDecl2).build();

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
        var outerCoreTypeSection = CoreTypeSection.builder().addFunctionType(coreFuncType).build();

        var alias = CoreAlias.builder().withSort(CoreSort.TYPE).withCount(2).withIndex(0).build();

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

    private static byte[] loadBytes(String resourceName) {
        InputStream is =
                WasmToolsTypesAssertionsTest.class.getResourceAsStream(
                        "/wasm-tools-types/" + resourceName);
        assertNotNull(is, "Resource not found: " + resourceName);
        try {
            return is.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static CustomSection componentNameCustomSection() {
        return CustomSection.builder()
                .withCustomSection(
                        UnknownCustomSection.builder()
                                .withName("component-name")
                                .withBytes(new byte[0])
                                .build())
                .build();
    }
}
