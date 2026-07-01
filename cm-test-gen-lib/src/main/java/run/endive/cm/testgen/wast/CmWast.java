package run.endive.cm.testgen.wast;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public final class CmWast {
    private final String sourceFilename;
    private final List<CmCommand> commands;

    public CmWast(
            @JsonProperty("source_filename") String sourceFilename,
            @JsonProperty("commands") List<CmCommand> commands) {
        this.sourceFilename = sourceFilename;
        this.commands = commands;
    }

    public String sourceFilename() {
        return sourceFilename;
    }

    public List<CmCommand> commands() {
        return commands;
    }
}
