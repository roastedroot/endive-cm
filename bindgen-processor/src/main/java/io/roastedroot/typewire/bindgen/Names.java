package io.roastedroot.typewire.bindgen;

import java.util.Set;

/**
 * Turns WIT names into Java ones. WIT names are kebab-case by construction, so a word boundary is
 * always a hyphen.
 *
 * @see <a href="https://github.com/WebAssembly/component-model/blob/706074c96bc14cfc58469e1bdc452bb4d91921c7/design/mvp/Explainer.md#import-and-export-definitions">Explainer.md, kebab names</a>
 */
final class Names {

    private Names() {}

    /**
     * What Java will not accept as an identifier. WIT reserves some of these itself, but not all,
     * and a WIT name may escape a WIT keyword with {@code %}, so any of them can reach here.
     */
    private static final Set<String> RESERVED =
            Set.of(
                    "abstract",
                    "assert",
                    "boolean",
                    "break",
                    "byte",
                    "case",
                    "catch",
                    "char",
                    "class",
                    "const",
                    "continue",
                    "default",
                    "do",
                    "double",
                    "else",
                    "enum",
                    "extends",
                    "final",
                    "finally",
                    "float",
                    "for",
                    "goto",
                    "if",
                    "implements",
                    "import",
                    "instanceof",
                    "int",
                    "interface",
                    "long",
                    "native",
                    "new",
                    "package",
                    "private",
                    "protected",
                    "public",
                    "return",
                    "short",
                    "static",
                    "strictfp",
                    "super",
                    "switch",
                    "synchronized",
                    "this",
                    "throw",
                    "throws",
                    "transient",
                    "try",
                    "void",
                    "volatile",
                    "while",
                    // Not keywords, but reserved literals, which an identifier may not be either.
                    "true",
                    "false",
                    "null");

    /** {@code hello-world} becomes {@code HelloWorld}. */
    static String type(String witName) {
        return join(witName, true);
    }

    /**
     * {@code host-log} becomes {@code hostLog}, and a name Java reserves gains a trailing
     * underscore, since {@code new} is a WIT name but not a Java one.
     */
    static String member(String witName) {
        String name = join(witName, false);
        return RESERVED.contains(name) ? name + "_" : name;
    }

    /**
     * A member named after the item owning it, such as a resource's static function, so that two
     * resources may each declare one of the same name.
     */
    static String qualifiedMember(String owner, String witName) {
        return member(owner) + type(witName);
    }

    /**
     * {@code hello-world} becomes {@code HelloWorldWorld}. The suffix keeps a world clear of the
     * {@code Imports} interface its class holds, since WASI names a world {@code imports}.
     */
    static String worldClass(String world) {
        return type(world) + "World";
    }

    /** The method on an interface's {@code Handles} minting an owned handle to {@code resource}. */
    static String ownHandle(String resource) {
        return "own" + type(resource);
    }

    /** The method on an interface's {@code Handles} finding what a handle to it stands for. */
    static String handleGetter(String resource) {
        return "get" + type(resource);
    }

    /** The method on an interface's {@code Handles} taking ownership of what a handle stands for. */
    static String handleTaker(String resource) {
        return "take" + type(resource);
    }

    /** The method on an interface's {@code Handles} giving the resource type of {@code resource}. */
    static String resourceTypeGetter(String resource) {
        return join(resource, false) + "ResourceType";
    }

    /**
     * A generated name kept clear of {@code taken}, which holds the Java names a WIT declaration
     * has already put in scope. Generated code introduces locals and lambda parameters of its own,
     * and a WIT name is free to be any of them.
     */
    static String free(String preferred, Set<String> taken) {
        String name = preferred;
        while (taken.contains(name)) {
            name = name + "_";
        }
        return name;
    }

    /**
     * A WIT name as a Java package segment, without its version.
     *
     * <p>A Java reserved word gains a trailing underscore, so {@code class} becomes {@code class_}.
     */
    static String packageSegment(String witName) {
        String name = withoutVersion(witName);
        StringBuilder result = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c != '-') {
                result.append(Character.toLowerCase(c));
            }
        }

        String segment = result.toString();
        return RESERVED.contains(segment) ? segment + "_" : segment;
    }

    /** Leaves the WIT version out of names used by Java source. */
    static String withoutVersion(String witName) {
        int at = witName.indexOf('@');
        return at < 0 ? witName : witName.substring(0, at);
    }

    /** The unqualified, unversioned name of a WIT interface. */
    static String simpleName(String witName) {
        int slash = witName.lastIndexOf('/');
        return withoutVersion(slash < 0 ? witName : witName.substring(slash + 1));
    }

    private static String join(String witName, boolean leadingCapital) {
        StringBuilder result = new StringBuilder(witName.length());
        boolean capitalize = leadingCapital;
        for (int i = 0; i < witName.length(); i++) {
            char c = witName.charAt(i);
            if (c == '-') {
                capitalize = true;
            } else if (capitalize) {
                result.append(Character.toUpperCase(c));
                capitalize = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
