package io.github.stigjohnny.shipping.scenarios.support;

import io.github.stigjohnny.c4contractkit.api.Conformance;
import io.github.stigjohnny.c4contractkit.api.Report;
import io.github.stigjohnny.shipping.architecture.ClassDirectories;
import io.github.stigjohnny.shipping.scenarios.support.ScenarioReport.Outcome;
import io.github.stigjohnny.shipping.scenarios.support.ScenarioReport.Provenance;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.TestInfo;
import org.springframework.aop.framework.Advised;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.context.support.AbstractApplicationContext;

/**
 * One run of an executable scenario, as a test drives it: the real modules in a Spring context with the recorder's
 * observers, the coverage verification, the stimuli, the outcome assertions, the comparison and the reports.
 *
 * <p>The test only starts work and asserts outcomes. It never says that a step happened: {@link #finish()} closes the
 * recording, compares it with the sidecar, evaluates the outcome assertions, and writes the reports, all before
 * {@link #verify()} fails.
 */
public final class ScenarioRun implements AutoCloseable {

    /** The test task's working directory is apps/shipping-api; the repository is two levels up. */
    private static final Path REPOSITORY = Path.of("../..");
    private static final String MODEL = "architecture/workspace.dsl";
    private static final String SIDECARS = "architecture/scenarios/";
    private static final Path REPORTS = Path.of("build/reports/scenarios");
    private static String mainClassesSha256;

    /** The result of a run. */
    public record Result(Comparison.Result comparison, List<Outcome> outcomes, List<Recorder.Interaction> interactions,
            Path markdown, String verdict) {

        public Result {
            outcomes = List.copyOf(outcomes);
            interactions = List.copyOf(interactions);
        }

        public boolean outcomesHeld() {
            return outcomes.stream().allMatch(Outcome::held);
        }

        /** The findings that fail the scenario. */
        public List<Comparison.Finding> failing() {
            return comparison.failing();
        }
    }

    private final String reportName;
    private final TestInfo test;
    private final Observers observers;
    private final ScenarioModel model;
    private final Sidecar sidecar;
    private final Recorder recorder;
    private final AnnotationConfigApplicationContext context;
    private final Coverage.Result coverage;
    private final Map<String, Runnable> checks = new LinkedHashMap<>();
    private final List<Throwable> failures = new ArrayList<>();
    private Result result;

    private ScenarioRun(String view, String reportName, TestInfo test, Observers observers,
            Class<?>... configurations) {
        this.reportName = reportName;
        this.test = test;
        this.observers = observers;
        Report kit = Conformance.check(REPOSITORY.resolve(MODEL), ClassDirectories.main());
        this.model = ScenarioModel.read(REPOSITORY.resolve(MODEL), kit);
        String sidecarPath = SIDECARS + Sidecar.fileName(view);
        this.sidecar = Sidecar.read(REPOSITORY.resolve(sidecarPath), sidecarPath, model);
        this.recorder = new Recorder(model, observers.ignoredApis(), observers.publications());
        this.context = new AnnotationConfigApplicationContext();
        context.getBeanFactory().addBeanPostProcessor(recorder.observers());
        Supplier<ApplicationEventMulticaster> multicaster = SimpleApplicationEventMulticaster::new;
        context.registerBean(AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME,
                ApplicationEventMulticaster.class, multicaster);
        context.register(configurations);
        try {
            context.refresh();
            this.coverage = Coverage.verify(context, recorder, model, Coverage.callSites(model,
                    ClassDirectories.main()));
        } catch (RuntimeException e) {
            context.close();
            throw e;
        }
        recorder.begin();
    }

    /** Starts the scenario of a dynamic view; its reports are build/reports/scenarios/{@code <view slug>}. */
    public static ScenarioRun start(String view, TestInfo test, Observers observers, Class<?>... configurations) {
        return new ScenarioRun(view, Sidecar.fileName(view).replaceFirst("\\.json$", ""), test, observers,
                configurations);
    }

    /** Starts a negative fixture's run; its reports are build/reports/scenarios/fixtures/{@code <name>}. */
    public static ScenarioRun fixture(String view, String name, TestInfo test, Observers observers,
            Class<?>... configurations) {
        return new ScenarioRun(view, "fixtures/" + name, test, observers, configurations);
    }

    /** A bean of the run's context, as the modules see it: an observed bean is the observer's proxy. */
    public <T> T bean(Class<T> type) {
        return context.getBean(type);
    }

    /**
     * The real implementation behind an observed bean, for what only the test does with it, such as binding a tenant
     * the way the planned HTTP filter will. Calls on it are not observed.
     */
    public <T> T implementation(Class<?> api, Class<T> type) {
        Object bean = context.getBean(api);
        try {
            Object target = bean instanceof Advised advised ? advised.getTargetSource().getTarget() : bean;
            return type.cast(target);
        } catch (Exception e) {
            throw new IllegalStateException("the implementation behind " + api.getName() + " cannot be read", e);
        }
    }

    /** Prepares the scenario; recorded as setup and never compared. */
    public <T> T setup(String description, Supplier<T> work) {
        return recorder.stimulus(Recorder.SETUP, description, work);
    }

    /** Starts one run of the scenario: everything it causes is recorded under the run's name and compared. */
    public <T> T stimulus(String run, String description, Supplier<T> work) {
        if (run.equals(Recorder.SETUP) || run.isBlank()) {
            throw new IllegalArgumentException("a run needs a name of its own");
        }
        return recorder.stimulus(run, description, work);
    }

    /** An outcome assertion, evaluated after the recording ends; only whether it held goes into the report. */
    public void outcome(String description, Runnable check) {
        if (checks.putIfAbsent(description, check) != null) {
            throw new IllegalArgumentException("a second outcome with the description: " + description);
        }
    }

    /** Ends the recording, compares, evaluates the outcomes and writes the reports. */
    public Result finish() {
        if (result != null) {
            return result;
        }
        recorder.end();
        List<Recorder.Interaction> interactions = recorder.interactions();
        List<Recorder.Stimulus> stimuli = recorder.stimuli();
        Comparison.Result comparison = Comparison.compare(sidecar, model, observers, coverage, stimuli,
                interactions);
        List<Outcome> outcomes = new ArrayList<>();
        checks.forEach((description, check) -> {
            try {
                check.run();
                outcomes.add(new Outcome(description, true));
            } catch (AssertionError | RuntimeException e) {
                failures.add(e);
                outcomes.add(new Outcome(description, false));
            }
        });
        String sidecarPath = SIDECARS + Sidecar.fileName(sidecar.view());
        Provenance provenance = new Provenance(revision(), MODEL, sha256(REPOSITORY.resolve(MODEL)), sidecarPath,
                sha256(REPOSITORY.resolve(sidecarPath)), mainClassesSha256());
        ScenarioReport report = new ScenarioReport(sidecar, title(), test.getTestClass().map(Class::getName)
                .orElse(""), test.getTestMethod().map(Method::getName).orElse(""), provenance, observers, coverage,
                stimuli, interactions, comparison, outcomes);
        Path markdown = report.write(REPORTS, reportName);
        result = new Result(comparison, outcomes, interactions, markdown, report.verdict());
        return result;
    }

    /** {@link #finish()}, then fails unless the run conforms and every outcome held. */
    public void verify() {
        Result finished = finish();
        List<String> problems = new ArrayList<>();
        finished.comparison().failing().forEach(f -> problems.add(f.type() + (f.step().isEmpty() ? "" : " "
                + f.step()) + ": " + f.message()));
        finished.outcomes().stream().filter(o -> !o.held()).forEach(o -> problems.add("outcome did not hold: "
                + o.description()));
        if (!problems.isEmpty()) {
            AssertionError error = new AssertionError("Scenario " + sidecar.view() + " " + finished.verdict()
                    + (finished.outcomesHeld() ? "" : ", and outcomes failed") + " (report: " + finished.markdown()
                    + "):\n  " + String.join("\n  ", problems));
            failures.forEach(error::addSuppressed);
            throw error;
        }
    }

    @Override
    public void close() {
        context.close();
    }

    /** The view's title: the first sentence of its description, as the docs tool titles it. */
    private String title() {
        String description = model.view(sidecar.view()).orElseThrow().description().strip();
        int end = description.indexOf(". ");
        String sentence = end < 0 ? description : description.substring(0, end);
        return sentence.endsWith(".") ? sentence.substring(0, sentence.length() - 1) : sentence;
    }

    private static String revision() {
        String revision = System.getProperty("shipping.revision", "").strip();
        return revision.isEmpty() ? "unknown: the test did not run through Gradle" : revision;
    }

    private static String sha256(Path file) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** One digest over every main class file: its path relative to its directory, then its bytes, in path order. */
    private static synchronized String mainClassesSha256() {
        if (mainClassesSha256 == null) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                for (Path directory : ClassDirectories.main()) {
                    if (!Files.isDirectory(directory)) {
                        continue;
                    }
                    List<Path> files;
                    try (Stream<Path> walk = Files.walk(directory)) {
                        files = walk.filter(Files::isRegularFile).sorted().toList();
                    }
                    for (Path file : files) {
                        digest.update(directory.relativize(file).toString().replace('\\', '/')
                                .getBytes(StandardCharsets.UTF_8));
                        digest.update((byte) 0);
                        digest.update(Files.readAllBytes(file));
                    }
                }
                mainClassesSha256 = HexFormat.of().formatHex(digest.digest());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
        return mainClassesSha256;
    }
}
