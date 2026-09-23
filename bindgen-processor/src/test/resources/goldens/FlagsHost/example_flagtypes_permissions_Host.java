package endive.testing.example.flagtypes.permissions;

import javax.annotation.processing.Generated;

/**
 * The WIT interface {@code example:flag-types/permissions}, which the embedder implements.
 */
@Generated("run.endive.cm.bindgen.BindgenProcessor")
public interface Host {

    Permission grant(Permission requested);
}
