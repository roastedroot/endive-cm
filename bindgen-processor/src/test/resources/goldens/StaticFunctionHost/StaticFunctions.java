package endive.testing;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.processing.Generated;
import run.endive.cm.abi.ResourceValue;
import run.endive.cm.runtime.ComponentFunction;
import run.endive.cm.runtime.ComponentInstance;
import run.endive.cm.runtime.ComponentLinker;
import run.endive.cm.runtime.ComponentStore;
import run.endive.cm.runtime.HostInstance;
import run.endive.cm.runtime.HostResource;
import run.endive.cm.runtime.HostResourceTable;
import run.endive.cm.runtime.PrimitiveHostTypeDescriptor;
import run.endive.cm.types.FuncType;
import run.endive.cm.types.LabelValType;
import run.endive.cm.types.PrimValType;
import run.endive.cm.types.ValType;
import run.endive.cm.types.WasmComponent;

/**
 * Bindings for the WIT world {@code example:static-functions/static-functions}.
 */
@Generated("run.endive.cm.bindgen.BindgenProcessor")
public final class StaticFunctions {

    /**
     * The world's imports, which the embedder implements.
     */
    public interface Imports {

        /**
         * The imported interface {@code example:static-functions/host-counters}.
         */
        endive.testing.example.staticfunctions.hostcounters.Host hostCounters();
    }

    private final ComponentInstance instance;

    private final ComponentFunction run;

    private final endive.testing.exports.example.staticfunctions.guestcounters.Guest guestCounters;

    private StaticFunctions(ComponentInstance instance) {
        this.instance = instance;
        this.run = instance.export("run").typed(PrimitiveHostTypeDescriptor.forClass(Long.class));
        this.guestCounters = new endive.testing.exports.example.staticfunctions.guestcounters.Guest(instance.exportedInstance("example:static-functions/guest-counters"));
    }

    /**
     * Instantiates {@code component}, satisfying its imports with {@code imports}.
     */
    public static StaticFunctions instantiate(ComponentStore store, WasmComponent component, Imports imports) {
        Map<String, Object> values = new LinkedHashMap<>();
        endive.testing.example.staticfunctions.hostcounters.Host hostCounters = imports.hostCounters();
        HostInstance.Builder hostCountersBuilder = HostInstance.builder(store);
        HostResourceTable<endive.testing.example.staticfunctions.hostcounters.Counter> hostCountersCounterTable = new HostResourceTable<>();
        HostResource hostCountersCounter = hostCountersBuilder.declareResource(rep -> hostCountersCounterTable.drop(rep, endive.testing.example.staticfunctions.hostcounters.Counter::drop));
        hostCountersBuilder.addResource("counter", hostCountersCounter);
        hostCountersBuilder.addFunction("[constructor]counter", FuncType.builder().addParam(LabelValType.builder().withLabel("start").withValType(ValType.builder().withPrimValType(PrimValType.U32).build()).build()).withResult(hostCountersCounter.own()).build(), args -> new Object[] { ResourceValue.owned(hostCountersCounter.type(), hostCountersCounterTable.add(hostCounters.counter((Long) args[0]))) });
        hostCountersBuilder.addFunction("[static]counter.open", FuncType.builder().addParam(LabelValType.builder().withLabel("start").withValType(ValType.builder().withPrimValType(PrimValType.U32).build()).build()).withResult(hostCountersCounter.own()).build(), args -> new Object[] { ResourceValue.owned(hostCountersCounter.type(), hostCountersCounterTable.add(hostCounters.counterOpen((Long) args[0]))) });
        hostCountersBuilder.addFunction("[static]counter.made", FuncType.builder().withResult(ValType.builder().withPrimValType(PrimValType.U32).build()).build(), args -> new Object[] { hostCounters.counterMade() });
        hostCountersBuilder.addFunction("[method]counter.get", FuncType.builder().addParam(LabelValType.builder().withLabel("self").withValType(hostCountersCounter.borrow()).build()).withResult(ValType.builder().withPrimValType(PrimValType.U32).build()).build(), args -> new Object[] { hostCountersCounterTable.get((ResourceValue) args[0]).get() });
        values.put("example:static-functions/host-counters", hostCountersBuilder.build());
        return new StaticFunctions(ComponentLinker.builder().build().instantiate(store, component, values));
    }

    /**
     * The component instance behind these bindings.
     */
    public ComponentInstance instance() {
        return instance;
    }

    public Long run() {
        return (Long) this.run.apply()[0];
    }

    /**
     * The exported interface {@code example:static-functions/guest-counters}.
     */
    public endive.testing.exports.example.staticfunctions.guestcounters.Guest guestCounters() {
        return guestCounters;
    }
}
