package endive.testing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.processing.Generated;
import run.endive.cm.abi.VariantValue;
import run.endive.cm.runtime.ComponentFunction;
import run.endive.cm.runtime.ComponentInstance;
import run.endive.cm.runtime.ComponentLinker;
import run.endive.cm.runtime.ComponentStore;
import run.endive.cm.runtime.HostInstance;
import run.endive.cm.runtime.VariantHostTypeDescriptor;
import run.endive.cm.types.FuncType;
import run.endive.cm.types.LabelValType;
import run.endive.cm.types.ListType;
import run.endive.cm.types.OptionType;
import run.endive.cm.types.PrimValType;
import run.endive.cm.types.Type;
import run.endive.cm.types.ValType;
import run.endive.cm.types.WasmComponent;

/**
 * Bindings for the WIT world {@code example:option-types/option-types}.
 */
@Generated("run.endive.cm.bindgen.BindgenProcessor")
@SuppressWarnings("unchecked")
public final class OptionTypes {

    /**
     * The world's imports, which the embedder implements.
     */
    public interface Imports {

        /**
         * The imported interface {@code example:option-types/maybe}.
         */
        endive.testing.example.optiontypes.maybe.Host maybe();
    }

    private final ComponentInstance instance;

    private final ComponentFunction run;

    private OptionTypes(ComponentInstance instance) {
        this.instance = instance;
        this.run = instance.export("run").typed(VariantHostTypeDescriptor.instance(), VariantHostTypeDescriptor.instance());
    }

    /**
     * Instantiates {@code component}, satisfying its imports with {@code imports}.
     */
    public static OptionTypes instantiate(ComponentStore store, WasmComponent component, Imports imports) {
        Map<String, Object> values = new LinkedHashMap<>();
        endive.testing.example.optiontypes.maybe.Host maybe = imports.maybe();
        HostInstance.Builder maybeBuilder = HostInstance.builder(store);
        ValType maybeType0 = maybeBuilder.declareType(Type.of(OptionType.builder().withValType(ValType.builder().withPrimValType(PrimValType.U32).build()).build()));
        ValType maybeType2 = maybeBuilder.declareType(Type.of(OptionType.builder().withValType(ValType.builder().withPrimValType(PrimValType.STRING).build()).build()));
        ValType maybeType4 = maybeBuilder.declareType(Type.of(ListType.builder().withElementType(maybeType0).build()));
        ValType maybeType6 = maybeBuilder.declareType(Type.of(OptionType.builder().withValType(maybeType4).build()));
        maybeBuilder.addFunction("echo", FuncType.builder().addParam(LabelValType.builder().withLabel("value").withValType(maybeType0).build()).withResult(maybeType0).build(), args -> new Object[] { Optional.ofNullable(maybe.echo((Long) ((VariantValue) args[0]).value())).map(some -> VariantValue.of("some", some)).orElse(VariantValue.of("none", null)) });
        maybeBuilder.addFunction("label", FuncType.builder().addParam(LabelValType.builder().withLabel("text").withValType(maybeType2).build()).withResult(maybeType2).build(), args -> new Object[] { Optional.ofNullable(maybe.label((String) ((VariantValue) args[0]).value())).map(some -> VariantValue.of("some", some)).orElse(VariantValue.of("none", null)) });
        maybeBuilder.addFunction("first", FuncType.builder().addParam(LabelValType.builder().withLabel("values").withValType(maybeType4).build()).withResult(maybeType0).build(), args -> new Object[] { Optional.ofNullable(maybe.first(((List<Object>) args[0]).stream().map(element -> (Long) ((VariantValue) element).value()).collect(Collectors.toList()))).map(some -> VariantValue.of("some", some)).orElse(VariantValue.of("none", null)) });
        maybeBuilder.addFunction("nested", FuncType.builder().addParam(LabelValType.builder().withLabel("values").withValType(maybeType6).build()).withResult(maybeType0).build(), args -> new Object[] { Optional.ofNullable(maybe.nested(Optional.ofNullable(((VariantValue) args[0]).value()).map(some -> ((List<Object>) some).stream().map(element -> (Long) ((VariantValue) element).value()).collect(Collectors.toList())).orElse(null))).map(some -> VariantValue.of("some", some)).orElse(VariantValue.of("none", null)) });
        values.put("example:option-types/maybe", maybeBuilder.build());
        return new OptionTypes(ComponentLinker.builder().build().instantiate(store, component, values));
    }

    /**
     * The component instance behind these bindings.
     */
    public ComponentInstance instance() {
        return instance;
    }

    public Long run(Long value) {
        return (Long) ((VariantValue) this.run.apply(Optional.ofNullable(value).map(some -> VariantValue.of("some", some)).orElse(VariantValue.of("none", null)))[0]).value();
    }
}
