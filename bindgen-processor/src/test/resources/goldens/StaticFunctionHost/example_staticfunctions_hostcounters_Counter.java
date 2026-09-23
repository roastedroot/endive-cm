package endive.testing.example.staticfunctions.hostcounters;

import javax.annotation.processing.Generated;

/**
 * The WIT resource {@code counter}, which the embedder implements. A method's borrowed receiver is what Java carries as {@code this}, so it is not a parameter here.
 */
@Generated("run.endive.cm.bindgen.BindgenProcessor")
public interface Counter {

    Long get();

    /**
     * Called when the guest drops an owned handle to this resource.
     */
    default void drop() {
    }
}
