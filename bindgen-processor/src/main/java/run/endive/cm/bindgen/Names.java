package run.endive.cm.bindgen;

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
     * A WIT name as a Java package segment, which Google's style says is lowercase letters and
     * digits with consecutive words run together. A segment Java reserves gains a trailing
     * underscore, since there is no lowercase-only spelling that would not be a keyword.
     */
    static String packageSegment(String witName) {
        StringBuilder result = new StringBuilder(witName.length());
        for (int i = 0; i < witName.length(); i++) {
            char c = witName.charAt(i);
            if (c != '-') {
                result.append(Character.toLowerCase(c));
            }
        }
        String segment = result.toString();
        return RESERVED.contains(segment) ? segment + "_" : segment;
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
