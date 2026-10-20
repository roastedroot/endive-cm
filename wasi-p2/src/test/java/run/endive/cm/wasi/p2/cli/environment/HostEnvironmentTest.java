package run.endive.cm.wasi.p2.cli.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import run.endive.cm.runtime.Tuple2;

class HostEnvironmentTest {

    @Test
    void theGivenArgumentsAreReturned() {
        HostEnvironment environment = new HostEnvironment(List.of("app", "--verbose"), Map.of());

        assertEquals(List.of("app", "--verbose"), environment.getArguments());
    }

    @Test
    void variablesAreReturnedInTheOrderGiven() {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("PATH", "/bin");
        variables.put("HOME", "/home/guest");
        variables.put("EMPTY", "");

        assertEquals(
                List.of(
                        Tuple2.of("PATH", "/bin"),
                        Tuple2.of("HOME", "/home/guest"),
                        Tuple2.of("EMPTY", "")),
                new HostEnvironment(List.of(), variables).getEnvironment());
    }

    /** Later changes to what was given do not reach the guest, which sees the same values always. */
    @Test
    void theValuesAreFixedWhenTheHostIsBuilt() {
        List<String> arguments = new ArrayList<>(List.of("app"));
        Map<String, String> variables = new LinkedHashMap<>(Map.of("A", "1"));
        HostEnvironment environment = new HostEnvironment(arguments, variables);

        arguments.add("late");
        variables.put("B", "2");

        assertEquals(List.of("app"), environment.getArguments());
        assertEquals(List.of(Tuple2.of("A", "1")), environment.getEnvironment());
    }

    @Test
    void thereIsNoInitialWorkingDirectory() {
        assertNull(new HostEnvironment(List.of(), Map.of()).initialCwd());
    }
}
