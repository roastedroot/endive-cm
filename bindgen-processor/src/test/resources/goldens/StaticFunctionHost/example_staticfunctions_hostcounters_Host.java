package endive.testing.example.staticfunctions.hostcounters;

import javax.annotation.processing.Generated;

/**
 * The WIT interface {@code example:static-functions/host-counters}, which the embedder implements.
 */
@Generated("run.endive.cm.bindgen.BindgenProcessor")
public interface Host {

    /**
     * Makes a {@code counter}.
     */
    Counter counter(Long start);

    /**
     * The static {@code counter.open}.
     */
    Counter counterOpen(Long start);

    /**
     * The static {@code counter.made}.
     */
    Long counterMade();
}
