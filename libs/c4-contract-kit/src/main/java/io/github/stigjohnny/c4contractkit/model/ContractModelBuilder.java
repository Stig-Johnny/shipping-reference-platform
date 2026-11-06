package io.github.stigjohnny.c4contractkit.model;

import static io.github.stigjohnny.c4contractkit.model.ContractProperties.ROOT;

import com.structurizr.model.Container;
import com.structurizr.model.Model;
import com.structurizr.model.Relationship;
import com.structurizr.model.StaticStructureElementInstance;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.lang.model.SourceVersion;

/**
 * Validates a parsed workspace against the model rules of libs/c4-contract-kit/AGENTS.md and builds the
 * {@link ContractModel}. It adds its problems to those the parser found and fails once, listing them all. Without the
 * one checked container it skips only the checks that need it.
 *
 * <p>Implied relationships are disabled: a model in which the parser created one is rejected. Every relationship the
 * parser created, implied or not, is checked for misplaced contract properties.
 */
final class ContractModelBuilder {

    private final Model model;
    private final ElementNames names;
    private final Problems problems;

    ContractModelBuilder(ParsedModel parsed, Problems problems) {
        this.model = parsed.workspace().getModel();
        this.names = new ElementNames(parsed.identifiers());
        this.problems = problems;
    }

    ContractModel build() throws InvalidModelException {
        List<Relationship> parsed = List.copyOf(model.getRelationships());
        rejectImpliedRelationships(parsed);
        CheckedContainer checked = checkedContainer();
        new PropertyPlacement(checked, names, problems).check(model.getElements(), parsed);

        // The checked container has contract.root; the default is never used.
        String root = Objects.requireNonNullElse(checked.container().getProperties().get(ROOT), "");
        boolean validRoot = SourceVersion.isName(root);
        if (!validRoot) {
            problems.add(checked.name(), "contract.root '" + root + "' is not a Java package name");
        }
        Optional<String> usableRoot = validRoot ? Optional.of(root) : Optional.empty();
        List<ComponentDraft> components =
                new ComponentRules(names, problems, usableRoot).read(checked.container().getComponents());
        List<String> isolated = new IsolatedClasses(checked, problems, usableRoot).read(components);
        List<ContractRelationship> relationships =
                new RelationshipRules(checked, names, problems).read(parsed, components);
        problems.throwIfAny();
        return new ContractModel(root, isolated, components.stream().map(ComponentDraft::toContract).toList(),
                relationships);
    }

    /**
     * The DSL creates implied relationships unless the file says {@code !impliedRelationships false}, and a file can
     * switch them on again at any line. The kit cannot force the strategy, so it refuses any that were created.
     * Structurizr links a deployment instance's copy of its element's relationship to the original in the same way;
     * a copy connects two instances and is not implied.
     */
    private void rejectImpliedRelationships(List<Relationship> relationships) {
        for (Relationship relationship : relationships) {
            String linked = relationship.getLinkedRelationshipId();
            if (linked != null && !(relationship.getSource() instanceof StaticStructureElementInstance)) {
                problems.add(names.of(relationship), "implied by " + names.of(model.getRelationship(linked))
                        + "; implied relationships are disabled, so the model must say !impliedRelationships false"
                        + " and never switch them on");
            }
        }
    }

    private CheckedContainer checkedContainer() throws InvalidModelException {
        List<Container> containers = model.getElements().stream()
                .filter(Container.class::isInstance)
                .map(Container.class::cast)
                .filter(container -> container.getProperties().containsKey(ROOT))
                .toList();
        if (containers.isEmpty()) {
            throw problems.fatal("model", "no container has contract.root; exactly one must");
        }
        if (containers.size() > 1) {
            throw problems.fatal(containers.stream().map(names::of).sorted().collect(Collectors.joining(", ")),
                    "more than one container has contract.root; exactly one may");
        }
        Container container = containers.get(0);
        return new CheckedContainer(container, names.of(container));
    }
}
