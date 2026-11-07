package io.github.stigjohnny.c4contractkit.model;

import static io.github.stigjohnny.c4contractkit.model.ContractProperties.MODE;
import static io.github.stigjohnny.c4contractkit.model.ContractProperties.REQUIRED_TAG;

import com.structurizr.model.Element;
import com.structurizr.model.Relationship;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The rules for relationships: contract.mode and the Required tag, at most one relationship per ordered pair of
 * components, and no cycle among the components.
 */
final class RelationshipRules {

    private final CheckedContainer checked;
    private final ElementNames names;
    private final Problems problems;

    RelationshipRules(CheckedContainer checked, ElementNames names, Problems problems) {
        this.checked = checked;
        this.names = names;
        this.problems = problems;
    }

    List<ContractRelationship> read(Collection<Relationship> relationships, List<ComponentDraft> components) {
        Map<Element, ComponentDraft> byElement =
                components.stream().collect(Collectors.toMap(ComponentDraft::element, Function.identity()));
        Map<String, Integer> perPair = new TreeMap<>();
        Map<String, Collection<String>> edges = new TreeMap<>();
        List<ContractRelationship> contract = new ArrayList<>();
        for (Relationship relationship : relationships) {
            String name = names.of(relationship);
            if (!checked.connectsTwoComponents(relationship)) {
                if (relationship.hasTag(REQUIRED_TAG)) {
                    problems.add(name, "the tag Required is only allowed on relationships between two components of "
                            + checked.name());
                }
                continue;
            }
            perPair.merge(name, 1, Integer::sum);
            edges.computeIfAbsent(names.of(relationship.getSource()), _ -> new TreeSet<>())
                    .add(names.of(relationship.getDestination()));
            ComponentDraft source = byElement.get(relationship.getSource());
            ComponentDraft target = byElement.get(relationship.getDestination());
            mode(name, relationship, relationship.getProperties().get(MODE)).ifPresent(mode -> {
                if (source != null && target != null) {
                    source.id().ifPresent(sourceId -> target.id().ifPresent(
                            targetId -> contract.add(new ContractRelationship(sourceId, targetId, mode))));
                }
            });
        }
        perPair.forEach((pair, count) -> {
            if (count > 1) {
                problems.add(pair, count + " relationships connect these components; at most one may");
            }
        });
        for (List<String> cycle : ComponentCycles.in(edges)) {
            problems.add(String.join(", ", cycle), "the component relationships form a cycle");
        }
        return contract;
    }

    private Optional<RelationshipMode> mode(String name, Relationship relationship, @Nullable String value) {
        Optional<RelationshipMode> mode = value == null ? Optional.of(RelationshipMode.PERMITTED) : switch (value) {
            case "REQUIRED" -> Optional.of(RelationshipMode.REQUIRED);
            case "PERMITTED" -> Optional.of(RelationshipMode.PERMITTED);
            default -> Optional.empty();
        };
        if (mode.isEmpty()) {
            problems.add(name, "contract.mode '" + value + "' is neither REQUIRED nor PERMITTED");
        }
        boolean tagged = relationship.hasTag(REQUIRED_TAG);
        if (mode.equals(Optional.of(RelationshipMode.REQUIRED)) && !tagged) {
            problems.add(name, "contract.mode is REQUIRED, but the tag Required is missing");
        }
        if (mode.equals(Optional.of(RelationshipMode.PERMITTED)) && tagged) {
            problems.add(name, "the tag Required is present, but contract.mode is PERMITTED");
        }
        return mode;
    }
}
