package io.github.stigjohnny.c4contractkit.rules;

import io.github.stigjohnny.c4contractkit.model.ComponentStatus;
import io.github.stigjohnny.c4contractkit.model.RelationshipMode;
import io.github.stigjohnny.c4contractkit.model.WebComponent;
import io.github.stigjohnny.c4contractkit.model.WebModel;
import io.github.stigjohnny.c4contractkit.model.WebRelationship;
import io.github.stigjohnny.c4contractkit.sources.FileKind;
import io.github.stigjohnny.c4contractkit.sources.FileRefusal;
import io.github.stigjohnny.c4contractkit.sources.ResolvedImport;
import io.github.stigjohnny.c4contractkit.sources.Resolution;
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
 * Compares the web container of a validated model with the files and imports of its web root, in both directions
 * (libs/c4-contract-kit/AGENTS.md, "Web imports").
 *
 * <ul>
 *   <li>A file belongs to the component one of whose {@code web.paths} is a directory above it; a file of no component
 *       is unowned.</li>
 *   <li>An import of a file of another component is a dependency between the two components, whatever its kind
 *       (type-only imports and re-exports included). Imports inside one component are not edges, but every import
 *       between code files counts for cycles.</li>
 *   <li>A component with {@code web.entry} may be imported by other components through that file only.</li>
 *   <li>A bare specifier must name a package that the importing component's {@code web.packages} lists.</li>
 *   <li>Test files are owned but not evaluated; what the grammar refuses, and every file type it does not cover, fails
 *       the check.</li>
 * </ul>
 */
public final class WebRules {

    private final WebModel model;
    private final SourceSet sources;
    private final Map<String, FileKind> kinds = new HashMap<>();
    private final Map<String, Integer> codeCounts = new HashMap<>();
    private final Map<List<String>, Set<ImportEvidenceItem>> dependencies = new HashMap<>();
    private final Map<List<String>, Set<ImportEvidenceItem>> bypasses = new HashMap<>();
    private final Map<List<String>, Set<ImportEvidenceItem>> undeclaredPackages = new HashMap<>();
    private final Map<String, Set<String>> graph = new TreeMap<>();
    private final List<ImportEvidenceItem> fileImports = new ArrayList<>();
    private final List<Refused> refused = new ArrayList<>();
    private final List<WebFinding> findings = new ArrayList<>();

    private WebRules(WebModel model, SourceSet sources) {
        this.model = model;
        this.sources = sources;
        sources.files().forEach(file -> kinds.put(file.path(), file.kind()));
    }

    /**
     * Compares the model with the sources.
     *
     * @param model a validated web model
     * @param sources the files and imports of the model's {@code web.root}
     * @return the components, the edges with their verdicts and the violations, in report order
     */
    public static WebComparison compare(WebModel model, SourceSet sources) {
        WebRules rules = new WebRules(model, sources);
        sources.files().forEach(rules::readFile);
        // Test files are owned but not evaluated, as the class check reads production classes only.
        sources.refusals().stream().filter(refusal -> !rules.isTest(refusal.file())).forEach(rules::readRefusal);
        sources.imports().stream().filter(found -> !rules.isTest(found.file())).forEach(rules::readImport);
        return rules.result();
    }

    private boolean isTest(String file) {
        return kinds.get(file) == FileKind.TEST;
    }

    private void readFile(SourceFile file) {
        String shown = shown(file.path());
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
        if (file.kind() == FileKind.UNSUPPORTED) {
            findings.add(new WebFinding(WebFindingType.UNSUPPORTED_FILE, List.of(shown), shown + " is not a .ts, "
                    + ".tsx, .css, image or font file: the restricted import grammar does not cover it.", List.of()));
        }
    }

    private void readRefusal(FileRefusal refusal) {
        refused.add(new Refused(refusal.file(), refusal.line(), refusal.reason(),
                new ImportEvidenceItem(shown(refusal.file()), refusal.line(), "", "refused", "")));
    }

    private void readImport(ResolvedImport found) {
        Optional<WebComponent> source = model.componentOf(found.file());
        switch (found.resolution()) {
            case Resolution.ToFile(String target) -> {
                ImportEvidenceItem evidence = evidence(found, shown(target));
                if (kinds.get(target) == FileKind.CODE) {
                    graph.computeIfAbsent(found.file(), _ -> new TreeSet<>()).add(target);
                    fileImports.add(evidence);
                }
                Optional<WebComponent> destination = model.componentOf(target);
                if (source.isPresent() && destination.isPresent()
                        && !source.get().id().equals(destination.get().id())) {
                    List<String> pair = List.of(source.get().id(), destination.get().id());
                    add(dependencies, pair, evidence);
                    if (destination.get().entry().filter(entry -> !entry.equals(target)).isPresent()) {
                        add(bypasses, pair, evidence);
                    }
                }
            }
            case Resolution.ToPackage(String name) -> source.filter(component -> !component.packages().contains(name))
                    .ifPresent(component -> add(undeclaredPackages, List.of(component.id(), name),
                            evidence(found, name)));
            case Resolution.Unresolved(String reason) -> {
                String file = shown(found.file());
                findings.add(new WebFinding(WebFindingType.UNRESOLVED_IMPORT, List.of(file, found.specifier()),
                        file + ":" + found.line() + ": '" + found.specifier() + "' " + reason + ".",
                        List.of(evidence(found, ""))));
            }
            case Resolution.Unsupported(String reason) ->
                    refused.add(new Refused(found.file(), found.line(), reason, evidence(found, "")));
            case Resolution.ToBuiltin(String name) -> refused.add(new Refused(found.file(), found.line(),
                    "a built-in module of Node.js (" + name + ") is not supported in the web app", evidence(found, "")));
        }
    }

    private WebComparison result() {
        refused.stream().sorted(Comparator.comparing(Refused::file).thenComparingInt(Refused::line))
                .forEach(item -> findings.add(new WebFinding(WebFindingType.UNSUPPORTED_SYNTAX,
                        List.of(shown(item.file())), shown(item.file()) + ":" + item.line() + ": " + item.reason() + ".",
                        List.of(item.evidence()))));
        List<WebEdgeOutcome> edges = edges();
        bypasses.forEach((pair, evidence) -> findings.add(new WebFinding(WebFindingType.ENTRY_POINT_BYPASSED, pair,
                pair.get(0) + " imports " + pair.get(1) + " other than through its web.entry "
                        + shown(model.component(pair.get(1)).entry().orElseThrow()) + ".", List.copyOf(evidence))));
        undeclaredPackages.forEach((pair, evidence) -> findings.add(new WebFinding(WebFindingType.UNDECLARED_PACKAGE,
                pair, pair.get(0) + " imports the package " + pair.get(1) + ", which its web.packages does not list.",
                List.copyOf(evidence))));
        for (List<String> cycle : ImportCycles.in(graph)) {
            List<String> files = cycle.stream().map(this::shown).toList();
            Set<String> members = new HashSet<>(files);
            findings.add(new WebFinding(WebFindingType.IMPORT_CYCLE, files, "These files import each other in a cycle: "
                    + String.join(", ", files) + ".", fileImports.stream()
                    .filter(item -> members.contains(item.file()) && members.contains(item.target())).toList()));
        }
        List<WebComponentSummary> components = model.components().stream().map(this::summary).toList();
        return new WebComparison(sources.root(), components, edges, findings);
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
            component.entry().filter(entry -> !kinds.containsKey(entry)).ifPresent(entry -> findings.add(
                    new WebFinding(WebFindingType.ENTRY_POINT_MISSING, List.of(component.id(), shown(entry)),
                            component.id() + " is built, but its web.entry " + shown(entry) + " does not exist.",
                            List.of())));
        }
        return new WebComponentSummary(component.id(), component.name(), component.status(), component.paths(),
                component.entry(), component.packages(), fileCount);
    }

    private ImportEvidenceItem evidence(ResolvedImport found, String target) {
        return new ImportEvidenceItem(shown(found.file()), found.line(), found.specifier(), found.kind().label(),
                target);
    }

    /** A path under the web root as reports show it: relative to the repository root ({@code web.root} is never empty). */
    private String shown(String path) {
        return sources.root() + "/" + path;
    }

    private static <K> void add(Map<K, Set<ImportEvidenceItem>> evidenceByKey, K key, ImportEvidenceItem evidence) {
        evidenceByKey.computeIfAbsent(key, _ -> new HashSet<>()).add(evidence);
    }

    /** A construct or specifier the grammar refused, by its file and line under the web root. */
    private record Refused(String file, int line, String reason, ImportEvidenceItem evidence) {
    }
}
