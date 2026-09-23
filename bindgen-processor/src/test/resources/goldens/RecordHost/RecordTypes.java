package endive.testing;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.processing.Generated;
import run.endive.cm.runtime.ComponentFunction;
import run.endive.cm.runtime.ComponentInstance;
import run.endive.cm.runtime.ComponentLinker;
import run.endive.cm.runtime.ComponentStore;
import run.endive.cm.runtime.HostInstance;
import run.endive.cm.runtime.PrimitiveHostTypeDescriptor;
import run.endive.cm.types.FuncType;
import run.endive.cm.types.LabelValType;
import run.endive.cm.types.ListType;
import run.endive.cm.types.PrimValType;
import run.endive.cm.types.RecordType;
import run.endive.cm.types.Type;
import run.endive.cm.types.ValType;
import run.endive.cm.types.WasmComponent;

/**
 * Bindings for the WIT world {@code example:records/record-types}.
 */
@Generated("run.endive.cm.bindgen.BindgenProcessor")
public final class RecordTypes {

    /**
     * The world's imports, which the embedder implements.
     */
    public interface Imports {

        /**
         * The imported interface {@code example:records/types}.
         */
        endive.testing.example.records.types.Host types();
    }

    private final ComponentInstance instance;

    private final ComponentFunction check;

    private final endive.testing.exports.example.records.shapes.Guest shapes;

    private RecordTypes(ComponentInstance instance) {
        this.instance = instance;
        this.check = instance.export("check").typed(PrimitiveHostTypeDescriptor.forClass(Long.class));
        this.shapes = new endive.testing.exports.example.records.shapes.Guest(instance.exportedInstance("example:records/shapes"));
    }

    /**
     * Instantiates {@code component}, satisfying its imports with {@code imports}.
     */
    public static RecordTypes instantiate(ComponentStore store, WasmComponent component, Imports imports) {
        Map<String, Object> values = new LinkedHashMap<>();
        endive.testing.example.records.types.Host types = imports.types();
        HostInstance.Builder typesBuilder = HostInstance.builder(store);
        ValType typesPoint = typesBuilder.declareType(Type.of(RecordType.builder().addField(LabelValType.builder().withLabel("x").withValType(ValType.builder().withPrimValType(PrimValType.U32).build()).build()).addField(LabelValType.builder().withLabel("y").withValType(ValType.builder().withPrimValType(PrimValType.U32).build()).build()).build()));
        ValType typesType2 = typesBuilder.declareType(Type.of(ListType.builder().withElementType(ValType.builder().withPrimValType(PrimValType.STRING).build()).build()));
        ValType typesPerson = typesBuilder.declareType(Type.of(RecordType.builder().addField(LabelValType.builder().withLabel("name").withValType(ValType.builder().withPrimValType(PrimValType.STRING).build()).build()).addField(LabelValType.builder().withLabel("active").withValType(ValType.builder().withPrimValType(PrimValType.BOOL).build()).build()).addField(LabelValType.builder().withLabel("id").withValType(ValType.builder().withPrimValType(PrimValType.U64).build()).build()).addField(LabelValType.builder().withLabel("initial").withValType(ValType.builder().withPrimValType(PrimValType.CHAR).build()).build()).addField(LabelValType.builder().withLabel("tags").withValType(typesType2).build()).addField(LabelValType.builder().withLabel("home").withValType(typesPoint).build()).build()));
        typesBuilder.addFunction("resident", FuncType.builder().withResult(typesPerson).build(), args -> new Object[] { types.resident().toComponent() });
        values.put("example:records/types", typesBuilder.build());
        return new RecordTypes(ComponentLinker.builder().build().instantiate(store, component, values));
    }

    /**
     * The component instance behind these bindings.
     */
    public ComponentInstance instance() {
        return instance;
    }

    public Long check() {
        return (Long) this.check.apply()[0];
    }

    /**
     * The exported interface {@code example:records/shapes}.
     */
    public endive.testing.exports.example.records.shapes.Guest shapes() {
        return shapes;
    }
}
