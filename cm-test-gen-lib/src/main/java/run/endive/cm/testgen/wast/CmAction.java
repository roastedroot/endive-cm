package run.endive.cm.testgen.wast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class CmAction {

    private final String type;
    private final String field;
    private final String module;

    public CmAction(
            @JsonProperty("type") String type,
            @JsonProperty("field") String field,
            @JsonProperty("module") String module) {
        this.type = type;
        this.field = field;
        this.module = module;
    }

    public String type() {
        return type;
    }

    public String field() {
        return field;
    }

    public String module() {
        return module;
    }
}
