package endive.testing;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.processing.Generated;
import run.endive.cm.runtime.ComponentInstance;
import run.endive.cm.runtime.ComponentLinker;
import run.endive.cm.runtime.ComponentStore;
import run.endive.cm.runtime.HostInstance;
import run.endive.cm.types.Case;
import run.endive.cm.types.FuncType;
import run.endive.cm.types.LabelValType;
import run.endive.cm.types.PrimValType;
import run.endive.cm.types.Type;
import run.endive.cm.types.ValType;
import run.endive.cm.types.VariantType;
import run.endive.cm.types.WasmComponent;

/**
 * Bindings for the WIT world {@code example:variant-types/variant-types}.
 */
@Generated("run.endive.cm.bindgen.BindgenProcessor")
public final class VariantTypes {

    /**
     * The world's imports, which the embedder implements.
     */
    public interface Imports {

        /**
         * The imported interface {@code example:variant-types/commands}.
         */
        endive.testing.example.varianttypes.commands.Host commands();
    }

    private final ComponentInstance instance;

    private final endive.testing.exports.example.varianttypes.replies.Guest replies;

    private VariantTypes(ComponentInstance instance) {
        this.instance = instance;
        this.replies = new endive.testing.exports.example.varianttypes.replies.Guest(instance.exportedInstance("example:variant-types/replies"));
    }

    /**
     * Instantiates {@code component}, satisfying its imports with {@code imports}.
     */
    public static VariantTypes instantiate(ComponentStore store, WasmComponent component, Imports imports) {
        Map<String, Object> values = new LinkedHashMap<>();
        endive.testing.example.varianttypes.commands.Host commands = imports.commands();
        HostInstance.Builder commandsBuilder = HostInstance.builder(store);
        ValType commandsCommand = commandsBuilder.declareType(Type.of(VariantType.builder().addCase(Case.builder().withLabel("stop").build()).addCase(Case.builder().withLabel("jump").withValType(ValType.builder().withPrimValType(PrimValType.U32).build()).build()).addCase(Case.builder().withLabel("speak").withValType(ValType.builder().withPrimValType(PrimValType.STRING).build()).build()).build()));
        commandsBuilder.addFunction("handle", FuncType.builder().addParam(LabelValType.builder().withLabel("cmd").withValType(commandsCommand).build()).withResult(commandsCommand).build(), args -> new Object[] { commands.handle(endive.testing.example.varianttypes.commands.Command.fromComponent(args[0])).toComponent() });
        values.put("example:variant-types/commands", commandsBuilder.build());
        return new VariantTypes(ComponentLinker.builder().build().instantiate(store, component, values));
    }

    /**
     * The component instance behind these bindings.
     */
    public ComponentInstance instance() {
        return instance;
    }

    /**
     * The exported interface {@code example:variant-types/replies}.
     */
    public endive.testing.exports.example.varianttypes.replies.Guest replies() {
        return replies;
    }
}
