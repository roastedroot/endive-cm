package run.endive.cm.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ComponentInstance {

    private final Map<String, Object> exports;

    ComponentInstance(Map<String, Object> exports) {
        this.exports = Collections.unmodifiableMap(new LinkedHashMap<>(exports));
    }

    public ComponentFunction export(String name) {
        Object value = exports.get(name);
        if (value == null) {
            throw new LinkageException("No export named \"" + name + "\"");
        }
        if (!(value instanceof ComponentFunction)) {
            throw new LinkageException(
                    "Export \""
                            + name
                            + "\" is not a function (got "
                            + value.getClass().getName()
                            + ")");
        }
        return (ComponentFunction) value;
    }

    public Map<String, Object> exports() {
        return exports;
    }
}
