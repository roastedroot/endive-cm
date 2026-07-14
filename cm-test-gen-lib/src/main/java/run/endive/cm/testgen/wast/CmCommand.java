package run.endive.cm.testgen.wast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class CmCommand {
    private final String type;
    private final int line;
    private final String filename;
    private final String name;
    private final String moduleType;
    private final String text;
    private final CmAction action;
    private final JsonNode[] expected;

    public CmCommand(
            @JsonProperty("type") String type,
            @JsonProperty("line") int line,
            @JsonProperty("filename") String filename,
            @JsonProperty("name") String name,
            @JsonProperty("module_type") String moduleType,
            @JsonProperty("text") String text,
            @JsonProperty("action") CmAction action,
            @JsonProperty("expected") JsonNode[] expected) {
        this.type = type;
        this.line = line;
        this.filename = filename;
        this.name = name;
        this.moduleType = moduleType;
        this.text = text;
        this.action = action;
        this.expected = expected;
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

    public JsonNode[] expected() {
        return expected;
    }
}
