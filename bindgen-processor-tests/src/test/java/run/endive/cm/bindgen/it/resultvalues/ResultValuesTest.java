package run.endive.cm.bindgen.it.resultvalues;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import run.endive.cm.bindgen.it.Components;
import run.endive.cm.bindgen.it.resultvalues.example.resultvalues.reporting.Host;
import run.endive.cm.bindgen.it.resultvalues.example.resultvalues.reporting.Level;
import run.endive.cm.bindgen.it.resultvalues.example.resultvalues.reporting.Outcome;
import run.endive.cm.bindgen.it.resultvalues.exports.example.resultvalues.checking.Guest;
import run.endive.cm.runtime.Bindgen;
import run.endive.cm.runtime.ComponentStore;
import run.endive.cm.runtime.Result;
import run.endive.cm.types.WasmComponent;
import run.endive.runtime.TrapException;

/**
 * A result reached as a value rather than as a function's own result, which crosses as a {@link
 * Result} in both call directions.
 *
 * <p>The guest's {@code run} traps unless the host answers {@code reverse} with the level it was
 * handed swapped, so a pass shows a nested result crossed both ways.
 */
@Bindgen(world = "result-values")
public class ResultValuesTest {

    private static WasmComponent component;

    @BeforeAll
    static void buildComponent() {
        component =
                Components.build(
                        Components.bytes("/result-values.wat"),
                        Components.text("/wit/result-values.wit"),
                        "result-values");
    }

    @Test
    void aResultWithPayloadsReachesTheHost() {
        Recorder recorder = new Recorder();

        instantiate(recorder).run();

        assertEquals(List.of(Result.ok(7L), Result.err("bad")), recorder.reports);
    }

    @Test
    void aResultWithoutPayloadsReachesTheHost() {
        Recorder recorder = new Recorder();

        instantiate(recorder).run();

        assertEquals(List.of(Result.ok(null), Result.err(null)), recorder.exits);
    }

    @Test
    void aResultInARecordReachesTheHost() {
        Recorder recorder = new Recorder();

        instantiate(recorder).run();

        assertEquals(List.of(new Outcome("flip", Result.err(Level.LOW))), recorder.outcomes);
    }

    /** The guest checks the record the host answers, so a wrong level traps. */
    @Test
    void aResultInARecordTheGuestDoesNotExpectTraps() {
        ResultValuesWorld bindings =
                instantiate(
                        new Recorder() {
                            @Override
                            public Outcome reverse(Outcome outcome) {
                                return outcome;
                            }
                        });

        assertThrows(TrapException.class, bindings::run);
    }

    @Test
    void aResultWithPayloadsReachesTheGuest() {
        Guest checking = instantiate(new Recorder()).checking();

        assertEquals(42L, checking.check(Result.ok(42L)));
        assertEquals(1004L, checking.check(Result.err("boom")));
    }

    @Test
    void aResultWithoutPayloadsReachesTheGuest() {
        Guest checking = instantiate(new Recorder()).checking();

        assertEquals(0L, checking.checkBare(Result.ok(null)));
        assertEquals(1L, checking.checkBare(Result.err(null)));
    }

    private static ResultValuesWorld instantiate(Host reporting) {
        return ResultValuesWorld.instantiate(new ComponentStore(), component, () -> reporting);
    }

    /** The host side of {@code example:result-values/reporting}. */
    private static class Recorder implements Host {

        private final List<Result<Long, String>> reports = new ArrayList<>();
        private final List<Result<Void, Void>> exits = new ArrayList<>();
        private final List<Outcome> outcomes = new ArrayList<>();

        @Override
        public void report(Result<Long, String> status) {
            reports.add(status);
        }

        @Override
        public void exit(Result<Void, Void> status) {
            exits.add(status);
        }

        @Override
        public Outcome reverse(Outcome outcome) {
            outcomes.add(outcome);
            Result<Void, Level> status = outcome.status();
            if (status.isOk()) {
                return outcome;
            }
            Level swapped = status.error() == Level.LOW ? Level.HIGH : Level.LOW;
            return new Outcome(outcome.name(), Result.err(swapped));
        }
    }
}
