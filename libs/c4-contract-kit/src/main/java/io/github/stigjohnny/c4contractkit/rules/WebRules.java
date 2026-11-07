package io.github.stigjohnny.c4contractkit.rules;

import io.github.stigjohnny.c4contractkit.model.ComponentStatus;
import io.github.stigjohnny.c4contractkit.model.RelationshipMode;
import io.github.stigjohnny.c4contractkit.model.WebComponent;
import io.github.stigjohnny.c4contractkit.model.WebModel;
import io.github.stigjohnny.c4contractkit.model.WebRelationship;
import io.github.stigjohnny.c4contractkit.sources.Allowlist;
import io.github.stigjohnny.c4contractkit.sources.FileKind;
import io.github.stigjohnny.c4contractkit.sources.FileRefusal;
import io.github.stigjohnny.c4contractkit.sources.Resolution;
import io.github.stigjohnny.c4contractkit.sources.ResolvedImport;
import io.github.stigjohnny.c4contractkit.sources.RootKind;
import io.github.stigjohnny.c4contractkit.sources.SourceFile;
import io.github.stigjohnny.c4contractkit.sources.SourceSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Compares the web container of a validated model with the files and imports of its roots, in both directions, and
 * holds every import that leaves the app's own files to the dependency allowlist (libs/c4-contract-kit/AGENTS.md, "Web
 * imports").
 *
 * <ul>
 *   <li>A file of the web root belongs to the component one of whose {@code web.paths} is a directory above it; a file
 *       of no component is unowned. Test and public roots belong to no component.</li>
 *   <li>A production import of a file of another component is a dependency between the two components, whatever its
 *       kind (type-only imports and re-exports included). Imports inside one component are not edges, but every
 *       import between code files, tests included, counts for cycles.</li>
 *   <li>A test is held to the rules of its component: it may import another component only along a relationship and
 *       through its {@code web.entry}, but its imports are never evidence, so a test never satisfies a REQUIRED
 *       relationship. No production file may import a test.</li>
 *   <li>A component with {@code web.entry} may be imported by other components through that file only.</li>
 *   <li>A bare specifier in code that runs in the browser must name a package that the importing component's
 *       {@code web.packages} lists, which {@code package.json} declares in {@code dependencies} at an exact version,
 *       and which the allowlist approves for the app in {@code dependencies}. A test's package must be listed in
 *       {@code web.testPackages} and approved in {@code devDependencies} or {@code dependencies}.</li>
 *   <li>Browser code may import no built-in module of Node.js. A test may import one written with {@code node:} that
 *       {@code web.testBuiltins} lists, when the allowlist approves the Node.js that {@code engines.node} names for
 *       the {@code web} job.</li>
 *   <li>What the grammar refuses, every file type it does not cover and every refused setting of the app's
 *       configuration fails the check.</li>
 * </ul>
 */
public final class WebRules {

    private static final Set<String> RUNTIME = Set.of("dependencies");
    private static final Set<String> TEST_SCOPES = Set.of("dependencies", "devDependencies");

    private final WebModel model;
    private final WebInputs inputs;
    /** Every file of every root, by its path relative to the repository root. */
    private final Map<String, FileKind> kinds = new HashMap<>();
    private final Map<String, Integer> codeCounts = new HashMap<>();
    private final Map<List<String>, Set<ImportEvidenceItem>> dependencies = new HashMap<>();
    private final Map<List<String>, Set<ImportEvidenceItem>> testDependencies = new HashMap<>();
    private final Map<List<String>, Set<ImportEvidenceItem>> bypasses = new HashMap<>();
    private final Map<List<String>, Set<ImportEvidenceItem>> undeclaredPackages = new HashMap<>();
    private final Map<List<String>, Set<ImportEvidenceItem>> undeclaredTestPackages = new HashMap<>();
    /** By owner, specifier and message: the import is declared, but no review approves it. */
    private final Map<List<String>, Set<ImportEvidenceItem>> unapproved = new HashMap<>();
    private final Map<String, Set<String>> graph = new TreeMap<>();
    private final List<ImportEvidenceItem> fileImports = new ArrayList<>();
    private final List<Refused> refused = new ArrayList<>();
    private final List<WebFinding> findings = new ArrayList<>();
    private final List<ExternalImportItem> externalImports = new ArrayList<>();

    private WebRules(WebModel model, WebInputs inputs) {
        this.model = model;
        this.inputs = inputs;
    }

    /**
     * Compares the model with the web root alone; see {@link #compare(WebModel, WebInputs)}.
     *
     * @param model a validated web model
     * @param sources the files and imports of the model's {@code web.root}
     */
    public static WebComparison compare(WebModel model, SourceSet sources) {
        return compare(model, WebInputs.of(sources));
    }

    /**
     * Compares the model with its roots, the app's configuration and the allowlist.
     *
     * @param model a validated web model
     * @param inputs the files and imports of the model's roots, the app's configuration and the allowlist
     * @return the components, the edges with their verdicts and the violations, in report order
     */
    public static WebComparison compare(WebModel model, WebInputs inputs) {
        WebRules rules = new WebRules(model, inputs);
        rules.readFiles(inputs.sources(), RootKind.SOURCE);
        inputs.testRoots().forEach(set -> rules.readFiles(set, RootKind.TEST));
        inputs.publicRoots().forEach(set -> rules.readFiles(set, RootKind.PUBLIC));
        for (SourceSet set : rules.allSets()) {
            set.refusals().forEach(refusal -> rules.readRefusal(set, refusal));
        }
        inputs.configuration().refusals().forEach(rules::readConfiguration);
        inputs.sources().imports().forEach(found -> rules.readImport(inputs.sources(), found, true));
        inputs.testRoots().forEach(set -> set.imports().forEach(found -> rules.readImport(set, found, false)));
        return rules.result();
    }

    private List<SourceSet> allSets() {
        List<SourceSet> sets = new ArrayList<>();
        sets.add(inputs.sources());
        sets.addAll(inputs.testRoots());
        sets.addAll(inputs.publicRoots());
        return sets;
    }

    private void readFiles(SourceSet set, RootKind root) {
        for (SourceFile file : set.files()) {
            String shown = shown(set, file.path());
            kinds.put(shown, file.kind());
            if (root == RootKind.SOURCE) {
                own(file, shown);
            }
            if (file.kind() == FileKind.UNSUPPORTED) {
                String covered = root == RootKind.PUBLIC ? "is not a style sheet, image or font: a public root holds "
                        + "only those, served as they are." : "is not a .ts, .tsx, .css, image or font file: the "
                        + "restricted import grammar does not cover it.";
                findings.add(new WebFinding(WebFindingType.UNSUPPORTED_FILE, List.of(shown), shown + " " + covered,
                        List.of()));
            }
        }
    }

    private void own(SourceFile file, String shown) {
        Optional<WebComponent> owner = model.componentOf(file.path());
        if (owner.isEmpty()) {
            findings.add(new WebFinding(WebFindingType.UNOWNED_FILE, List.of(shown),
                    shown + " is in no component's web.paths.", List.of()));
        }
        owner.ifPresent(component -> {
            if (file.kind() == FileKind.CODE) {
                codeCounts.merge(component.id(), 1, Integer::sum);
            }
            if (component.status() == ComponentStatus.PLANNED) {
                findings.add(new WebFinding(WebFindingType.PLANNED_COMPONENT_HAS_CODE, List.of(component.id(), shown),
                        component.id() + " is planned, but its web.paths contain " + shown + ".", List.of()));
            }
        });
    }

    private void readRefusal(SourceSet set, FileRefusal refusal) {
        String shown = shown(set, refusal.file());
        refused.add(new Refused(shown, refusal.line(), refusal.reason(),
                new ImportEvidenceItem(shown, refusal.line(), "", "refused", "")));
    }

    private void readConfiguration(FileRefusal refusal) {
        String app = inputs.configuration().app();
        String shown = app.isEmpty() ? refusal.file() : app + "/" + refusal.file();
        findings.add(new WebFinding(WebFindingType.UNSUPPORTED_CONFIGURATION, List.of(shown),
                shown + ":" + refusal.line() + ": " + refusal.reason() + ".",
                List.of(new ImportEvidenceItem(shown, refusal.line(), "", "refused", ""))));
    }

    private void readImport(SourceSet set, ResolvedImport found, boolean webRoot) {
        String file = shown(set, found.file());
        boolean test = kinds.get(file) == FileKind.TEST;
        Optional<WebComponent> source = webRoot ? model.componentOf(found.file()) : Optional.empty();
        String owner = source.map(WebComponent::id).orElse(webRoot ? file : set.root());
        switch (found.resolution()) {
            case Resolution.ToFile(String target) -> readFileImport(set, found, file, test, source, target);
            case Resolution.ToPackage(String name) -> {
                if (test) {
                    testPackage(found, file, owner, name);
                } else {
                    browserPackage(found, file, source, name);
                }
            }
            case Resolution.ToBuiltin(String name) -> builtin(found, file, test, name);
            case Resolution.Unresolved(String reason) -> findings.add(new WebFinding(WebFindingType.UNRESOLVED_IMPORT,
                    List.of(file, found.specifier()), file + ":" + found.line() + ": '" + found.specifier() + "' "
                            + reason + ".", List.of(evidence(found, file, ""))));
            case Resolution.Unsupported(String reason) ->
                    refused.add(new Refused(file, found.line(), reason, evidence(found, file, "")));
        }
    }

    private void readFileImport(SourceSet set, ResolvedImport found, String file, boolean test,
                                Optional<WebComponent> source, String target) {
        String shownTarget = shown(set, target);
        ImportEvidenceItem evidence = evidence(found, file, shownTarget);
        FileKind kind = kinds.get(shownTarget);
        if (!test && kind == FileKind.TEST) {
            findings.add(new WebFinding(WebFindingType.TEST_FILE_IMPORTED, List.of(file, shownTarget), file
                    + " imports the test " + shownTarget + ": production code may not import tests, whose imports "
                    + "are held to the rules for tests and are never evidence.", List.of(evidence)));
            return;
        }
        if (kind == FileKind.CODE || kind == FileKind.TEST) {
            graph.computeIfAbsent(file, _ -> new TreeSet<>()).add(shownTarget);
            fileImports.add(evidence);
        }
        if (source.isEmpty()) {
            return;
        }
        Optional<WebComponent> destination = model.componentOf(target);
        if (destination.isEmpty() || source.get().id().equals(destination.get().id())) {
            return;
        }
        List<String> pair = List.of(source.get().id(), destination.get().id());
        add(test ? testDependencies : dependencies, pair, evidence);
        if (destination.get().entry().filter(entry -> !entry.equals(target)).isPresent()) {
            add(bypasses, pair, evidence);
        }
    }

    /** A file in no component is already a violation; its packages are reported as undeclared, without another. */
    private void browserPackage(ResolvedImport found, String file, Optional<WebComponent> source, String name) {
        ImportEvidenceItem evidence = evidence(found, file, name);
        String version = inputs.configuration().dependencies().getOrDefault(name, "");
        if (source.isEmpty()) {
            decide(found, file, name, version, "web.packages (the file is in no component)",
                    ExternalImportVerdict.UNDECLARED, "");
            return;
        }
        WebComponent component = source.get();
        String rule = "web.packages of " + component.id();
        if (!component.packages().contains(name)) {
            add(undeclaredPackages, List.of(component.id(), name), evidence);
            decide(found, file, name, version, rule, ExternalImportVerdict.UNDECLARED, "");
            return;
        }
        if (version.isEmpty()) {
            unapproved(component.id(), name, "package.json declares no exact version of " + name + " in dependencies, "
                    + "so no reviewed release is known", evidence);
            decide(found, file, name, version, rule, ExternalImportVerdict.UNPINNED, "");
            return;
        }
        if (approvalUnavailable(component.id(), name, evidence)) {
            decide(found, file, name, version, rule, ExternalImportVerdict.UNAPPROVED, "");
            return;
        }
        Optional<Allowlist.Entry> entry = inputs.allowlist().npm(name, version, inputs.configuration().app(), RUNTIME);
        if (entry.isEmpty()) {
            unapproved(component.id(), name, Allowlist.PATH + " approves no npm " + name + " " + version + " for "
                    + inputs.configuration().app() + " in dependencies, and only that approval covers code that runs "
                    + "in the browser", evidence);
        }
        decide(found, file, name, version, rule, entry);
    }

    private void testPackage(ResolvedImport found, String file, String owner, String name) {
        ImportEvidenceItem evidence = evidence(found, file, name);
        String version = inputs.configuration().devDependencies().getOrDefault(name,
                inputs.configuration().dependencies().getOrDefault(name, ""));
        String rule = "web.testPackages";
        if (!model.testPackages().contains(name)) {
            add(undeclaredTestPackages, List.of(owner, name), evidence);
            decide(found, file, name, version, rule, ExternalImportVerdict.UNDECLARED, "");
            return;
        }
        if (version.isEmpty()) {
            unapproved(owner, name, "package.json declares no exact version of " + name + ", so no reviewed release is "
                    + "known", evidence);
            decide(found, file, name, version, rule, ExternalImportVerdict.UNPINNED, "");
            return;
        }
        if (approvalUnavailable(owner, name, evidence)) {
            decide(found, file, name, version, rule, ExternalImportVerdict.UNAPPROVED, "");
            return;
        }
        Optional<Allowlist.Entry> entry = inputs.allowlist().npm(name, version, inputs.configuration().app(),
                TEST_SCOPES);
        if (entry.isEmpty()) {
            unapproved(owner, name, Allowlist.PATH + " approves no npm " + name + " " + version + " for "
                    + inputs.configuration().app() + " in devDependencies or dependencies", evidence);
        }
        decide(found, file, name, version, rule, entry);
    }

    private void builtin(ResolvedImport found, String file, boolean test, String name) {
        ImportEvidenceItem evidence = evidence(found, file, name);
        Optional<String> node = test ? inputs.configuration().node() : Optional.empty();
        String version = node.orElse("");
        String problem;
        if (!test) {
            problem = file + " imports the Node.js built-in " + name + ": code that runs in the browser may not import "
                    + "Node.js built-ins.";
            decide(found, file, name, version, "no Node.js built-in in browser code", ExternalImportVerdict.REFUSED,
                    "");
        } else if (!name.startsWith("node:")) {
            problem = file + " imports " + name + " without the node: prefix: a test names a Node.js built-in as node:"
                    + name + ", which web.testBuiltins must list.";
            decide(found, file, name, version, "a test names a Node.js built-in with node:",
                    ExternalImportVerdict.REFUSED, "");
        } else if (!model.testBuiltins().contains(name)) {
            problem = file + " imports the Node.js built-in " + name + ", which web.testBuiltins does not list.";
            decide(found, file, name, version, "web.testBuiltins", ExternalImportVerdict.UNDECLARED, "");
        } else {
            testBuiltin(found, file, name, node, evidence);
            return;
        }
        findings.add(new WebFinding(WebFindingType.NODE_BUILTIN, List.of(file, name), problem, List.of(evidence)));
    }

    /** A built-in that web.testBuiltins lists: the allowlist must approve the Node.js that engines.node names. */
    private void testBuiltin(ResolvedImport found, String file, String name, Optional<String> node,
                             ImportEvidenceItem evidence) {
        String rule = "web.testBuiltins";
        if (node.isEmpty()) {
            unapproved(file, name, "package.json names no exact engines.node, so the Node.js whose review covers "
                    + name + " is not known", evidence);
            decide(found, file, name, "", rule, ExternalImportVerdict.UNPINNED, "");
            return;
        }
        if (approvalUnavailable(file, name, evidence)) {
            decide(found, file, name, node.get(), rule, ExternalImportVerdict.UNAPPROVED, "");
            return;
        }
        Optional<Allowlist.Entry> entry = inputs.allowlist().nodeRuntime(node.get());
        if (entry.isEmpty()) {
            unapproved(file, name, Allowlist.PATH + " approves no " + Allowlist.NODE_RUNTIME + " " + node.get()
                    + " for the web job, the Node.js whose built-in " + name + " tests import", evidence);
        }
        decide(found, file, name, node.get(), rule, entry);
    }

    private void decide(ResolvedImport found, String file, String target, String version, String rule,
                        Optional<Allowlist.Entry> entry) {
        decide(found, file, target, version, rule,
                entry.isPresent() ? ExternalImportVerdict.APPROVED : ExternalImportVerdict.UNAPPROVED,
                entry.map(Allowlist.Entry::summary).orElse(""));
    }

    private void decide(ResolvedImport found, String file, String target, String version, String rule,
                        ExternalImportVerdict verdict, String allowlistEntry) {
        externalImports.add(new ExternalImportItem(file, found.line(), found.specifier(), target, version, rule,
                allowlistEntry, verdict));
    }

    /** True, after the finding, when the allowlist could not be read: then nothing is approved. */
    private boolean approvalUnavailable(String owner, String name, ImportEvidenceItem evidence) {
        Optional<String> reason = inputs.allowlist().unavailable();
        reason.ifPresent(why -> unapproved(owner, name, "the allowlist is unavailable (" + why + "), so nothing is "
                + "approved", evidence));
        return reason.isPresent();
    }

    private void unapproved(String owner, String name, String why, ImportEvidenceItem evidence) {
        add(unapproved, List.of(owner, name, why), evidence);
    }

    private WebComparison result() {
        refused.stream().sorted(Comparator.comparing(Refused::file).thenComparingInt(Refused::line))
                .forEach(item -> findings.add(new WebFinding(WebFindingType.UNSUPPORTED_SYNTAX,
                        List.of(item.file()), item.file() + ":" + item.line() + ": " + item.reason() + ".",
                        List.of(item.evidence()))));
        List<WebEdgeOutcome> edges = edges();
        testDependencies.forEach((pair, evidence) -> {
            if (model.relationship(pair.get(0), pair.get(1)).isEmpty()) {
                findings.add(new WebFinding(WebFindingType.DIVERGENCE, pair, "A test of " + pair.get(0) + " imports "
                        + pair.get(1) + ", but the model declares no relationship " + pair.get(0) + " -> "
                        + pair.get(1) + ".", List.copyOf(evidence)));
            }
        });
        bypasses.forEach((pair, evidence) -> findings.add(new WebFinding(WebFindingType.ENTRY_POINT_BYPASSED, pair,
                pair.get(0) + " imports " + pair.get(1) + " other than through its web.entry "
                        + shown(inputs.sources(), model.component(pair.get(1)).entry().orElseThrow()) + ".",
                List.copyOf(evidence))));
        undeclaredPackages.forEach((pair, evidence) -> findings.add(new WebFinding(WebFindingType.UNDECLARED_PACKAGE,
                pair, pair.get(0) + " imports the package " + pair.get(1) + ", which its web.packages does not list.",
                List.copyOf(evidence))));
        undeclaredTestPackages.forEach((pair, evidence) -> findings.add(new WebFinding(
                WebFindingType.UNDECLARED_PACKAGE, pair, "A test of " + pair.get(0) + " imports the package "
                        + pair.get(1) + ", which web.testPackages does not list.", List.copyOf(evidence))));
        unapproved.forEach((key, evidence) -> findings.add(new WebFinding(WebFindingType.UNAPPROVED_IMPORT,
                List.of(key.get(0), key.get(1)), key.get(2) + ".", List.copyOf(evidence))));
        for (List<String> cycle : ImportCycles.in(graph)) {
            Set<String> members = new HashSet<>(cycle);
            findings.add(new WebFinding(WebFindingType.IMPORT_CYCLE, cycle, "These files import each other in a cycle: "
                    + String.join(", ", cycle) + ".", fileImports.stream()
                    .filter(item -> members.contains(item.file()) && members.contains(item.target())).toList()));
        }
        List<WebComponentSummary> components = model.components().stream().map(this::summary).toList();
        return new WebComparison(inputs.sources().root(), model.testRoots(), model.publicRoots(), components, edges,
                externalImports, findings);
    }

    private List<WebEdgeOutcome> edges() {
        Map<List<String>, Set<ImportEvidenceItem>> undeclared = new HashMap<>(dependencies);
        List<WebEdgeOutcome> edges = new ArrayList<>();
        for (WebRelationship relationship : model.relationships()) {
            Set<ImportEvidenceItem> evidence = Objects.requireNonNullElse(
                    undeclared.remove(List.of(relationship.source(), relationship.target())), Set.of());
            edges.add(declared(relationship, evidence));
        }
        undeclared.forEach((pair, evidence) -> {
            String source = pair.get(0);
            String target = pair.get(1);
            edges.add(new WebEdgeOutcome(source, target, EdgeMode.UNDECLARED, EdgeVerdict.DIVERGENCE,
                    List.copyOf(evidence)));
            findings.add(new WebFinding(WebFindingType.DIVERGENCE, pair, source + " imports " + target
                    + ", but the model declares no relationship " + source + " -> " + target + ".",
                    List.copyOf(evidence)));
        });
        return edges;
    }

    private WebEdgeOutcome declared(WebRelationship relationship, Set<ImportEvidenceItem> evidence) {
        EdgeMode mode = relationship.mode() == RelationshipMode.REQUIRED ? EdgeMode.REQUIRED : EdgeMode.PERMITTED;
        EdgeVerdict verdict;
        if (isPlanned(relationship.source()) || isPlanned(relationship.target())) {
            verdict = EdgeVerdict.NOT_EVALUATED;
        } else if (!evidence.isEmpty()) {
            verdict = EdgeVerdict.CONVERGENCE;
        } else {
            verdict = mode == EdgeMode.REQUIRED ? EdgeVerdict.ABSENCE : EdgeVerdict.NO_STATIC_EVIDENCE;
        }
        if (verdict == EdgeVerdict.ABSENCE) {
            findings.add(new WebFinding(WebFindingType.ABSENCE, List.of(relationship.source(), relationship.target()),
                    "The model requires " + relationship.source() + " -> " + relationship.target()
                            + ", but no file of " + relationship.source() + " imports a file of "
                            + relationship.target() + ".", List.of()));
        }
        return new WebEdgeOutcome(relationship.source(), relationship.target(), mode, verdict, List.copyOf(evidence));
    }

    private boolean isPlanned(String componentId) {
        return model.component(componentId).status() == ComponentStatus.PLANNED;
    }

    private WebComponentSummary summary(WebComponent component) {
        int fileCount = codeCounts.getOrDefault(component.id(), 0);
        if (component.status() == ComponentStatus.BUILT) {
            if (fileCount == 0) {
                findings.add(new WebFinding(WebFindingType.BUILT_COMPONENT_EMPTY, List.of(component.id()),
                        component.id() + " is built, but its web.paths contain no .ts or .tsx file.", List.of()));
            }
            String entry = component.entry().map(path -> shown(inputs.sources(), path)).orElse("");
            if (!entry.isEmpty() && !kinds.containsKey(entry)) {
                findings.add(new WebFinding(WebFindingType.ENTRY_POINT_MISSING, List.of(component.id(), entry),
                        component.id() + " is built, but its web.entry " + entry + " does not exist.", List.of()));
            }
        }
        return new WebComponentSummary(component.id(), component.name(), component.status(), component.paths(),
                component.entry(), component.packages(), fileCount);
    }

    private ImportEvidenceItem evidence(ResolvedImport found, String file, String target) {
        return new ImportEvidenceItem(file, found.line(), found.specifier(), found.kind().label(), target);
    }

    /** A path under a root as reports show it: relative to the repository root (a root is never empty). */
    private static String shown(SourceSet set, String path) {
        return set.root() + "/" + path;
    }

    private static <K> void add(Map<K, Set<ImportEvidenceItem>> evidenceByKey, K key, ImportEvidenceItem evidence) {
        evidenceByKey.computeIfAbsent(key, _ -> new HashSet<>()).add(evidence);
    }

    /** A construct or specifier the grammar refused, by its file (relative to the repository root) and line. */
    private record Refused(String file, int line, String reason, ImportEvidenceItem evidence) {
    }
}
