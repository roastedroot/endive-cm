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
import run.endive.cm.types.*;
import run.endive.wasm.types.CompType;
import run.endive.wasm.types.FunctionType;
import run.endive.wasm.types.RecType;
import run.endive.wasm.types.SubType;

public class ComponentParserTests {

    static java.util.stream.Stream<Arguments> testLocations() {

        return java.util.stream.Stream.of(
                // Arguments.arguments("./wasmtime/types.wast", new int[] {145}),
                Arguments.arguments("./wasm-tools/types.wast", WasmToolsTypesAssertions.expectedComponents, new int[0]));
    }

    @ParameterizedTest
    @MethodSource("testLocations")
    void allSpecTests(String testLocation, Map<Integer, WasmComponent> expectedComponents, int[] skipLines) throws IOException {
        var testDir = Path.of("src/test/resources/spec-tests");
        var testFile = testDir.resolve(testLocation);
        try (var wast = Files.newInputStream(testFile);
                var wastTests = JsonFromWast.exec(wast)) {
            wastTests.runTests(expectedComponents, skipLines);
        } catch (Exception e) {
            if (e instanceof WastTests.CommandException) {
                var ce = (WastTests.CommandException) e;
                var outFile = testDir.resolve("./failed.wasm");
                Files.write(outFile, ce.testContents());
            }
            fail(e);
        }
    }

    private static final class WasmToolsTypesAssertions {

        private final static Map<Integer, WasmComponent> expectedComponents = Map.of(17, case17());

        private static WasmComponent case17() {
            var funcType = FunctionType.of(new ArrayList<>(), new ArrayList<>());
            var compType = CompType.builder().withFuncType(funcType).build();
            var subType = SubType.builder().withTypeIdx(new int[] {}).withFinal(true).withCompType(compType).build();
            var recType = RecType.builder().withSubTypes(new SubType[] {subType}).build();
            var coreFuncType = CoreType.builder().withRecType(recType).build();

            var alias = CoreAlias.builder().withSort(CoreSort.TYPE).
                    withOuterTarget(CoreOuterAliasTarget.builder()
                            .withTypeIndex(1)
                            .withSortIndex(0)
                            .build()).build();
            var moduleDecl = ModuleDecl.builder().withAlias(alias).build();
            var moduleType = ModuleType.builder().addModuleDecl(moduleDecl).build();
            var coreModuleType = CoreType.builder().withModuleType(moduleType).build();

            var coreTypeSection = CoreTypeSection.builder().addCoreType(coreFuncType).addCoreType(coreModuleType).build();
            return WasmComponent.builder().addCoreTypeSection(coreTypeSection).build();
        }
    }
}
