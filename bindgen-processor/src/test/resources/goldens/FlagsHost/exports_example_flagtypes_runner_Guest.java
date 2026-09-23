package endive.testing.exports.example.flagtypes.runner;

import javax.annotation.processing.Generated;
import run.endive.cm.runtime.ComponentFunction;
import run.endive.cm.runtime.ComponentInstance;
import run.endive.cm.runtime.RecordHostTypeDescriptor;

/**
 * The WIT interface {@code example:flag-types/runner}, as the component exports it.
 */
@Generated("run.endive.cm.bindgen.BindgenProcessor")
public final class Guest {

    private final ComponentFunction run;

    /**
     * Built by the world's bindings. Public only because they are another package.
     */
    public Guest(ComponentInstance instance) {
        this.run = instance.export("run").typed(RecordHostTypeDescriptor.instance(), RecordHostTypeDescriptor.instance());
    }

    public Mode run(Mode requested) {
        return Mode.fromComponent(this.run.apply(requested.toComponent())[0]);
    }
}
