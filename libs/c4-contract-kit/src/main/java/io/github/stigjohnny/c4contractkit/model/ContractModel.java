package io.github.stigjohnny.c4contractkit.model;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The part of a C4 model that the contract checks: the checked container's package root, its isolated classes, its
 * components and the relationships between them. Every list is immutable and sorted.
 *
 * @param root the container's {@code contract.root}
 * @param isolated the container's {@code contract.isolated} classes
 * @param components the container's components, by id
 * @param relationships the relationships between those components, by source and then target
 */
public record ContractModel(String root, List<String> isolated, List<ContractComponent> components,
                            List<ContractRelationship> relationships) {

    public ContractModel {
        isolated = isolated.stream().sorted().toList();
        components = components.stream().sorted(Comparator.comparing(ContractComponent::id)).toList();
        relationships = relationships.stream()
                .sorted(Comparator.comparing(ContractRelationship::source).thenComparing(ContractRelationship::target))
                .toList();
    }

    /** The component with this {@code contract.id}; relationships name only components of the model. */
    public ContractComponent component(String id) {
        return components.stream().filter(component -> component.id().equals(id)).findFirst().orElseThrow();
    }

    /** The component a class belongs to by its package, if any. */
    public Optional<ContractComponent> componentOf(String className) {
        String packageName = className.substring(0, Math.max(0, className.lastIndexOf('.')));
        return components.stream().filter(component -> component.owns(packageName)).findFirst();
    }

    /** The {@code contract.isolated} entry that covers a class: the class itself or the class it is nested in. */
    public Optional<String> isolatedEntryOf(String className) {
        return isolated.stream()
                .filter(entry -> className.equals(entry) || className.startsWith(entry + "$"))
                .findFirst();
    }

    /** Whether a class lies under {@code contract.root}. */
    public boolean isUnderRoot(String className) {
        return className.startsWith(root + ".");
    }
}
