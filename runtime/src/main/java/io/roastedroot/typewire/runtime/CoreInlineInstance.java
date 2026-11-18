package io.roastedroot.typewire.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CoreInlineInstance extends CoreModuleInstance {

    private final Map<String, Object> exports = new LinkedHashMap<>();

    private CoreInlineInstance(Map<String, Object> exports) {
        this.exports.putAll(Collections.unmodifiableMap(exports));
    }

    Object getExport(String name) {
        if (!exports.containsKey(name)) {
            throw new LinkageException("Export '" + name + "' not found in inline module instance");
        }
        return exports.get(name);
    }

    Map<String, Object> getExports() {
        return exports;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final Map<String, Object> exports = new LinkedHashMap<>();

        public Builder addExport(String name, Object value) {
            exports.put(name, value);
            return this;
        }

        public CoreInlineInstance build() {
            return new CoreInlineInstance(exports);
        }
    }
}
