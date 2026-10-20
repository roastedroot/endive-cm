package run.endive.cm.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import run.endive.cm.corpus.ComponentCorpus;

/** Every component the corpus compiled with a real toolchain. */
public class ComponentCorpusTests {

    static Stream<String> components() {
        return ComponentCorpus.entries().stream().filter(entry -> entry.endsWith(".wasm"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("components")
    public void aCompiledComponentParsesAndValidates(String component) {
        var parsed = TestComponents.parse(ComponentCorpus.readBytes(component));

        assertFalse(parsed.sections().isEmpty());
    }

    /** The index is written by hand-run tooling, so it may fall out of step with the files. */
    @Test
    public void everyIndexedFileIsPresent() {
        for (String entry : ComponentCorpus.entries()) {
            ComponentCorpus.readBytes(entry);
        }
    }
}
