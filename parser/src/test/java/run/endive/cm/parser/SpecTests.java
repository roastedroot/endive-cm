 package run.endive.cm.parser;

 import java.nio.file.Files;
 import java.nio.file.Path;

 import org.junit.jupiter.params.ParameterizedTest;
 import org.junit.jupiter.params.provider.Arguments;
 import org.junit.jupiter.params.provider.MethodSource;
 import run.endive.cm.tools.JsonFromWast;

 import static org.junit.jupiter.api.Assertions.fail;

 public class SpecTests {

    static java.util.stream.Stream<Arguments> testLocations() {
        return java.util.stream.Stream.of(
                //Arguments.arguments("./wasmtime/types.wast", new int[] {145}),
                Arguments.arguments("./wasm-tools/types.wast", new int[0]));
    }

    @ParameterizedTest
    @MethodSource("testLocations")
    void allSpecTests(String testLocation, int[] skipLines) {
        var testDir = Path.of("src/test/resources/spec-tests");
        var testFile = testDir.resolve(testLocation);
        try (var wast = Files.newInputStream(testFile); var wastTests = JsonFromWast.exec(wast)){
            wastTests.runTests(skipLines);
        } catch (Exception e) {
            fail(e);
        }
    }
 }
