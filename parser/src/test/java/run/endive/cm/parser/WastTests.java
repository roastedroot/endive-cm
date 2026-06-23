package run.endive.cm.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import run.endive.cm.tools.ComponentValidateException;
import run.endive.cm.types.WasmComponent;

public final class WastTests implements AutoCloseable {

    private final WastTestFile wastTestFile;

    private final FileSystem wastFS;

    public WastTests(String wastJson, FileSystem wastFS) {
        var objectMapper = new ObjectMapper();
        try {
            this.wastTestFile = objectMapper.readValue(wastJson, WastTestFile.class);
            this.wastFS = wastFS;
        } catch (JsonProcessingException e) {
            throw new JsonFromWastException("failed to deserialize wast json", e);
        }
    }

    public void runTests(
            Map<Integer, WasmComponent> expectedComponents, boolean parseOnly, int[] skipCases) {
        var workingDir = wastFS.getPath("/work");
        for (var command : wastTestFile.commands()) {
            command.execute(workingDir, expectedComponents, parseOnly, skipCases);
        }
    }

    @Override
    public void close() throws Exception {
        this.wastFS.close();
    }

    static final class WastTestFile {
        private final String sourceFilename;
        private final List<Command> commands;

        WastTestFile(
                @JsonProperty("source_filename") String sourceFilename,
                @JsonProperty("commands") List<Command> commands) {
            this.sourceFilename = sourceFilename;
            this.commands = commands;
        }

        String sourceFilename() {
            return sourceFilename;
        }

        List<Command> commands() {
            return commands;
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = Module.class, name = "module"),
        @JsonSubTypes.Type(value = AssertMalformed.class, name = "assert_malformed"),
        @JsonSubTypes.Type(value = AssertInvalid.class, name = "assert_invalid")
    })
    interface Command {

        void execute(
                Path location,
                Map<Integer, WasmComponent> expectedComponents,
                boolean parseOnly,
                int... skipCases)
                throws CommandException;

        default WasmComponent parseComponent(Path location, String filename)
                throws CommandException {
            var filePath = location.resolve(filename);
            try (var in = new ByteArrayInputStream(Files.readAllBytes(filePath))) {
                try {
                    var parser = ComponentParser.builder().build();
                    return parser.parse(() -> in);
                } catch (UnsupportedOperationException e) {
                    throw new CommandException(filePath, e);
                }
            } catch (IOException e) {
                throw new CommandException(filePath, e);
            }
        }
    }

    public static class CommandException extends RuntimeException {

        private final byte[] testContents;

        public CommandException(Path filePath, Throwable cause) {
            super(cause);
            this.testContents = readFileContents(filePath);
        }

        public CommandException(Path filePath, String message) {
            super(message);
            this.testContents = readFileContents(filePath);
        }

        public CommandException(Path filePath, String message, Throwable cause) {
            super(message, cause);
            this.testContents = readFileContents(filePath);
        }

        private byte[] readFileContents(Path location) {
            try {
                return Files.readAllBytes(location);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read test contents from " + location, e);
            }
        }

        public byte[] testContents() {
            return testContents;
        }
    }

    static final class AssertInvalid implements Command {
        private final int line;
        private final String filename;
        private final String moduleType;
        private final String text;

        AssertInvalid(
                @JsonProperty("line") int line,
                @JsonProperty("filename") String filename,
                @JsonProperty("module_type") String moduleType,
                @JsonProperty("text") String text) {
            this.line = line;
            this.filename = filename;
            this.moduleType = moduleType;
            this.text = text;
        }

        int line() {
            return line;
        }

        String filename() {
            return filename;
        }

        String moduleType() {
            return moduleType;
        }

        String text() {
            return text;
        }

        @Override
        public void execute(
                Path location,
                Map<Integer, WasmComponent> expectedComponents,
                boolean parseOnly,
                int... skipCases)
                throws CommandException {
            var testId = Integer.parseInt(filename.split("\\.")[1]);
            if (skipCases.length > 0) {
                if (IntStream.of(skipCases).anyMatch(i -> i == testId)) {
                    return;
                }
            }
            try {
                parseComponent(location, filename);
            } catch (Throwable e) {
                if (!(e instanceof ComponentValidateException)) {
                    throw new CommandException(
                            location.resolve(filename),
                            String.format(
                                    "Expected validation of %s to fail at line %d due to '%s' but"
                                            + " got unexpected exception of type %s",
                                    filename, line, text, e.getClass().getSimpleName()),
                            e);
                }
                return;
            }
            throw new CommandException(
                    location.resolve(filename),
                    String.format(
                            "\"Expected validation of %s to fail at line %d due to '%s' ",
                            filename, line, text));
        }
    }

    static final class AssertMalformed implements Command {
        private final int line;
        private final String filename;
        private final String moduleType;
        private final String text;

        AssertMalformed(
                @JsonProperty("line") int line,
                @JsonProperty("filename") String filename,
                @JsonProperty("module_type") String moduleType,
                @JsonProperty("text") String text) {
            this.line = line;
            this.filename = filename;
            this.moduleType = moduleType;
            this.text = text;
        }

        int line() {
            return line;
        }

        String filename() {
            return filename;
        }

        String moduleType() {
            return moduleType;
        }

        String text() {
            return text;
        }

        @Override
        public void execute(
                Path location,
                Map<Integer, WasmComponent> expectedComponents,
                boolean parseOnly,
                int... skipCases)
                throws CommandException {
            var testId = Integer.parseInt(filename.split("\\.")[1]);
            if (skipCases.length > 0) {
                if (IntStream.of(skipCases).anyMatch(i -> i == testId)) {
                    return;
                }
            }
            try {
                parseComponent(location, filename);
            } catch (Throwable e) {
                if (!(e instanceof ComponentValidateException)) {
                    throw new CommandException(
                            location.resolve(filename),
                            String.format(
                                    "Expected validation of %s to fail at line %d due to '%s' but"
                                            + " got unexpected exception of type %s",
                                    filename, line, text, e.getClass().getSimpleName()),
                            e);
                }
                return;
            }
            throw new CommandException(
                    location.resolve(filename),
                    String.format(
                            "\"Expected validation of %s to fail at line %d due to '%s' ",
                            filename, line, text));
        }
    }

    static final class Action {
        private final String type;
        private final String field;
        private final List<TypedValue> args;

        Action(
                @JsonProperty("type") String type,
                @JsonProperty("field") String field,
                @JsonProperty("args") List<TypedValue> args) {
            this.type = type;
            this.field = field;
            this.args = args;
        }

        String type() {
            return type;
        }

        String field() {
            return field;
        }

        List<TypedValue> args() {
            return args;
        }
    }

    static final class TypedValue {
        private final String type;
        private final String value;

        TypedValue(@JsonProperty("type") String type, @JsonProperty("value") String value) {
            this.type = type;
            this.value = value;
        }

        String type() {
            return type;
        }

        String value() {
            return value;
        }

        Object converted() {
            switch (type) {
                case "bool":
                    return Boolean.parseBoolean(value);
                case "s8":
                    return Byte.parseByte(value);
                case "u8":
                case "s16":
                    return Short.parseShort(value);
                case "u16":
                case "s32":
                case "i32":
                    return Integer.parseInt(value);
                case "u32":
                case "s64":
                case "i64":
                    return Long.parseLong(value);
                case "u64":
                    return BigInteger.valueOf(Long.parseLong(value));
                case "f32":
                    return Float.parseFloat(value);
                case "f64":
                    return Double.parseDouble(value);
                default:
                    return value;
            }
        }
    }

    static final class Module implements Command {
        private final int line;
        private final String name;
        private final String filename;
        private final String moduleType;

        Module(
                @JsonProperty("line") int line,
                @JsonProperty("name") String name,
                @JsonProperty("filename") String filename,
                @JsonProperty("module_type") String moduleType) {
            this.line = line;
            this.name = name;
            this.filename = filename;
            this.moduleType = moduleType;
        }

        int line() {
            return line;
        }

        String name() {
            return name;
        }

        String filename() {
            return filename;
        }

        String moduleType() {
            return moduleType;
        }

        @Override
        public void execute(
                Path location,
                Map<Integer, WasmComponent> expectedComponents,
                boolean parseOnly,
                int... skipCases)
                throws CommandException {
            var testId = Integer.parseInt(filename.split("\\.")[1]);
            if (skipCases.length > 0) {
                if (IntStream.of(skipCases).anyMatch(i -> i == testId)) {
                    return;
                }
            }
            try {
                WasmComponent actualComponent = parseComponent(location, filename);
                if (expectedComponents.containsKey(testId)) {
                    WasmComponent expectedComponent = expectedComponents.get(testId);
                    assertThat(actualComponent)
                            .usingRecursiveComparison()
                            .ignoringFields("customSections")
                            .isEqualTo(expectedComponent);
                }
                if (!parseOnly) {
                    // TODO Include component instantiation here once implemented
                    throw new UnsupportedOperationException(
                            "Component instantiation not yet implemented");
                }
            } catch (Throwable e) {
                throw new CommandException(
                        location.resolve(filename),
                        String.format(
                                "Failed to load module %s due to error at line %d", filename, line),
                        e);
            }
        }
    }
}
