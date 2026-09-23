package endive.testing;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.processing.Generated;
import run.endive.cm.runtime.ComponentInstance;
import run.endive.cm.runtime.ComponentLinker;
import run.endive.cm.runtime.ComponentStore;
import run.endive.cm.runtime.HostInstance;
import run.endive.cm.types.FlagsType;
import run.endive.cm.types.FuncType;
import run.endive.cm.types.LabelValType;
import run.endive.cm.types.Type;
import run.endive.cm.types.ValType;
import run.endive.cm.types.WasmComponent;

/**
 * Bindings for the WIT world {@code example:flag-types/flag-types}.
 */
@Generated("run.endive.cm.bindgen.BindgenProcessor")
public final class FlagTypes {

    /**
     * The world's imports, which the embedder implements.
     */
    public interface Imports {

        /**
         * The imported interface {@code example:flag-types/permissions}.
         */
        endive.testing.example.flagtypes.permissions.Host permissions();
    }

    private final ComponentInstance instance;

    private final endive.testing.exports.example.flagtypes.runner.Guest runner;

    private FlagTypes(ComponentInstance instance) {
        this.instance = instance;
        this.runner = new endive.testing.exports.example.flagtypes.runner.Guest(instance.exportedInstance("example:flag-types/runner"));
    }

    /**
     * Instantiates {@code component}, satisfying its imports with {@code imports}.
     */
    public static FlagTypes instantiate(ComponentStore store, WasmComponent component, Imports imports) {
        Map<String, Object> values = new LinkedHashMap<>();
        endive.testing.example.flagtypes.permissions.Host permissions = imports.permissions();
        HostInstance.Builder permissionsBuilder = HostInstance.builder(store);
        ValType permissionsPermission = permissionsBuilder.declareType(Type.of(FlagsType.builder().addLabel("read").addLabel("write").addLabel("exec").build()));
        permissionsBuilder.addFunction("grant", FuncType.builder().addParam(LabelValType.builder().withLabel("requested").withValType(permissionsPermission).build()).withResult(permissionsPermission).build(), args -> new Object[] { permissions.grant(endive.testing.example.flagtypes.permissions.Permission.fromComponent(args[0])).toComponent() });
        values.put("example:flag-types/permissions", permissionsBuilder.build());
        return new FlagTypes(ComponentLinker.builder().build().instantiate(store, component, values));
    }

    /**
     * The component instance behind these bindings.
     */
    public ComponentInstance instance() {
        return instance;
    }

    /**
     * The exported interface {@code example:flag-types/runner}.
     */
    public endive.testing.exports.example.flagtypes.runner.Guest runner() {
        return runner;
    }
}
