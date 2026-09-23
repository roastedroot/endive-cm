package endive.testing;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.processing.Generated;
import run.endive.cm.abi.VariantValue;
import run.endive.cm.runtime.ComponentInstance;
import run.endive.cm.runtime.ComponentLinker;
import run.endive.cm.runtime.ComponentStore;
import run.endive.cm.runtime.HostInstance;
import run.endive.cm.types.EnumType;
import run.endive.cm.types.FuncType;
import run.endive.cm.types.LabelValType;
import run.endive.cm.types.PrimValType;
import run.endive.cm.types.ResultType;
import run.endive.cm.types.Type;
import run.endive.cm.types.ValType;
import run.endive.cm.types.WasmComponent;

/**
 * Bindings for the WIT world {@code example:result-types/result-types}.
 */
@Generated("run.endive.cm.bindgen.BindgenProcessor")
public final class ResultTypes {

    /**
     * The world's imports, which the embedder implements.
     */
    public interface Imports {

        /**
         * The imported interface {@code example:result-types/parsing}.
         */
        endive.testing.example.resulttypes.parsing.Host parsing();
    }

    private final ComponentInstance instance;

    private final endive.testing.exports.example.resulttypes.running.Guest running;

    private ResultTypes(ComponentInstance instance) {
        this.instance = instance;
        this.running = new endive.testing.exports.example.resulttypes.running.Guest(instance.exportedInstance("example:result-types/running"));
    }

    /**
     * Instantiates {@code component}, satisfying its imports with {@code imports}.
     */
    public static ResultTypes instantiate(ComponentStore store, WasmComponent component, Imports imports) {
        Map<String, Object> values = new LinkedHashMap<>();
        endive.testing.example.resulttypes.parsing.Host parsing = imports.parsing();
        HostInstance.Builder parsingBuilder = HostInstance.builder(store);
        ValType parsingParseError = parsingBuilder.declareType(Type.of(EnumType.builder().addLabel("empty").addLabel("overflow").build()));
        ValType parsingType2 = parsingBuilder.declareType(Type.of(ResultType.builder().withOk(ValType.builder().withPrimValType(PrimValType.U32).build()).withError(parsingParseError).build()));
        parsingBuilder.addFunction("parse", FuncType.builder().addParam(LabelValType.builder().withLabel("text").withValType(ValType.builder().withPrimValType(PrimValType.STRING).build()).build()).withResult(parsingType2).build(), args -> {
            try {
                return new Object[] { VariantValue.of("ok", parsing.parse((String) args[0])) };
            } catch (endive.testing.example.resulttypes.parsing.ParseErrorException caught) {
                return new Object[] { VariantValue.of("error", caught.error().toComponent()) };
            }
        });
        values.put("example:result-types/parsing", parsingBuilder.build());
        return new ResultTypes(ComponentLinker.builder().build().instantiate(store, component, values));
    }

    /**
     * The component instance behind these bindings.
     */
    public ComponentInstance instance() {
        return instance;
    }

    /**
     * The exported interface {@code example:result-types/running}.
     */
    public endive.testing.exports.example.resulttypes.running.Guest running() {
        return running;
    }
}
