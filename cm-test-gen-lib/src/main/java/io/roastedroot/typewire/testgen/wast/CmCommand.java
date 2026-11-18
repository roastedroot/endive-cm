package io.roastedroot.typewire.testgen.wast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class CmCommand {
    private final String type;
    private final int line;
    private final String filename;
    private final String name;
    private final String moduleType;
    private final String text;
    private final CmAction action;
    private final List<TypedValue> expected;
    private final String instance;
    private final String module;

    public CmCommand(
            @JsonProperty("type") String type,
            @JsonProperty("line") int line,
            @JsonProperty("filename") String filename,
            @JsonProperty("name") String name,
            @JsonProperty("module_type") String moduleType,
            @JsonProperty("text") String text,
            @JsonProperty("action") CmAction action,
            @JsonProperty("expected") List<TypedValue> expected,
            @JsonProperty("instance") String instance,
            @JsonProperty("module") String module) {
        this.type = type;
        this.line = line;
        this.filename = filename;
        this.name = name;
        this.moduleType = moduleType;
        this.text = text;
        this.action = action;
        this.expected = expected;
        this.instance = instance;
        this.module = module;
    }

    public CmCommandType commandType() {
        return CmCommandType.fromString(type);
    }

    public String type() {
        return type;
    }

    public int line() {
        return line;
    }

    public String filename() {
        return filename;
    }

    public String name() {
        return name;
    }

    public String moduleType() {
        return moduleType;
    }

    public String text() {
        return text;
    }

    public CmAction action() {
        return action;
    }

    public List<TypedValue> expected() {
        return expected;
    }

    /** The name a {@code module_instance} command gives the instance it creates. */
    public String instance() {
        return instance;
    }

    /** The name of the {@code module_definition} a {@code module_instance} command instantiates. */
    public String module() {
        return module;
    }

    public String emitExpected() {
        if (!expected.isEmpty()) {
            return expected.stream().map(TypedValue::emitValue).collect(Collectors.joining(", "));
        }
        return "";
    }
}
