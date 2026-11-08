package io.github.stigjohnny.shipping.scenarios.support;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stigjohnny.shipping.scenarios.support.Comparison.Finding;
import io.github.stigjohnny.shipping.scenarios.support.Comparison.FindingType;
import io.github.stigjohnny.shipping.scenarios.support.Comparison.Status;
import io.github.stigjohnny.shipping.scenarios.support.Comparison.StepResult;
import io.github.stigjohnny.shipping.scenarios.support.Sidecar.Cardinality;
import io.github.stigjohnny.shipping.scenarios.support.Sidecar.Phase;
import io.github.stigjohnny.shipping.scenarios.support.Sidecar.Step;
import io.github.stigjohnny.shipping.scenarios.support.Sidecar.StepKind;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * What a report claims follows its verdict: the evaluated steps conformed, no declared step was verified, or the run
 * does not conform. The JSON and the Markdown carry the same sentence, and outcomes stay apart from it.
 */
class ScenarioReportTest {

    private static final Step STEP = new Step(1, "a-step", StepKind.USER_ACTION, "clerk", "web", Map.of(),
            Phase.SYNCHRONOUS, new Cardinality(1, 1), List.of(), "Does something");

    @Test
    void aRunThatEvaluatedNoStepSaysThatNoDeclaredStepWasVerified() {
        ScenarioReport report = report(Status.NOT_EVALUATED, List.of());

        assertThat(report.verdict()).isEqualTo("NO_STEP_EVALUATED");
        assertThat(report.claim()).isEqualTo("No declared step was verified: none of the 1 steps could be evaluated "
                + "in this run. It checked only that no interaction crossed a module boundary without being declared, "
                + "within the recorder's coverage.");
        assertThat(report.markdown()).contains("> " + report.claim() + "\n").doesNotContain("conformed");
        assertThat(report.json()).containsEntry("claim", report.claim());
    }

    @Test
    void aConformingRunSaysOnlyThatItsEvaluatedStepsConformed() {
        ScenarioReport report = report(Status.PASSED, List.of());

        assertThat(report.verdict()).isEqualTo("CONFORMS");
        assertThat(report.claim()).startsWith("In one exercised run, the 1 evaluated steps conformed to their "
                + "declarations, within the recorder's coverage.");
    }

    @Test
    void aFailingRunSaysThatItDoesNotConform() {
        ScenarioReport report = report(Status.MISSING_STEP, List.of(new Finding(FindingType.MISSING_STEP, "run",
                "a-step", 0, "expected 1, observed 0")));

        assertThat(report.verdict()).isEqualTo("DOES_NOT_CONFORM");
        assertThat(report.claim()).isEqualTo("This run does not conform to its declared steps: 1 build blockers, listed below. "
                + "Known coverage gaps remain refused separately.");
        assertThat(report.markdown()).doesNotContain("conformed to their declarations", "No declared step");
    }

    @Test
    void knownRefusalsStayInJsonAndNeverClaimConformanceEvenWithoutEvaluatedSteps() {
        for (Status status : List.of(Status.PASSED, Status.NOT_EVALUATED)) {
            String gap = "a refused access";
            List<Finding> findings = List.of(new Finding(FindingType.COVERAGE_GAP, "", "", 0, gap));
            CoverageLedger.Entry entry = new CoverageLedger.Entry("HTTP response/problem assembly",
                    "phase 3 / option 2", gap);
            ScenarioReport report = report(status, findings,
                    new Coverage.Result(List.of(), List.of(new Coverage.Check("census", false, gap)), List.of(gap),
                            Set.of(), true), new CoverageLedger.Assessment(List.of(entry), List.of()));
            assertThat(report.verdict()).isEqualTo("COVERAGE_INCOMPLETE");
            assertThat(report.claim()).contains("coverage incomplete: 1 known gaps", "No scenario conformance");
            assertThat(report.markdown()).contains("**Verdict: COVERAGE_INCOMPLETE**", "FAILED: census",
                    "**COVERAGE_GAP**", "phase 3 / option 2").doesNotContain("**Verdict: CONFORMS**");
            assertThat(Json.write(report.json())).contains("\"verified\": false", "\"knownGapCount\": 1",
                    "\"type\": \"COVERAGE_GAP\"", "\"fails\": true", "\"buildAcceptable\": true");
        }
    }

    private static ScenarioReport report(Status status, List<Finding> findings) {
        return report(status, findings, new Coverage.Result(List.of(), List.of(), List.of(), Set.of(), true),
                new CoverageLedger.Assessment(List.of(), List.of()));
    }

    private static ScenarioReport report(Status status, List<Finding> findings, Coverage.Result coverage,
            CoverageLedger.Assessment ledger) {
        Sidecar sidecar = new Sidecar("fixture.json", "ScenarioFixture", List.of(STEP));
        Comparison.Result comparison = new Comparison.Result(List.of(new StepResult(STEP, status, "", List.of())),
                findings, Map.of());
        ScenarioReport.Provenance provenance = new ScenarioReport.Provenance("revision", "model", "0", "fixture.json",
                "0", "0");
        return new ScenarioReport(sidecar, "Fixture", "ScenarioReportTest", "report", provenance,
                Observers.inProcess(), coverage, List.of(new Recorder.Stimulus("run", "a stimulus", "T1", 0, 0)),
                List.of(), comparison, List.of(), ledger);
    }
}
