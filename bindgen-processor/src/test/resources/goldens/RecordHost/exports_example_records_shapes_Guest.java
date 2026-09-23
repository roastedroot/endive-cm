package endive.testing.exports.example.records.shapes;

import javax.annotation.processing.Generated;
import run.endive.cm.runtime.ComponentFunction;
import run.endive.cm.runtime.ComponentInstance;
import run.endive.cm.runtime.PrimitiveHostTypeDescriptor;
import run.endive.cm.runtime.RecordHostTypeDescriptor;

/**
 * The WIT interface {@code example:records/shapes}, as the component exports it.
 */
@Generated("run.endive.cm.bindgen.BindgenProcessor")
public final class Guest {

    private final ComponentFunction widen;

    /**
     * Built by the world's bindings. Public only because they are another package.
     */
    public Guest(ComponentInstance instance) {
        this.widen = instance.export("widen").typed(RecordHostTypeDescriptor.instance(), RecordHostTypeDescriptor.instance(), PrimitiveHostTypeDescriptor.forClass(Long.class));
    }

    public Span widen(Span s, Long by) {
        return Span.fromComponent(this.widen.apply(s.toComponent(), by)[0]);
    }
}
