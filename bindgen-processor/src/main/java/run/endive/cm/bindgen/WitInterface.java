package run.endive.cm.bindgen;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An interface a world imports, under the name the import declares.
 *
 * <p>An interface written inline in the world is named by the world, such as {@code my-custom-host}.
 * One written elsewhere is named by its fully qualified id, such as {@code example:imports/types},
 * and the Java name comes from the last segment either way, unless another interface the world
 * reaches the same way shares it.
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

    /**
     * Whether an embedder importing this interface has anything to implement. One declaring only
     * types, such as the types other interfaces {@code use}, gets no {@code Host}.
     */
    boolean needsHost() {
        if (!functions.isEmpty()) {
            return true;
        }
        for (WitResource resource : resources) {
            if (resource.constructor() != null || !resource.statics().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The name each of {@code interfaces} is reached by in the world's Java members. An interface
     * keeps its own name unless another shares it. Then each one sharing it gains its package, and
     * then its namespace, until the names differ. Versions never take part.
     */
    static Map<WitInterface, String> memberNames(List<WitInterface> interfaces) {
        Map<WitInterface, Integer> qualifiers = new IdentityHashMap<>();
        for (WitInterface iface : interfaces) {
            qualifiers.put(iface, 0);
        }
        List<List<WitInterface>> clashes = clashes(interfaces, qualifiers);
        while (!clashes.isEmpty()) {
            for (List<WitInterface> clash : clashes) {
                widen(clash, qualifiers);
            }
            clashes = clashes(interfaces, qualifiers);
        }
        Map<WitInterface, String> names = new IdentityHashMap<>();
        for (WitInterface iface : interfaces) {
            names.put(iface, iface.memberName(qualifiers.get(iface)));
        }
        return names;
    }

    private static List<List<WitInterface>> clashes(
            List<WitInterface> interfaces, Map<WitInterface, Integer> qualifiers) {
        Map<String, List<WitInterface>> byMember = new LinkedHashMap<>();
        for (WitInterface iface : interfaces) {
            String member = Names.member(iface.memberName(qualifiers.get(iface)));
            byMember.computeIfAbsent(member, m -> new ArrayList<>()).add(iface);
        }
        List<List<WitInterface>> clashes = new ArrayList<>();
        for (List<WitInterface> sharing : byMember.values()) {
            if (sharing.size() > 1) {
                clashes.add(sharing);
            }
        }
        return clashes;
    }

    private static void widen(List<WitInterface> clash, Map<WitInterface, Integer> qualifiers) {
        boolean widened = false;
        for (WitInterface iface : clash) {
            int current = qualifiers.get(iface);
            if (current < iface.idSegments().size() - 1) {
                qualifiers.put(iface, current + 1);
                widened = true;
            }
        }
        if (!widened) {
            throw new BindgenException(
                    "WIT interfaces \""
                            + clash.get(0).name()
                            + "\" and \""
                            + clash.get(1).name()
                            + "\" both generate Java member \""
                            + Names.member(clash.get(0).memberName(qualifiers.get(clash.get(0))))
                            + "\" and cannot be bound together");
        }
    }

    /** The interface name preceded by the last {@code qualifiers} segments of its id. */
    private String memberName(int qualifiers) {
        List<String> segments = idSegments();
        int from = Math.max(0, segments.size() - 1 - qualifiers);
        return Names.withoutVersion(String.join("-", segments.subList(from, segments.size())));
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
