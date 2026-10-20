package run.endive.cm.wasi.p2.cli.environment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import run.endive.cm.runtime.Tuple2;
import run.endive.cm.wasi.p2.bindings.wasi.cli.environment.Host;

/**
 * The host side of {@code wasi:cli/environment}, which hands the guest the arguments and the
 * environment variables it was given. Every call returns the same values.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/cli/wit/environment.wit">wasi:cli/environment</a>
 */
public final class HostEnvironment implements Host {

    private final List<String> arguments;
    private final List<Tuple2<String, String>> environment;

    /**
     * @param environment the variables by name, in the order the guest sees them
     */
    public HostEnvironment(List<String> arguments, Map<String, String> environment) {
        this.arguments = List.copyOf(arguments);
        List<Tuple2<String, String>> variables = new ArrayList<>();
        for (Map.Entry<String, String> variable : environment.entrySet()) {
            variables.add(Tuple2.of(variable.getKey(), variable.getValue()));
        }
        this.environment = Collections.unmodifiableList(variables);
    }

    @Override
    public List<Tuple2<String, String>> getEnvironment() {
        return environment;
    }

    @Override
    public List<String> getArguments() {
        return arguments;
    }

    /** A guest reaches files only through its preopens, so there is no directory to name. */
    @Override
    public String initialCwd() {
        return null;
    }
}
