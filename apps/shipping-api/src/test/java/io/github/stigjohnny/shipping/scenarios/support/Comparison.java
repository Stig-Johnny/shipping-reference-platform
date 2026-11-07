package io.github.stigjohnny.shipping.scenarios.support;

import io.github.stigjohnny.shipping.scenarios.support.Recorder.Interaction;
import io.github.stigjohnny.shipping.scenarios.support.Recorder.Seen;
import io.github.stigjohnny.shipping.scenarios.support.Recorder.Stimulus;
import io.github.stigjohnny.shipping.scenarios.support.Sidecar.StepKind;
import io.github.stigjohnny.shipping.scenarios.support.Sidecar.Phase;
import io.github.stigjohnny.shipping.scenarios.support.Sidecar.Step;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Compares the recorded interactions of each run with the scenario's declared steps. It reads only the recorder's
 * record, the sidecar, the model and the coverage result.
 *
 * <p>Per step: a step that touches a person, a simulated external system or a planned module, or whose kind the run
 * cannot observe, is not evaluated, with its reason; a step that the run should observe but whose observer is not in
 * place is a coverage gap; every other step is evaluated. Per run (one stimulus): the interactions are matched with the
 * evaluated steps by kind, endpoints and operation, never by prose, and in view order within each cardinality; a step
 * matched fewer times than its minimum is missing; an interaction that matches no evaluated step, or exceeds a step's
 * maximum, is unexpected; an interaction of a step that must come after another, observed before it, or a synchronous
 * step observed outside its stimulus, is an order violation. Interactions outside every stimulus are unexpected, and
 * interactions whose caller maps to no module are coverage gaps. A run without a stimulus fails, whatever its steps:
 * nothing passes by default.
 */
public final class Comparison {

    /** A step's result over every run. */
    public enum Status {
        PASSED, MISSING_STEP, UNEXPECTED_INTERACTION, ORDER_VIOLATION, NOT_EVALUATED, COVERAGE_GAP
    }

    /** A finding; all but NOT_EVALUATED fail the scenario. */
    public enum FindingType {
        MISSING_STEP, UNEXPECTED_INTERACTION, ORDER_VIOLATION, NOT_EVALUATED, COVERAGE_GAP;

        public boolean fails() {
            return this != NOT_EVALUATED;
        }
    }

    /**
     * One finding of the comparison.
     *
     * @param run the stimulus the finding concerns, or empty
     * @param step the step's id, or empty
     * @param interaction the interaction's sequence number, or 0
     */
    public record Finding(FindingType type, String run, String step, int interaction, String message) {
    }

    /** A step's status, why it has it, and the interactions matched with it as {@code run:seq}. */
    public record StepResult(Step step, Status status, String reason, List<String> observed) {

        public StepResult {
            observed = List.copyOf(observed);
        }
    }

    /** The comparison of a whole scenario run. */
    public record Result(List<StepResult> steps, List<Finding> findings, Map<Integer, String> matched) {

        public Result {
            steps = List.copyOf(steps);
            findings = List.copyOf(findings);
            matched = Map.copyOf(matched);
        }

        public boolean conforms() {
            return findings.stream().noneMatch(f -> f.type().fails());
        }

        public List<Finding> failing() {
            return findings.stream().filter(f -> f.type().fails()).toList();
        }
    }

    private static final Set<Seen> COMPARED = Set.of(Seen.MODULE_API_CALL, Seen.EVENT_PUBLICATION);

    private Comparison() {
    }

    static Result compare(Sidecar sidecar, ScenarioModel model, Observers observers, Coverage.Result coverage,
            List<Stimulus> stimuli, List<Interaction> interactions) {
        List<Finding> findings = new ArrayList<>();
        Map<String, Status> statuses = new LinkedHashMap<>();
        Map<String, String> reasons = new LinkedHashMap<>();
        List<Step> evaluated = new ArrayList<>();
        for (Step step : sidecar.steps()) {
            Optional<String> notEvaluated = notEvaluated(step, model, observers);
            Optional<String> gap = notEvaluated.isPresent() ? Optional.empty() : gap(step, coverage);
            if (notEvaluated.isPresent()) {
                statuses.put(step.id(), Status.NOT_EVALUATED);
                reasons.put(step.id(), notEvaluated.get());
                findings.add(new Finding(FindingType.NOT_EVALUATED, "", step.id(), 0, notEvaluated.get()));
            } else if (gap.isPresent()) {
                statuses.put(step.id(), Status.COVERAGE_GAP);
                reasons.put(step.id(), gap.get());
                findings.add(new Finding(FindingType.COVERAGE_GAP, "", step.id(), 0, gap.get()));
            } else {
                statuses.put(step.id(), Status.PASSED);
                reasons.put(step.id(), "");
                evaluated.add(step);
            }
        }
        coverage.gaps().forEach(g -> findings.add(new Finding(FindingType.COVERAGE_GAP, "", "", 0, g)));

        Map<String, List<String>> observed = new LinkedHashMap<>();
        Map<Integer, String> matched = new LinkedHashMap<>();
        for (Interaction interaction : interactions) {
            if (interaction.kind() == Seen.UNATTRIBUTED) {
                findings.add(new Finding(FindingType.COVERAGE_GAP, interaction.run(), "", interaction.seq(),
                        "the caller of " + interaction.operation() + " (" + interaction.caller() + ") maps to no "
                                + "module"));
            } else if (COMPARED.contains(interaction.kind()) && interaction.run().isEmpty()) {
                findings.add(new Finding(FindingType.UNEXPECTED_INTERACTION, "", "", interaction.seq(),
                        describe(interaction) + " happened outside every stimulus"));
            }
        }
        if (stimuli.stream().allMatch(s -> s.run().equals(Recorder.SETUP))) {
            findings.add(new Finding(FindingType.MISSING_STEP, "", "", 0, "no stimulus of the scenario ran, so "
                    + "nothing could be observed"));
            evaluated.forEach(step -> worsen(statuses, step, Status.MISSING_STEP));
        }
        Map<String, List<String>> after = new LinkedHashMap<>();
        sidecar.steps().forEach(s -> after.put(s.id(), s.after()));
        Set<String> absorbing = new LinkedHashSet<>();
        sidecar.steps().stream().filter(s -> statuses.get(s.id()) == Status.COVERAGE_GAP)
                .forEach(s -> absorbing.add(signature(s)));
        for (Stimulus stimulus : stimuli) {
            if (stimulus.run().equals(Recorder.SETUP)) {
                continue;
            }
            List<Interaction> run = interactions.stream()
                    .filter(i -> i.run().equals(stimulus.run()) && COMPARED.contains(i.kind())).toList();
            compareRun(stimulus, run, evaluated, after, absorbing, statuses, observed, matched, findings);
        }
        Comparator<String> bySeq = Comparator.comparingInt(o -> Integer.parseInt(o.substring(o.indexOf(':') + 1)));
        List<StepResult> results = sidecar.steps().stream().map(s -> new StepResult(s, statuses.get(s.id()),
                reasons.get(s.id()), observed.getOrDefault(s.id(), List.of()).stream().sorted(bySeq).toList()))
                .toList();
        List<Finding> ordered = new ArrayList<>(findings);
        ordered.sort(Comparator.comparing(Finding::type).thenComparing(Finding::run)
                .thenComparingInt(f -> sidecar.step(f.step()).map(Step::order).orElse(0))
                .thenComparingInt(Finding::interaction));
        return new Result(results, ordered, matched);
    }

    private static void compareRun(Stimulus stimulus, List<Interaction> run, List<Step> evaluated,
            Map<String, List<String>> after, Set<String> absorbing, Map<String, Status> statuses,
            Map<String, List<String>> observed, Map<Integer, String> matched, List<Finding> findings) {
        Map<String, List<Step>> stepsBySignature = new LinkedHashMap<>();
        evaluated.forEach(s -> stepsBySignature.computeIfAbsent(signature(s), k -> new ArrayList<>()).add(s));
        Map<String, List<Interaction>> seenBySignature = new LinkedHashMap<>();
        run.forEach(i -> seenBySignature.computeIfAbsent(signature(i), k -> new ArrayList<>()).add(i));
        Map<String, List<Interaction>> assigned = new LinkedHashMap<>();
        for (Map.Entry<String, List<Step>> group : stepsBySignature.entrySet()) {
            List<Interaction> seen = seenBySignature.getOrDefault(group.getKey(), List.of());
            int next = 0;
            List<Step> steps = group.getValue();
            for (int i = 0; i < steps.size(); i++) {
                Step step = steps.get(i);
                int reserved = steps.subList(i + 1, steps.size()).stream().mapToInt(s -> s.cardinality().min()).sum();
                int take = (int) Math.max(0, Math.min((long) step.cardinality().max(),
                        (long) seen.size() - next - reserved));
                take = Math.max(take, Math.min(step.cardinality().min(), seen.size() - next));
                List<Interaction> mine = List.copyOf(seen.subList(next, next + take));
                next += take;
                assigned.put(step.id(), mine);
                mine.forEach(m -> {
                    matched.put(m.seq(), step.id());
                    observed.computeIfAbsent(step.id(), k -> new ArrayList<>()).add(m.run() + ":" + m.seq());
                });
                if (mine.size() < step.cardinality().min()) {
                    worsen(statuses, step, Status.MISSING_STEP);
                    findings.add(new Finding(FindingType.MISSING_STEP, stimulus.run(), step.id(), 0, "expected "
                            + step.cardinality().text() + " x " + text(step) + ", observed " + mine.size()));
                }
            }
            for (Interaction extra : seen.subList(next, seen.size())) {
                Step last = steps.getLast();
                worsen(statuses, last, Status.UNEXPECTED_INTERACTION);
                findings.add(new Finding(FindingType.UNEXPECTED_INTERACTION, stimulus.run(), last.id(), extra.seq(),
                        describe(extra) + " exceeds the declared cardinality " + last.cardinality().text()));
            }
        }
        for (Map.Entry<String, List<Interaction>> group : seenBySignature.entrySet()) {
            if (!stepsBySignature.containsKey(group.getKey()) && !absorbing.contains(group.getKey())) {
                group.getValue().forEach(i -> findings.add(new Finding(FindingType.UNEXPECTED_INTERACTION,
                        stimulus.run(), "", i.seq(), describe(i) + " is not a step of the scenario")));
            }
        }
        order(stimulus, evaluated, after, assigned, statuses, findings);
    }

    /** Ordering: declared predecessors first (transitively, through steps not evaluated), and synchronous phases. */
    private static void order(Stimulus stimulus, List<Step> evaluated, Map<String, List<String>> after,
            Map<String, List<Interaction>> assigned, Map<String, Status> statuses, List<Finding> findings) {
        Set<String> ids = new LinkedHashSet<>();
        evaluated.forEach(s -> ids.add(s.id()));
        for (Step step : evaluated) {
            List<Interaction> mine = assigned.getOrDefault(step.id(), List.of());
            for (Interaction interaction : mine) {
                if (step.phase() == Phase.SYNCHRONOUS && (!interaction.thread().equals(stimulus.thread())
                        || interaction.seq() <= stimulus.after() || interaction.seq() > stimulus.until())) {
                    worsen(statuses, step, Status.ORDER_VIOLATION);
                    findings.add(new Finding(FindingType.ORDER_VIOLATION, stimulus.run(), step.id(),
                            interaction.seq(), "step " + step.id() + " is synchronous, but seq " + interaction.seq()
                                    + " ran outside its stimulus on thread " + interaction.thread()));
                }
            }
            for (String before : predecessors(step, after, ids)) {
                List<Interaction> earlier = assigned.getOrDefault(before, List.of());
                if (earlier.isEmpty() || mine.isEmpty()) {
                    continue;
                }
                int lastBefore = earlier.stream().mapToInt(Interaction::seq).max().orElseThrow();
                int firstMine = mine.stream().mapToInt(Interaction::seq).min().orElseThrow();
                if (firstMine < lastBefore) {
                    worsen(statuses, step, Status.ORDER_VIOLATION);
                    findings.add(new Finding(FindingType.ORDER_VIOLATION, stimulus.run(), step.id(), firstMine,
                            "step " + step.id() + " must come after step " + before + ", but seq " + firstMine
                                    + " came before seq " + lastBefore));
                }
            }
        }
    }

    /** The evaluated steps that must come before a step, through any chain of {@code after}. */
    private static Set<String> predecessors(Step step, Map<String, List<String>> after, Set<String> evaluated) {
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> todo = new ArrayDeque<>(step.after());
        while (!todo.isEmpty()) {
            String id = todo.pop();
            if (seen.add(id)) {
                todo.addAll(after.getOrDefault(id, List.of()));
            }
        }
        seen.retainAll(evaluated);
        return seen;
    }

    private static void worsen(Map<String, Status> statuses, Step step, Status status) {
        Status current = statuses.get(step.id());
        if (current == Status.PASSED || severity(status) > severity(current)) {
            statuses.put(step.id(), status);
        }
    }

    private static int severity(Status status) {
        return switch (status) {
            case PASSED, NOT_EVALUATED -> 0;
            case UNEXPECTED_INTERACTION -> 1;
            case ORDER_VIOLATION -> 2;
            case MISSING_STEP -> 3;
            case COVERAGE_GAP -> 4;
        };
    }

    private static Optional<String> notEvaluated(Step step, ScenarioModel model, Observers observers) {
        if (step.kind() == StepKind.USER_ACTION) {
            return Optional.of(step.from() + " is a person: no code runs for a person's action");
        }
        if (step.kind() == StepKind.SIMULATED_EXTERNAL) {
            return Optional.of(step.to() + " is an external system that v0.1 simulates inside " + step.from()
                    + ": no boundary is crossed");
        }
        for (String endpoint : List.of(step.from(), step.to())) {
            if (model.module(endpoint).filter(m -> !m.built()).isPresent()) {
                return Optional.of(endpoint + " is planned in the model: it has no code yet");
            }
        }
        return observers.unobservedReason(step.kind());
    }

    private static Optional<String> gap(Step step, Coverage.Result coverage) {
        String api = step.operation().getOrDefault("interface", "");
        if (step.kind() == StepKind.MODULE_API_CALL && !coverage.observedApis().contains(api)) {
            return Optional.of("no observed bean implements " + step.operation().get("interface") + " in this run, so "
                    + "its calls cannot be seen");
        }
        if (step.kind() == StepKind.EVENT_PUBLICATION && !coverage.publicationsObserved()) {
            return Optional.of("publications are not observed in this run");
        }
        return Optional.empty();
    }

    private static String signature(Step step) {
        String to = step.kind() == StepKind.EVENT_PUBLICATION ? "" : step.to();
        return step.kind() + "|" + step.from() + "|" + to + "|" + step.operationText();
    }

    /** The recorder's kinds that are compared have the names of the step kinds they observe. */
    private static String signature(Interaction interaction) {
        return StepKind.valueOf(interaction.kind().name()) + "|" + interaction.from() + "|" + interaction.to() + "|"
                + interaction.operation();
    }

    private static String text(Step step) {
        return step.kind() + " " + step.from() + " -> " + step.to() + " " + step.operationText();
    }

    static String describe(Interaction interaction) {
        return interaction.kind() + " " + interaction.from()
                + (interaction.to().isEmpty() ? "" : " -> " + interaction.to()) + " " + interaction.operation()
                + " (seq " + interaction.seq() + ")";
    }
}
