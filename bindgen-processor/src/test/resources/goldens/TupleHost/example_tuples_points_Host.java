package endive.testing.example.tuples.points;

import javax.annotation.processing.Generated;
import run.endive.cm.runtime.Tuple2;

/**
 * The WIT interface {@code example:tuples/points}, which the embedder implements.
 */
@Generated("run.endive.cm.bindgen.BindgenProcessor")
public interface Host {

    Tuple2<Long, Long> describe(Tuple2<String, Long> point);
}
