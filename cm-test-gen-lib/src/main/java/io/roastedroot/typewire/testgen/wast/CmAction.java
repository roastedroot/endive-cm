package io.roastedroot.typewire.testgen.wast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class CmAction {

    private final String type;
    private final String field;
    private final String module;
    private final List<TypedValue> args;

    public CmAction(
            @JsonProperty("type") String type,
            @JsonProperty("field") String field,
            @JsonProperty("module") String module,
            @JsonProperty("args") List<TypedValue> args) {
        this.type = type;
        this.field = field;
        this.module = module;
        this.args = args;
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

    public List<TypedValue> args() {
        return args;
    }

    public String emitArgs() {
        if (!args.isEmpty()) {
            return args.stream().map(TypedValue::emitValue).collect(Collectors.joining(", "));
        }
        return "";
    }
}
