package endive.testing.example.varianttypes.commands;

import javax.annotation.processing.Generated;

/**
 * The WIT interface {@code example:variant-types/commands}, which the embedder implements.
 */
@Generated("run.endive.cm.bindgen.BindgenProcessor")
public interface Host {

    Command handle(Command cmd);
}
