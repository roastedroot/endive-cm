package io.roastedroot.typewire.bindgen;

import java.util.List;
import java.util.Objects;

/** A world's imports and exports, as much of one as the generator can yet read. */
final class WitWorld {

    private final String name;
    private final String qualifiedName;
    private final List<WitFunction> imports;
    private final List<WitInterface> importedInterfaces;
    private final List<WitFunction> exports;
    private final List<WitInterface> exportedInterfaces;

    WitWorld(
            String name,
            String qualifiedName,
            List<WitFunction> imports,
            List<WitInterface> importedInterfaces,
            List<WitFunction> exports,
            List<WitInterface> exportedInterfaces) {
        this.name = Objects.requireNonNull(name, "name");
        this.qualifiedName = Objects.requireNonNull(qualifiedName, "qualifiedName");
        this.imports = List.copyOf(imports);
        this.importedInterfaces = List.copyOf(importedInterfaces);
        this.exports = List.copyOf(exports);
        this.exportedInterfaces = List.copyOf(exportedInterfaces);
    }

    /** The world's WIT name, such as {@code hello-world}. */
    String name() {
        return name;
    }

    /** The world's fully qualified id, such as {@code my:project/hello-world}. */
    String qualifiedName() {
        return qualifiedName;
    }

    /** The functions the world imports in its own right, rather than through an interface. */
    List<WitFunction> imports() {
        return imports;
    }

    List<WitInterface> importedInterfaces() {
        return importedInterfaces;
    }

    /** The functions the world exports in its own right, rather than through an interface. */
    List<WitFunction> exports() {
        return exports;
    }

    List<WitInterface> exportedInterfaces() {
        return exportedInterfaces;
    }
}
