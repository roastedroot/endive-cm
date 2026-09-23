package endive.testing.exports.example.staticfunctions.guestcounters;

import javax.annotation.processing.Generated;
import run.endive.cm.abi.ResourceValue;
import run.endive.cm.runtime.GuestResource;

/**
 * The WIT resource {@code tally}, which {@code example:static-functions/guest-counters} implements. Nothing destroys it on the embedder's behalf, so closing one is what runs the guest's destructor.
 */
@Generated("run.endive.cm.bindgen.BindgenProcessor")
public final class Tally implements AutoCloseable {

    private final Guest owner;

    private final ResourceValue handle;

    private boolean dropped;

    Tally(Guest owner, ResourceValue handle) {
        this.owner = owner;
        this.handle = handle;
    }

    public Long get() {
        return (Long) owner.tallyGet.apply(handle)[0];
    }

    /**
     * Runs the guest's destructor. Doing so more than once does nothing.
     */
    @Override
    public void close() {
        if (!dropped) {
            dropped = true;
            GuestResource.drop(handle);
        }
    }
}
