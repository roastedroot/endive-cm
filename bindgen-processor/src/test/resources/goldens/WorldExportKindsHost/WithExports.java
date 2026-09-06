package endive.testing;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.processing.Generated;
import run.endive.cm.runtime.ComponentFunction;
import run.endive.cm.runtime.ComponentInstance;
import run.endive.cm.runtime.ComponentLinker;
import run.endive.cm.runtime.ComponentStore;
import run.endive.cm.runtime.HostFunction;
import run.endive.cm.runtime.VoidHostTypeDescriptor;
import run.endive.cm.types.FuncType;
import run.endive.cm.types.LabelValType;
import run.endive.cm.types.PrimValType;
import run.endive.cm.types.ValType;
import run.endive.cm.types.WasmComponent;

/**
 * Bindings for the WIT world {@code example:world-exports/with-exports}.
 */
@Generated("run.endive.cm.bindgen.BindgenProcessor")
public final class WithExports {

    private static final FuncType LOG_FUNC = FuncType.builder().addParam(LabelValType.builder().withLabel("msg").withValType(ValType.builder().withPrimValType(PrimValType.STRING).build()).build()).build();

    /**
     * The world's imports, which the embedder implements.
     */
    public interface Imports {

        void log(String msg);
    }

    private final ComponentInstance instance;

    private final ComponentFunction run;

    private final endive.testing.exports.environment.Guest environment;

    private final endive.testing.exports.example.worldexports.units.Guest units;

    private WithExports(ComponentInstance instance) {
        this.instance = instance;
        this.run = instance.export("run").typed(VoidHostTypeDescriptor.instance());
        this.environment = new endive.testing.exports.environment.Guest(instance.exportedInstance("environment"));
        this.units = new endive.testing.exports.example.worldexports.units.Guest(instance.exportedInstance("example:world-exports/units"));
    }

    /**
     * Instantiates {@code component}, satisfying its imports with {@code imports}.
     */
    public static WithExports instantiate(ComponentStore store, WasmComponent component, Imports imports) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("log", HostFunction.of(store, LOG_FUNC, args -> {
            imports.log((String) args[0]);
            return new Object[0];
        }));
        return new WithExports(ComponentLinker.builder().build().instantiate(store, component, values));
    }

    /**
     * The component instance behind these bindings.
     */
    public ComponentInstance instance() {
        return instance;
    }

    public void run() {
        this.run.apply();
    }

    /**
     * The exported interface {@code environment}.
     */
    public endive.testing.exports.environment.Guest environment() {
        return environment;
    }

    /**
     * The exported interface {@code example:world-exports/units}.
     */
    public endive.testing.exports.example.worldexports.units.Guest units() {
        return units;
    }
}
