package run.endive.cm.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Which versions of an interface name a host may supply in place of the one imported.
 *
 * @see <a href="https://github.com/WebAssembly/component-model/blob/706074c96bc14cfc58469e1bdc452bb4d91921c7/design/mvp/Explainer.md#-canonical-interface-name">Explainer.md, canonical interface name</a>
 */
public class InterfaceVersionsTests {

    /** A version from 1.0.0 on keeps its major, and one below keeps its minor. */
    @ParameterizedTest
    @CsvSource({
        "a:b/c@1.0.0, a:b/c@1",
        "a:b/c@1.1.2, a:b/c@1",
        "a:b/c@2.1.2, a:b/c@2",
        "a:b/c@0.1.0, a:b/c@0.1",
        "a:b/c@0.2.6, a:b/c@0.2",
        "a:b/c@0.2.12, a:b/c@0.2",
        "a-b:c-d/e-f@123456.7890.488, a-b:c-d/e-f@123456"
    })
    public void aVersionIsCutToItsCompatibleTrack(String name, String key) {
        assertEquals(key, InterfaceVersions.compatibleKey(name));
    }

    @ParameterizedTest
    @CsvSource({"a:b/c@2.1.2+abc, a:b/c@2", "a:b/c@0.2.3+abc.1-x, a:b/c@0.2"})
    public void buildMetadataIsIgnored(String name, String key) {
        assertEquals(key, InterfaceVersions.compatibleKey(name));
    }

    /** A 0.0.x release promises no compatibility, so it matches only itself. */
    @ParameterizedTest
    @ValueSource(strings = {"a:b/c@0.0.0", "a:b/c@0.0.3", "a:b/c@0.0.3+abc"})
    public void aZeroMajorAndMinorHasNoKey(String name) {
        assertNull(InterfaceVersions.compatibleKey(name));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"a:b/c@1.0.0-pre", "a:b/c@0.2.0-rc.1", "a:b/c@0.0.1-pre", "a:b/c@1.0.0-a+b"})
    public void aPreReleaseHasNoKey(String name) {
        assertNull(InterfaceVersions.compatibleKey(name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"x", "a:b/c", "[method]r.f"})
    public void anUnversionedNameHasNoKey(String name) {
        assertNull(InterfaceVersions.compatibleKey(name));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "a:b/c@",
                "a:b/c@1",
                "a:b/c@0.2",
                "a:b/c@1.0",
                "a:b/c@1.0.0.0",
                "a:b/c@01.0.0",
                "a:b/c@1.02.0",
                "a:b/c@1.x.0",
                "a:b/c@1.0.0+",
                "a:b/c@1.0.0-",
                "a:b/c@1.0.0+a..b",
                "a:b/c@v1.0.0",
                "a:b/c@1.0.0 "
            })
    public void anInvalidVersionHasNoKey(String name) {
        assertNull(InterfaceVersions.compatibleKey(name));
    }

    @Test
    public void anExactNameIsPreferred() {
        assertEquals(
                "a:b/c@0.2.6",
                InterfaceVersions.resolve("a:b/c@0.2.6", List.of("a:b/c@0.2.12", "a:b/c@0.2.6")));
    }

    /** An exact name needs no version, so it matches even where no compatible key exists. */
    @Test
    public void anExactNameMatchesWithoutAKey() {
        assertEquals(
                "a:b/c@0.0.3", InterfaceVersions.resolve("a:b/c@0.0.3", List.of("a:b/c@0.0.3")));
        assertEquals("a:b/c", InterfaceVersions.resolve("a:b/c", List.of("a:b/c")));
    }

    @Test
    public void aNewerCompatibleVersionIsChosen() {
        assertEquals(
                "a:b/c@0.2.12", InterfaceVersions.resolve("a:b/c@0.2.6", List.of("a:b/c@0.2.12")));
    }

    @Test
    public void anOlderCompatibleVersionIsChosen() {
        assertEquals(
                "a:b/c@1.0.0", InterfaceVersions.resolve("a:b/c@1.4.0", List.of("a:b/c@1.0.0")));
    }

    /** Versions compare by number, so 0.2.12 is higher than 0.2.9 although it sorts lower as text. */
    @Test
    public void theHighestCompatibleVersionIsChosen() {
        assertEquals(
                "a:b/c@0.2.12",
                InterfaceVersions.resolve(
                        "a:b/c@0.2.0", List.of("a:b/c@0.2.9", "a:b/c@0.2.12", "a:b/c@0.2.10")));
        assertEquals(
                "a:b/c@1.10.0",
                InterfaceVersions.resolve(
                        "a:b/c@1.0.0", List.of("a:b/c@1.9.9", "a:b/c@1.10.0", "a:b/c@1.2.30")));
    }

    @Test
    public void numbersBeyondALongCompareByValue() {
        assertEquals(
                "a:b/c@0.18446744073709551616",
                InterfaceVersions.compatibleKey("a:b/c@0.18446744073709551616.0"));
        assertEquals(
                "a:b/c@1.18446744073709551616.0",
                InterfaceVersions.resolve(
                        "a:b/c@1.0.0",
                        List.of(
                                "a:b/c@1.18446744073709551616.0",
                                "a:b/c@1.9223372036854775807.0")));
    }

    @Test
    public void onlyTheRequestedInterfaceIsConsidered() {
        assertEquals(
                "a:b/c@0.2.1",
                InterfaceVersions.resolve(
                        "a:b/c@0.2.0", List.of("a:b/d@0.2.9", "a:b/c@0.2.1", "a:x/c@0.2.9")));
    }

    @Test
    public void anotherTrackIsRefused() {
        assertNull(InterfaceVersions.resolve("a:b/c@0.3.0", List.of("a:b/c@0.2.12")));
        assertNull(InterfaceVersions.resolve("a:b/c@0.2.12", List.of("a:b/c@0.3.0")));
        assertNull(InterfaceVersions.resolve("a:b/c@2.0.0", List.of("a:b/c@1.9.0")));
        assertNull(InterfaceVersions.resolve("a:b/c@1.0.0", List.of("a:b/c@0.1.0")));
    }

    @Test
    public void aZeroMajorAndMinorMatchesOnlyItself() {
        assertNull(InterfaceVersions.resolve("a:b/c@0.0.3", List.of("a:b/c@0.0.4")));
    }

    @Test
    public void aPreReleaseMatchesOnlyItself() {
        assertNull(InterfaceVersions.resolve("a:b/c@0.2.0-rc.1", List.of("a:b/c@0.2.0")));
        assertNull(InterfaceVersions.resolve("a:b/c@0.2.0", List.of("a:b/c@0.2.1-rc.1")));
    }

    @Test
    public void versionedAndUnversionedNamesNeverMatch() {
        assertNull(InterfaceVersions.resolve("a:b/c", List.of("a:b/c@0.2.0")));
        assertNull(InterfaceVersions.resolve("a:b/c@0.2.0", List.of("a:b/c")));
    }

    /** The key is only for matching, so a name spelled like one is not a candidate. */
    @Test
    public void aNameSpelledLikeAKeyIsNotACandidate() {
        assertNull(InterfaceVersions.resolve("a:b/c@0.2.6", List.of("a:b/c@0.2")));
    }

    @Test
    public void buildMetadataDoesNotPreventAMatch() {
        assertEquals(
                "a:b/c@1.2.3+abc",
                InterfaceVersions.resolve("a:b/c@1.0.0", List.of("a:b/c@1.2.3+abc")));
    }

    @Test
    public void candidatesDifferingOnlyInBuildMetadataAreAmbiguous() {
        var thrown =
                assertThrows(
                        LinkageException.class,
                        () ->
                                InterfaceVersions.resolve(
                                        "a:b/c@1.0.0",
                                        List.of("a:b/c@1.2.3+abc", "a:b/c@1.2.3+def")));
        assertTrue(
                thrown.getMessage().contains("a:b/c@1.0.0")
                        && thrown.getMessage().contains("a:b/c@1.2.3+abc")
                        && thrown.getMessage().contains("a:b/c@1.2.3+def"),
                "expected every name involved, got: " + thrown.getMessage());
        assertThrows(
                LinkageException.class,
                () ->
                        InterfaceVersions.resolve(
                                "a:b/c@1.0.0", List.of("a:b/c@1.2.3", "a:b/c@1.2.3+abc")));
    }

    /** Only a tie for the highest version leaves the choice open. */
    @Test
    public void aTieBelowTheHighestVersionIsNotAmbiguous() {
        assertEquals(
                "a:b/c@1.3.0",
                InterfaceVersions.resolve(
                        "a:b/c@1.0.0",
                        List.of("a:b/c@1.2.3+abc", "a:b/c@1.3.0", "a:b/c@1.2.3+def")));
    }

    @Test
    public void anExactNameIsNotAmbiguous() {
        assertEquals(
                "a:b/c@1.2.3+abc",
                InterfaceVersions.resolve(
                        "a:b/c@1.2.3+abc", List.of("a:b/c@1.2.3+abc", "a:b/c@1.2.3+def")));
    }
}
