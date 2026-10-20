package run.endive.cm.runtime;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decides which versions of an interface name are compatible with each other, so that a host may
 * supply an interface at a version other than the one a component imports.
 *
 * <p>A pre-release, or a version below 0.1.0, is compatible only with itself. This matches {@code
 * alternate_lookup_key} in Wasmtime and is stricter than the canonical form the Explainer describes.
 *
 * @see <a href="https://github.com/WebAssembly/component-model/blob/706074c96bc14cfc58469e1bdc452bb4d91921c7/design/mvp/Explainer.md#-canonical-interface-name">Explainer.md, canonical interface name</a>
 */
final class InterfaceVersions {

    private static final String IDENTIFIERS = "[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*";

    /** A Semantic Versioning 2.0 version. A pre-release is checked loosely since it has no key. */
    private static final Pattern SEMVER =
            Pattern.compile(
                    "(?<major>0|[1-9][0-9]*)\\.(?<minor>0|[1-9][0-9]*)\\.(?<patch>0|[1-9][0-9]*)"
                            + "(?<prerelease>-"
                            + IDENTIFIERS
                            + ")?(?:\\+"
                            + IDENTIFIERS
                            + ")?");

    private static final Comparator<Version> PRECEDENCE =
            Comparator.comparing((Version version) -> version.major)
                    .thenComparing(version -> version.minor)
                    .thenComparing(version -> version.patch);

    private InterfaceVersions() {}

    /**
     * The name that {@code name} shares with every compatible version of itself, such as {@code
     * a:b/c@1} for {@code a:b/c@1.2.3} and {@code a:b/c@0.2} for {@code a:b/c@0.2.6}.
     *
     * @return the key, or {@code null} when {@code name} has no version, a pre-release, a version
     *     below 0.1.0, or a version that is not valid
     */
    static String compatibleKey(String name) {
        Version version = Version.parse(name);
        return version == null ? null : version.key;
    }

    /**
     * The name in {@code provided} that satisfies {@code requested}. That is {@code requested}
     * itself when present, and otherwise the highest version sharing its {@link #compatibleKey}.
     *
     * @return the chosen name, or {@code null} when none qualifies
     * @throws LinkageException when the highest compatible versions differ only in build metadata
     */
    static String resolve(String requested, Collection<String> provided) {
        if (provided.contains(requested)) {
            return requested;
        }
        String key = compatibleKey(requested);
        if (key == null) {
            return null;
        }
        Version best = null;
        Version tied = null;
        for (String name : provided) {
            Version candidate = Version.parse(name);
            if (candidate == null || !candidate.key.equals(key)) {
                continue;
            }
            int order = best == null ? 1 : PRECEDENCE.compare(candidate, best);
            if (order > 0) {
                best = candidate;
                tied = null;
            } else if (order == 0) {
                tied = candidate;
            }
        }
        if (tied != null) {
            throw new LinkageException(
                    "Import \""
                            + requested
                            + "\" is ambiguous between \""
                            + best.name
                            + "\" and \""
                            + tied.name
                            + "\", which differ only in build metadata");
        }
        return best == null ? null : best.name;
    }

    /** A versioned name that has a compatible key. */
    private static final class Version {

        private final String name;
        private final String key;
        private final BigInteger major;
        private final BigInteger minor;
        private final BigInteger patch;

        private Version(
                String name, String key, BigInteger major, BigInteger minor, BigInteger patch) {
            this.name = name;
            this.key = key;
            this.major = major;
            this.minor = minor;
            this.patch = patch;
        }

        /** The version {@code name} carries, or {@code null} when it has no compatible key. */
        static Version parse(String name) {
            int at = name.indexOf('@');
            if (at < 0) {
                return null;
            }
            Matcher semver = SEMVER.matcher(name.substring(at + 1));
            if (!semver.matches() || semver.group("prerelease") != null) {
                return null;
            }
            String major = semver.group("major");
            String minor = semver.group("minor");
            String track;
            if (!major.equals("0")) {
                track = major;
            } else if (!minor.equals("0")) {
                track = "0." + minor;
            } else {
                return null;
            }
            return new Version(
                    name,
                    name.substring(0, at + 1) + track,
                    new BigInteger(major),
                    new BigInteger(minor),
                    new BigInteger(semver.group("patch")));
        }
    }
}
