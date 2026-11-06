package io.github.stigjohnny.c4contractkit.rules;

import io.github.stigjohnny.c4contractkit.bytecode.CodeReference;
import io.github.stigjohnny.c4contractkit.bytecode.ImportedClass;
import io.github.stigjohnny.c4contractkit.model.ComponentStatus;
import io.github.stigjohnny.c4contractkit.model.ContractComponent;
import io.github.stigjohnny.c4contractkit.model.ContractModel;
import io.github.stigjohnny.c4contractkit.model.ContractRelationship;
import io.github.stigjohnny.c4contractkit.model.RelationshipMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Compares a validated model with the imported classes, in both directions (libs/c4-contract-kit/AGENTS.md,
 * "Comparison semantics").
 *
 * <ul>
 *   <li>Classes outside {@code contract.root} are ignored as origins, and references to classes outside it (the JDK,
 *       libraries) are ignored as targets.
 *   <li>A class belongs to the component whose package root is its package or a prefix of it followed by ".".
 *   <li>Only direct references count, and references inside one component are ignored.
 *   <li>{@code package-info} declares a package, not a class: its annotations count as references, but it never
 *       makes a component non-empty, a planned component built, or a class unmapped.
 * </ul>
 */
public final class ConformanceRules {

    private final ContractModel model;
    private final Map<String, Integer> classCounts = new HashMap<>();
    private final Map<List<String>, Set<EvidenceItem>> dependencies = new HashMap<>();
    private final Map<Breach, Set<EvidenceItem>> breaches = new HashMap<>();
    private final List<Finding> findings = new ArrayList<>();

    private ConformanceRules(ContractModel model) {
        this.model = model;
    }

    /**
     * Compares the model with the classes.
     *
     * @param model a validated model
     * @param classes the imported classes, in any order
     * @return the components, the edges with their verdicts and the violations, in report order
     */
    public static Comparison compare(ContractModel model, List<ImportedClass> classes) {
        ConformanceRules rules = new ConformanceRules(model);
        classes.forEach(rules::read);
        return rules.result();
    }

    private void read(ImportedClass imported) {
        String name = imported.name();
        if (!model.isUnderRoot(name)) {
            return;
        }
        Optional<String> isolated = model.isolatedEntryOf(name);
        Optional<ContractComponent> component = model.componentOf(name);
        if (!imported.packageInfo()) {
            checkClass(name, isolated, component);
        }
        for (CodeReference reference : imported.references()) {
            checkReference(reference, isolated, component);
        }
    }

    private void checkClass(String name, Optional<String> isolated, Optional<ContractComponent> component) {
        if (component.isEmpty() && isolated.isEmpty()) {
            findings.add(new Finding(FindingType.UNMAPPED_CLASS, List.of(name), name + " is under contract.root "
                    + model.root() + ", but in no component's packages and not in contract.isolated.", List.of()));
        }
        component.ifPresent(owner -> {
            classCounts.merge(owner.id(), 1, Integer::sum);
            if (owner.status() == ComponentStatus.PLANNED) {
                findings.add(new Finding(FindingType.PLANNED_COMPONENT_HAS_CODE, List.of(owner.id(), name),
                        owner.id() + " is planned, but its packages contain " + name + ".", List.of()));
            }
        });
    }

    private void checkReference(CodeReference reference, Optional<String> isolated,
                                Optional<ContractComponent> component) {
        String target = reference.targetClass();
        if (!model.isUnderRoot(target)) {
            return;
        }
        EvidenceItem evidence = EvidenceItem.of(reference);
        Optional<ContractComponent> targetComponent = model.componentOf(target);
        if (isolated.isPresent()) {
            String entry = isolated.get();
            targetComponent.ifPresent(depended -> add(breaches, new Breach(List.of(entry, depended.id()),
                    "The isolated class " + entry + " depends on " + depended.id() + "."), evidence));
            return;
        }
        Optional<String> targetIsolated = model.isolatedEntryOf(target);
        component.ifPresent(owner -> {
            if (targetIsolated.isPresent()) {
                String entry = targetIsolated.get();
                add(breaches, new Breach(List.of(owner.id(), entry),
                        owner.id() + " depends on the isolated class " + entry + "."), evidence);
            } else {
                targetComponent.filter(depended -> !depended.equals(owner))
                        .ifPresent(depended -> add(dependencies, List.of(owner.id(), depended.id()), evidence));
            }
        });
    }

    private Comparison result() {
        Map<List<String>, Set<EvidenceItem>> undeclared = new HashMap<>(dependencies);
        List<EdgeOutcome> edges = new ArrayList<>();
        for (ContractRelationship relationship : model.relationships()) {
            Set<EvidenceItem> evidence = Objects.requireNonNullElse(
                    undeclared.remove(List.of(relationship.source(), relationship.target())), Set.of());
            edges.add(declared(relationship, evidence));
        }
        undeclared.forEach((pair, evidence) -> {
            String source = pair.get(0);
            String target = pair.get(1);
            edges.add(new EdgeOutcome(source, target, EdgeMode.UNDECLARED, EdgeVerdict.DIVERGENCE,
                    List.copyOf(evidence)));
            findings.add(new Finding(FindingType.DIVERGENCE, pair, source + " depends on " + target
                    + ", but the model declares no relationship " + source + " -> " + target + ".",
                    List.copyOf(evidence)));
        });
        breaches.forEach((breach, evidence) -> findings.add(new Finding(FindingType.ISOLATION_BREACH,
                breach.identifiers(), breach.message(), List.copyOf(evidence))));
        List<ComponentSummary> components = model.components().stream().map(this::summary).toList();
        return new Comparison(components, edges, findings);
    }

    private EdgeOutcome declared(ContractRelationship relationship, Set<EvidenceItem> evidence) {
        EdgeMode mode = relationship.mode() == RelationshipMode.REQUIRED ? EdgeMode.REQUIRED : EdgeMode.PERMITTED;
        EdgeVerdict verdict = verdict(relationship, mode, evidence);
        if (verdict == EdgeVerdict.ABSENCE) {
            findings.add(new Finding(FindingType.ABSENCE, List.of(relationship.source(), relationship.target()),
                    "The model requires " + relationship.source() + " -> " + relationship.target()
                            + ", but no class of " + relationship.source() + " references a class of "
                            + relationship.target() + ".", List.of()));
        }
        return new EdgeOutcome(relationship.source(), relationship.target(), mode, verdict, List.copyOf(evidence));
    }

    private EdgeVerdict verdict(ContractRelationship relationship, EdgeMode mode, Set<EvidenceItem> evidence) {
        if (isPlanned(relationship.source()) || isPlanned(relationship.target())) {
            return EdgeVerdict.NOT_EVALUATED;
        }
        if (!evidence.isEmpty()) {
            return EdgeVerdict.CONVERGENCE;
        }
        return mode == EdgeMode.REQUIRED ? EdgeVerdict.ABSENCE : EdgeVerdict.NO_STATIC_EVIDENCE;
    }

    private boolean isPlanned(String componentId) {
        return model.component(componentId).status() == ComponentStatus.PLANNED;
    }

    private ComponentSummary summary(ContractComponent component) {
        int classCount = classCounts.getOrDefault(component.id(), 0);
        if (component.status() == ComponentStatus.BUILT && classCount == 0) {
            findings.add(new Finding(FindingType.BUILT_COMPONENT_EMPTY, List.of(component.id()),
                    component.id() + " is built, but its packages contain no class.", List.of()));
        }
        return new ComponentSummary(component.id(), component.name(), component.status(), component.packages(),
                classCount);
    }

    private static <K> void add(Map<K, Set<EvidenceItem>> evidenceByKey, K key, EvidenceItem evidence) {
        evidenceByKey.computeIfAbsent(key, _ -> new HashSet<>()).add(evidence);
    }

    /** A breach of isolation: the depending side first, as identifiers, with its sentence. */
    private record Breach(List<String> identifiers, String message) {
    }
}
