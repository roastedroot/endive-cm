package run.endive.cm.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import run.endive.cm.abi.VariantValue;

/**
 * The result class generated bindings hand to an embedder, and the variant carrying a result
 * across the Canonical ABI.
 */
public class ResultTests {

    @Test
    public void aResultIsCarriedAsAVariantLabelledByItsCase() {
        assertEquals(VariantValue.of("ok", 2L), Result.ok(2L).toComponent());
        assertEquals(VariantValue.of("error", "bad"), Result.err("bad").toComponent());
    }

    @Test
    public void aCaseWithoutAPayloadCarriesNull() {
        assertEquals(VariantValue.of("ok", null), Result.<Void, Void>ok(null).toComponent());
        assertEquals(VariantValue.of("error", null), Result.<Void, Void>err(null).toComponent());
    }

    @Test
    public void aLiftedResultIsCastToTheDeclaredClasses() {
        Result<Long, String> ok =
                Result.fromComponent(VariantValue.of("ok", 2L), Long.class, String.class);
        Result<Long, String> err =
                Result.fromComponent(VariantValue.of("error", "bad"), Long.class, String.class);

        assertTrue(ok.isOk());
        assertEquals(2L, ok.value());
        assertFalse(err.isOk());
        assertEquals("bad", err.error());
    }

    @Test
    public void onlyThePayloadOfTheCaseIsConverted() {
        Result<Long, String> lifted =
                Result.fromComponent(
                        VariantValue.of("ok", 2L),
                        payload -> (Long) payload + 1,
                        payload -> {
                            throw new AssertionError("the error case was converted");
                        });

        assertEquals(Result.ok(3L), lifted);
        assertEquals(
                VariantValue.of("error", "BAD"),
                Result.<Long, String>err("bad")
                        .toComponent(
                                payload -> {
                                    throw new AssertionError("the ok case was converted");
                                },
                                String::toUpperCase));
    }

    @Test
    public void aPayloadOfAnotherClassIsRefused() {
        assertThrows(
                ClassCastException.class,
                () -> Result.fromComponent(VariantValue.of("ok", "two"), Long.class, Void.class));
    }

    @Test
    public void anUnknownCaseIsRefused() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Result.fromComponent(VariantValue.of("some", 2L), Long.class, Void.class));
    }

    @Test
    public void thePayloadOfTheOtherCaseIsAbsent() {
        Result<Void, Void> ok = Result.ok(null);
        Result<Void, Void> err = Result.err(null);

        assertNull(ok.value());
        assertNull(err.error());
        assertThrows(NoSuchElementException.class, ok::error);
        assertThrows(NoSuchElementException.class, err::value);
    }

    @Test
    public void resultsCompareByTheirCaseAndPayload() {
        assertEquals(Result.ok(2L), Result.ok(2L));
        assertEquals(Result.ok(2L).hashCode(), Result.ok(2L).hashCode());
        assertNotEquals(Result.ok(2L), Result.ok(3L));
        assertNotEquals(Result.<Void, Void>ok(null), Result.<Void, Void>err(null));
        assertEquals("ok(2)", Result.ok(2L).toString());
        assertEquals("err(bad)", Result.err("bad").toString());
    }
}
