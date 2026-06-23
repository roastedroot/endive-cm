package run.endive.cm.parser;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import run.endive.cm.types.CoreAlias;
import run.endive.cm.types.CoreOuterAliasTarget;
import run.endive.cm.types.CoreSort;
import run.endive.cm.types.CoreType;
import run.endive.cm.types.CoreTypeSection;
import run.endive.cm.types.ModuleDecl;
import run.endive.cm.types.ModuleType;
import run.endive.cm.types.WasmComponent;
import run.endive.wasm.types.CompType;
import run.endive.wasm.types.FunctionType;
import run.endive.wasm.types.RecType;
import run.endive.wasm.types.SubType;

public class ComponentParserTests {

    static java.util.stream.Stream<Arguments> testLocations() {

        return java.util.stream.Stream.of(
                Arguments.arguments(
                        "./wasm-tools/types.wast",
                        WasmToolsTypesAssertions.expectedComponents,
                        new int[] {29, 30, 31, 32, 33, 34, 40}));
    }

    @ParameterizedTest
    @MethodSource("testLocations")
    void allSpecTests(
            String testLocation, Map<Integer, WasmComponent> expectedComponents, int[] skipCases)
            throws IOException {
        var testDir = Path.of("src/test/resources/spec-tests");
        var testFile = testDir.resolve(testLocation);
        try (var wast = Files.newInputStream(testFile);
                var wastTests = JsonFromWast.exec(wast)) {
            wastTests.runTests(expectedComponents, true, skipCases);
        } catch (Throwable e) {
            if (e instanceof WastTests.CommandException) {
                var ce = (WastTests.CommandException) e;
                var outFile = testDir.resolve("./failed.wasm");
                Files.write(outFile, ce.testContents());
            }
            fail(e);
        }
    }

    private static final class WasmToolsTypesAssertions {

        private static final Map<Integer, WasmComponent> expectedComponents = Map.of(17, case17());

        private static WasmComponent case17() {
            var compType =
                    CompType.builder()
                            .withFuncType(FunctionType.of(new ArrayList<>(), new ArrayList<>()))
                            .build();
            var subType =
                    SubType.builder()
                            .withTypeIdx(new int[] {})
                            .withFinal(true)
                            .withCompType(compType)
                            .build();
            var recType = RecType.builder().withSubTypes(new SubType[] {subType}).build();
            var coreFuncType = CoreType.builder().withRecType(recType).build();

            var alias =
                    CoreAlias.builder()
                            .withSort(CoreSort.TYPE)
                            .withOuterTarget(
                                    CoreOuterAliasTarget.builder()
                                            .withTypeIndex(1)
                                            .withSortIndex(0)
                                            .build())
                            .build();

            var moduleDecl = ModuleDecl.builder().withAlias(alias).build();
            var moduleType = ModuleType.builder().addModuleDecl(moduleDecl).build();
            var coreModuleType = CoreType.builder().withModuleType(moduleType).build();

            var coreTypeSection =
                    CoreTypeSection.builder()
                            .addCoreType(coreFuncType)
                            .addCoreType(coreModuleType)
                            .build();
            return WasmComponent.builder().addCoreTypeSection(coreTypeSection).build();
        }
    }
}
