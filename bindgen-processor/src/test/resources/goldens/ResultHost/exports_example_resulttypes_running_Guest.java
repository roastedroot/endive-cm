package endive.testing.exports.example.resulttypes.running;

import javax.annotation.processing.Generated;
import run.endive.cm.abi.VariantValue;
import run.endive.cm.runtime.ComponentFunction;
import run.endive.cm.runtime.ComponentInstance;
import run.endive.cm.runtime.PrimitiveHostTypeDescriptor;
import run.endive.cm.runtime.VariantHostTypeDescriptor;

/**
 * The WIT interface {@code example:result-types/running}, as the component exports it.
 */
@Generated("run.endive.cm.bindgen.BindgenProcessor")
public final class Guest {

    private final ComponentFunction run;

    private final ComponentFunction check;

    private final ComponentFunction count;

    private final ComponentFunction ping;

    /**
     * Built by the world's bindings. Public only because they are another package.
     */
    public Guest(ComponentInstance instance) {
        this.run = instance.export("run").typed(VariantHostTypeDescriptor.instance(), PrimitiveHostTypeDescriptor.forClass(String.class));
        this.check = instance.export("check").typed(VariantHostTypeDescriptor.instance(), PrimitiveHostTypeDescriptor.forClass(Long.class));
        this.count = instance.export("count").typed(VariantHostTypeDescriptor.instance(), PrimitiveHostTypeDescriptor.forClass(Boolean.class));
        this.ping = instance.export("ping").typed(VariantHostTypeDescriptor.instance(), PrimitiveHostTypeDescriptor.forClass(Boolean.class));
    }

    public Long run(String text) {
        VariantValue outcome = (VariantValue) this.run.apply(text)[0];
        if (!"ok".equals(outcome.label())) {
            throw new RunErrorException(RunError.fromComponent(outcome.value()));
        }
        return (Long) outcome.value();
    }

    public void check(Long value) {
        VariantValue outcome = (VariantValue) this.check.apply(value)[0];
        if (!"ok".equals(outcome.label())) {
            throw new RunErrorException(RunError.fromComponent(outcome.value()));
        }
    }

    public Long count(Boolean fail) {
        VariantValue outcome = (VariantValue) this.count.apply(fail)[0];
        if (!"ok".equals(outcome.label())) {
            throw new RunningResult6Exception();
        }
        return (Long) outcome.value();
    }

    public void ping(Boolean fail) {
        VariantValue outcome = (VariantValue) this.ping.apply(fail)[0];
        if (!"ok".equals(outcome.label())) {
            throw new RunningResult8Exception();
        }
    }
}
