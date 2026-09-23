package endive.testing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.Generated;
import run.endive.cm.runtime.ComponentInstance;
import run.endive.cm.runtime.ComponentLinker;
import run.endive.cm.runtime.ComponentStore;
import run.endive.cm.runtime.HostInstance;
import run.endive.cm.types.FuncType;
import run.endive.cm.types.LabelValType;
import run.endive.cm.types.ListType;
import run.endive.cm.types.PrimValType;
import run.endive.cm.types.Type;
import run.endive.cm.types.ValType;
import run.endive.cm.types.WasmComponent;

/**
 * Bindings for the WIT world {@code my:project/hello-world}.
 */
@Generated("run.endive.cm.bindgen.BindgenProcessor")
@SuppressWarnings("unchecked")
public final class HelloWorld {

    /**
     * The world's imports, which the embedder implements.
     */
    public interface Imports {

        /**
         * The imported interface {@code my:project/host}.
         */
        endive.testing.my.project.host.Host host();
    }

    private final ComponentInstance instance;

    private final endive.testing.exports.demo.Guest demo;

    private HelloWorld(ComponentInstance instance) {
        this.instance = instance;
        this.demo = new endive.testing.exports.demo.Guest(instance.exportedInstance("demo"));
    }

    /**
     * Instantiates {@code component}, satisfying its imports with {@code imports}.
     */
    public static HelloWorld instantiate(ComponentStore store, WasmComponent component, Imports imports) {
        Map<String, Object> values = new LinkedHashMap<>();
        endive.testing.my.project.host.Host host = imports.host();
        HostInstance.Builder hostBuilder = HostInstance.builder(store);
        ValType hostType1 = hostBuilder.declareType(Type.of(ListType.builder().withElementType(ValType.builder().withPrimValType(PrimValType.U8).build()).build()));
        hostBuilder.addFunction("gen-random-integer", FuncType.builder().withResult(ValType.builder().withPrimValType(PrimValType.U32).build()).build(), args -> new Object[] { host.genRandomInteger() });
        hostBuilder.addFunction("sha256", FuncType.builder().addParam(LabelValType.builder().withLabel("bytes").withValType(hostType1).build()).withResult(ValType.builder().withPrimValType(PrimValType.STRING).build()).build(), args -> new Object[] { host.sha256((List<Short>) args[0]) });
        values.put("my:project/host", hostBuilder.build());
        return new HelloWorld(ComponentLinker.builder().build().instantiate(store, component, values));
    }

    /**
     * The component instance behind these bindings.
     */
    public ComponentInstance instance() {
        return instance;
    }

    /**
     * The exported interface {@code demo}.
     */
    public endive.testing.exports.demo.Guest demo() {
        return demo;
    }
}
