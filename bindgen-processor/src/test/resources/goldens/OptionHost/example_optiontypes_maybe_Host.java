package endive.testing.example.optiontypes.maybe;

import java.util.List;
import javax.annotation.processing.Generated;

/**
 * The WIT interface {@code example:option-types/maybe}, which the embedder implements.
 */
@Generated("run.endive.cm.bindgen.BindgenProcessor")
public interface Host {

    Long echo(Long value);

    String label(String text);

    Long first(List<Long> values);

    Long nested(List<Long> values);
}
