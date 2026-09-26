package run.endive.cm.bindgen;

import java.util.List;
import java.util.Objects;

/**
 * An interface a world imports, under the name the import declares.
 *
 * <p>An interface written inline in the world is named by the world, such as {@code my-custom-host}.
 * One written elsewhere is named by its fully qualified id, such as {@code example:imports/types},
 * and the Java name comes from the last segment either way.
 */
final class WitInterface {

    private final String name;
    private final List<WitFunction> functions;
    private final List<WitResource> resources;
    private final List<WitType> types;
    private final WitScope scope;

    WitInterface(
            String name,
            List<WitFunction> functions,
            List<WitResource> resources,
            List<WitType> types,
            WitScope scope) {
        this.name = Objects.requireNonNull(name, "name");
        this.functions = List.copyOf(functions);
        this.resources = List.copyOf(resources);
        this.types = List.copyOf(types);
        this.scope = scope;
    }

    /** The name the world imports this under, which is the key the linker matches. */
    String name() {
        return name;
    }

    /** The functions belonging to the interface itself, rather than to one of its resources. */
    List<WitFunction> functions() {
        return functions;
    }

    List<WitResource> resources() {
        return resources;
    }

    /** The types the interface declares, in the order its index space holds them. */
    List<WitType> types() {
        return types;
    }

    /** The index space against which this interface's value types resolve. */
    WitScope scope() {
        return scope;
    }

    /** The interface's own name, with any package qualification dropped. */
    String simpleName() {
        int slash = name.lastIndexOf('/');
        return slash < 0 ? name : name.substring(slash + 1);
    }

    /** The Java member name, including the version when the interface has one. */
    String javaName() {
        return Names.versionedMember(simpleName());
    }

    /**
     * The Java package this interface's types are generated into, mirroring the WIT id.
     *
     * <p>An interface named by its id contributes a segment per part of that id, and one written
     * inline in a world has no id to contribute, so it sits directly under the base. Exports go
     * under {@code exports}, which is what lets a world import and export one name at once.
     *
     * @param base the package the annotation was found in
     */
    String javaPackage(String base, boolean exported) {
        StringBuilder result = new StringBuilder(base);
        if (exported) {
            append(result, "exports");
        }
        for (String segment : idSegments()) {
            append(result, Names.packageSegment(segment));
        }
        return result.toString();
    }

    /** {@code example:imported-resources/logging} names three segments, an inline name only one. */
    private List<String> idSegments() {
        int slash = name.indexOf('/');
        if (slash < 0) {
            return List.of(name);
        }
        String qualifier = name.substring(0, slash);
        int colon = qualifier.indexOf(':');
        if (colon < 0) {
            return List.of(qualifier, name.substring(slash + 1));
        }
        return List.of(
                qualifier.substring(0, colon),
                qualifier.substring(colon + 1),
                name.substring(slash + 1));
    }

    private static void append(StringBuilder target, String segment) {
        if (target.length() > 0) {
            target.append('.');
        }
        target.append(segment);
    }
}
