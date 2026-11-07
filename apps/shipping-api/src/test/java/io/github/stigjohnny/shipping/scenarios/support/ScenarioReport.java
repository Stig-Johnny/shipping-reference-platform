package io.github.stigjohnny.shipping.scenarios.support;

import io.github.stigjohnny.shipping.scenarios.support.Comparison.Finding;
import io.github.stigjohnny.shipping.scenarios.support.Comparison.Status;
import io.github.stigjohnny.shipping.scenarios.support.Comparison.StepResult;
import io.github.stigjohnny.shipping.scenarios.support.Recorder.Interaction;
import io.github.stigjohnny.shipping.scenarios.support.Recorder.Stimulus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A scenario run's report: {@code <name>.json} (schema {@value #SCHEMA}) for tools such as the docs site, and
 * {@code <name>.md} for people. Both are UTF-8 with LF line ends, without timestamps, and hold no argument, return
 * value or payload: operations are types and methods. They are written before a test asserts anything.
 */
public final class ScenarioReport {

    public static final String SCHEMA = "shipping-scenarios/report/v1";

    /**
     * What a report says this run shows, and what it does not.
     */
    public static final String CLAIM = "One exercised run of this scenario conforms to its declared steps, within the "
            + "recorder's coverage. It does not cover all paths, schedules or inputs.";

    /** Where the report's inputs come from, as paths relative to the repository and their SHA-256 digests. */
    public record Provenance(String revision, String model, String modelSha256, String sidecar, String sidecarSha256,
            String mainClassesSha256) {
    }

    /** An outcome assertion, asserted separately from the interactions. */
    public record Outcome(String description, boolean held) {
    }

    private final Sidecar sidecar;
    private final String title;
    private final String testClass;
    private final String testMethod;
    private final Provenance provenance;
    private final Observers observers;
    private final Coverage.Result coverage;
    private final List<Stimulus> stimuli;
    private final List<Interaction> interactions;
    private final Comparison.Result result;
    private final List<Outcome> outcomes;

    ScenarioReport(Sidecar sidecar, String title, String testClass, String testMethod, Provenance provenance,
            Observers observers, Coverage.Result coverage, List<Stimulus> stimuli, List<Interaction> interactions,
            Comparison.Result result, List<Outcome> outcomes) {
        this.sidecar = sidecar;
        this.title = title;
        this.testClass = testClass;
        this.testMethod = testMethod;
        this.provenance = provenance;
        this.observers = observers;
        this.coverage = coverage;
        this.stimuli = List.copyOf(stimuli);
        this.interactions = List.copyOf(interactions);
        this.result = result;
        this.outcomes = List.copyOf(outcomes);
    }

    /** Writes {@code <name>.json} and {@code <name>.md} under the directory and returns the Markdown file. */
    Path write(Path directory, String name) {
        try {
            Path json = directory.resolve(name + ".json");
            Files.createDirectories(json.getParent());
            Files.writeString(json, Json.write(json()), StandardCharsets.UTF_8);
            Path markdown = directory.resolve(name + ".md");
            Files.writeString(markdown, markdown(), StandardCharsets.UTF_8);
            return markdown;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * DOES_NOT_CONFORM with a failing finding; NO_STEP_EVALUATED when nothing failed but no step could be evaluated,
     * so that only the absence of unexpected interactions was checked; CONFORMS otherwise.
     */
    String verdict() {
        if (!result.conforms()) {
            return "DOES_NOT_CONFORM";
        }
        return result.steps().stream().anyMatch(s -> s.status() == Status.PASSED) ? "CONFORMS" : "NO_STEP_EVALUATED";
    }

    private long count(Status status) {
        return result.steps().stream().filter(s -> s.status() == status).count();
    }

    Map<String, Object> json() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema", SCHEMA);
        root.put("verdict", verdict());
        root.put("outcomesHeld", outcomes.stream().allMatch(Outcome::held));
        root.put("claim", CLAIM);
        root.put("scenario", map("view", sidecar.view(), "title", title, "sidecar", provenance.sidecar(),
                "sidecarSha256", provenance.sidecarSha256()));
        root.put("test", map("class", testClass, "method", testMethod));
        root.put("provenance", map("revision", provenance.revision(), "model", provenance.model(), "modelSha256",
                provenance.modelSha256(), "mainClassesSha256", provenance.mainClassesSha256()));
        long notEvaluated = count(Status.NOT_EVALUATED);
        long gaps = count(Status.COVERAGE_GAP);
        root.put("summary", map("steps", sidecar.steps().size(), "evaluated",
                (int) (sidecar.steps().size() - notEvaluated - gaps), "passed", (int) count(Status.PASSED),
                "notEvaluated", (int) notEvaluated, "coverageGaps", (int) gaps,
                "failingFindings", result.failing().size(), "outcomes", outcomes.size(),
                "outcomesFailed", (int) outcomes.stream().filter(o -> !o.held()).count()));
        Map<String, Object> observed = new LinkedHashMap<>();
        observers.describe().forEach((kind, entry) -> observed.put(kind.name(), entry));
        root.put("observers", observed);
        root.put("coverage", map("verified", coverage.gaps().isEmpty(),
                "checks", coverage.checks().stream().map(c -> map("check", c.name(), "passed", c.passed(),
                        "detail", c.detail())).toList(),
                "gaps", coverage.gaps(),
                "callSites", coverage.callSites().stream().map(c -> map("from", c.from(), "to", c.to(), "target",
                        c.target(), "category", c.category(), "location", c.location())).toList()));
        root.put("stimuli", stimuli.stream().map(s -> map("run", s.run(), "description", s.description(),
                "thread", s.thread())).toList());
        root.put("steps", result.steps().stream().map(ScenarioReport::step).toList());
        root.put("interactions", interactions.stream().map(i -> map("seq", i.seq(), "run", i.run(), "thread",
                i.thread(), "kind", i.kind(), "from", i.from(), "to", i.to(), "operation", i.operation(),
                "causedBy", i.causedBy(), "caller", i.caller(), "step",
                result.matched().getOrDefault(i.seq(), ""))).toList());
        root.put("findings", result.findings().stream().map(f -> map("type", f.type(), "fails", f.type().fails(),
                "run", f.run(), "step", f.step(), "interaction", f.interaction(), "message", f.message())).toList());
        root.put("outcomes", outcomes.stream().map(o -> map("description", o.description(), "held", o.held()))
                .toList());
        return root;
    }

    private static Map<String, Object> step(StepResult result) {
        Sidecar.Step step = result.step();
        return map("order", step.order(), "id", step.id(), "kind", step.kind(), "from", step.from(), "to", step.to(),
                "operation", step.operationText(), "description", step.description(), "phase", step.phase(),
                "cardinality", step.cardinality().text(), "after", step.after(), "status", result.status(),
                "reason", result.reason(), "observed", result.observed());
    }

    String markdown() {
        StringBuilder out = new StringBuilder();
        out.append("# Scenario: ").append(title).append("\n\n").append("Dynamic view `").append(sidecar.view())
                .append("`, steps in `").append(provenance.sidecar()).append("`.\n\n");
        long evaluated = sidecar.steps().size() - count(Status.NOT_EVALUATED) - count(Status.COVERAGE_GAP);
        long held = outcomes.stream().filter(Outcome::held).count();
        out.append("**Verdict: ").append(verdict()).append("**. Steps evaluated: ").append(evaluated).append(" of ")
                .append(sidecar.steps().size()).append(", passed: ").append(count(Status.PASSED))
                .append(", not evaluated: ").append(count(Status.NOT_EVALUATED)).append(". Failing findings: ")
                .append(result.failing().size()).append(". Outcomes held: ").append(held).append(" of ")
                .append(outcomes.size()).append(".\n\n");
        out.append("> ").append(CLAIM).append(" A step that is not evaluated was neither checked nor assumed.\n\n");
        out.append("## Steps\n\n| # | Step | Kind | From -> To | Operation | Description | Status | Observed |\n")
                .append("|---|---|---|---|---|---|---|---|\n");
        for (StepResult step : result.steps()) {
            Sidecar.Step s = step.step();
            out.append("| ").append(s.order()).append(" | `").append(s.id()).append("` | ").append(s.kind())
                    .append(" | ").append(s.from()).append(" -> ").append(s.to()).append(" | ")
                    .append(code(s.operationText())).append(" | ").append(cell(s.description())).append(" | ")
                    .append(step.status()).append(" | ").append(String.join(", ", step.observed())).append(" |\n");
        }
        List<Finding> failing = result.failing();
        out.append("\n## Findings\n\n");
        if (failing.isEmpty()) {
            out.append("None that fail the scenario.\n");
        }
        for (Finding finding : failing) {
            out.append("- **").append(finding.type()).append("**")
                    .append(finding.run().isEmpty() ? "" : " in run " + finding.run())
                    .append(finding.step().isEmpty() ? "" : ", step `" + finding.step() + "`").append(": ")
                    .append(finding.message()).append('\n');
        }
        List<StepResult> skipped = result.steps().stream().filter(s -> s.status() == Status.NOT_EVALUATED).toList();
        out.append("\n## Not evaluated (").append(skipped.size()).append(")\n\n");
        if (skipped.isEmpty()) {
            out.append("Every step was evaluated.\n");
        }
        skipped.forEach(s -> out.append("- `").append(s.step().id()).append("`: ").append(s.reason()).append('\n'));
        out.append("\n## Observed interactions\n\n");
        stimuli.forEach(s -> out.append("- Stimulus `").append(s.run()).append("` on ").append(s.thread())
                .append(": ").append(s.description()).append('\n'));
        out.append("\n| Seq | Run | Thread | Kind | From -> To | Operation | Caused by | Step |\n")
                .append("|---|---|---|---|---|---|---|---|\n");
        for (Interaction i : interactions) {
            out.append("| ").append(i.seq()).append(" | ").append(i.run()).append(" | ").append(i.thread())
                    .append(" | ").append(i.kind()).append(" | ").append(i.from())
                    .append(i.to().isEmpty() ? "" : " -> " + i.to()).append(" | ").append(code(i.operation()))
                    .append(" | ").append(i.causedBy() == 0 ? "" : Integer.toString(i.causedBy())).append(" | ")
                    .append(result.matched().getOrDefault(i.seq(), "")).append(" |\n");
        }
        if (interactions.isEmpty()) {
            out.append("| | | | none | | | | |\n");
        }
        out.append("\n## Coverage\n\n");
        observers.describe().forEach((kind, entry) -> out.append("- ").append(kind).append(": ")
                .append(Boolean.TRUE.equals(entry.get("observed")) ? "observed, by " + entry.get("how")
                        : "not observed: " + entry.get("reason")).append('\n'));
        coverage.checks().forEach(c -> out.append("- ").append(c.passed() ? "Passed" : "FAILED").append(": ")
                .append(c.name()).append(" (").append(c.detail()).append(")\n"));
        coverage.gaps().forEach(g -> out.append("- Gap: ").append(g).append('\n'));
        out.append("\nCross-module call sites in the main classes:\n\n");
        if (coverage.callSites().isEmpty()) {
            out.append("- none\n");
        }
        coverage.callSites().forEach(c -> out.append("- ").append(c.from()).append(" -> ").append(c.to()).append(": `")
                .append(c.target()).append("`, ").append(c.category()).append(", at ").append(c.location())
                .append('\n'));
        out.append("\n## Outcomes\n\nAsserted separately from the interactions.\n\n");
        outcomes.forEach(o -> out.append("- ").append(o.held() ? "Held" : "DID NOT HOLD").append(": ")
                .append(o.description()).append('\n'));
        out.append("\n## Provenance\n\n- Test: `").append(testClass).append('#').append(testMethod).append("`\n")
                .append("- Revision: ").append(provenance.revision()).append('\n')
                .append("- Model: `").append(provenance.model()).append("`, SHA-256 ").append(provenance.modelSha256())
                .append('\n').append("- Sidecar: `").append(provenance.sidecar()).append("`, SHA-256 ")
                .append(provenance.sidecarSha256()).append('\n').append("- Main classes: SHA-256 ")
                .append(provenance.mainClassesSha256()).append('\n');
        return out.toString();
    }

    private static String code(String text) {
        return text.isEmpty() ? "" : "`" + text + "`";
    }

    private static String cell(String text) {
        return text.replace("|", "\\|").replace('\n', ' ');
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
