package run.endive.cm.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import run.endive.cm.types.WasmComponent;

/**
 * Instantiating a core module runs its start function and nothing else. A WASI preview 1 command
 * adapted into a component exports {@code _start}, which the adapter calls once the component runs.
 *
 * @see <a href="https://github.com/WebAssembly/component-model/blob/706074c96bc14cfc58469e1bdc452bb4d91921c7/design/mvp/Explainer.md#instance-definitions">Explainer.md, instance definitions</a>
 */
public class CoreStartTests {

    @Test
    public void onlyTheStartFunctionRunsWhenACoreModuleIsInstantiated() {
        var instance =
                ComponentLinker.builder()
                        .build()
                        .instantiate(new ComponentStore(), component(), Map.of());

        assertEquals(1L, instance.export("start-ran").apply()[0]);
        assertEquals(0L, instance.export("underscore-start-ran").apply()[0]);
    }

    private static WasmComponent component() {
        try (InputStream is = CoreStartTests.class.getResourceAsStream("/core-start/start.wat")) {
            assertNotNull(is);
            return TestComponents.fromWat(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
