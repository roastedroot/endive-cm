package endive.testing.exports.example.varianttypes.replies;

import javax.annotation.processing.Generated;
import run.endive.cm.runtime.ComponentFunction;
import run.endive.cm.runtime.ComponentInstance;
import run.endive.cm.runtime.VariantHostTypeDescriptor;
import run.endive.cm.runtime.VoidHostTypeDescriptor;

/**
 * The WIT interface {@code example:variant-types/replies}, as the component exports it.
 */
@Generated("run.endive.cm.bindgen.BindgenProcessor")
public final class Guest {

    private final ComponentFunction echo;

    private final ComponentFunction run;

    /**
     * Built by the world's bindings. Public only because they are another package.
     */
    public Guest(ComponentInstance instance) {
        this.echo = instance.export("echo").typed(VariantHostTypeDescriptor.instance(), VariantHostTypeDescriptor.instance());
        this.run = instance.export("run").typed(VoidHostTypeDescriptor.instance());
    }

    public Reply echo(Reply r) {
        return Reply.fromComponent(this.echo.apply(r.toComponent())[0]);
    }

    public void run() {
        this.run.apply();
    }
}
