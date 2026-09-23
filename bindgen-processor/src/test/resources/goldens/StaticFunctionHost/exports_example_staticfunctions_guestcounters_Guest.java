package endive.testing.exports.example.staticfunctions.guestcounters;

import javax.annotation.processing.Generated;
import run.endive.cm.abi.ResourceValue;
import run.endive.cm.runtime.ComponentFunction;
import run.endive.cm.runtime.ComponentInstance;
import run.endive.cm.runtime.PrimitiveHostTypeDescriptor;
import run.endive.cm.runtime.ResourceHostTypeDescriptor;

/**
 * The WIT interface {@code example:static-functions/guest-counters}, as the component exports it.
 */
@Generated("run.endive.cm.bindgen.BindgenProcessor")
public final class Guest {

    final ComponentFunction tallyTally;

    final ComponentFunction tallyGet;

    final ComponentFunction tallyOpen;

    final ComponentFunction tallyMade;

    /**
     * Built by the world's bindings. Public only because they are another package.
     */
    public Guest(ComponentInstance instance) {
        this.tallyTally = instance.export("[constructor]tally").typed(ResourceHostTypeDescriptor.instance(), PrimitiveHostTypeDescriptor.forClass(Long.class));
        this.tallyGet = instance.export("[method]tally.get").typed(PrimitiveHostTypeDescriptor.forClass(Long.class), ResourceHostTypeDescriptor.instance());
        this.tallyOpen = instance.export("[static]tally.open").typed(ResourceHostTypeDescriptor.instance(), PrimitiveHostTypeDescriptor.forClass(Long.class));
        this.tallyMade = instance.export("[static]tally.made").typed(PrimitiveHostTypeDescriptor.forClass(Long.class));
    }

    /**
     * Makes a {@code tally} inside the component.
     */
    public Tally tally(Long start) {
        return new Tally(this, (ResourceValue) this.tallyTally.apply(start)[0]);
    }

    /**
     * The static {@code tally.open}.
     */
    public Tally tallyOpen(Long start) {
        return new Tally(this, (ResourceValue) this.tallyOpen.apply(start)[0]);
    }

    /**
     * The static {@code tally.made}.
     */
    public Long tallyMade() {
        return (Long) this.tallyMade.apply()[0];
    }
}
