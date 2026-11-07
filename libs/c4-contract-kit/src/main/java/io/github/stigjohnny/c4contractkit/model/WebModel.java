package io.github.stigjohnny.c4contractkit.model;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The part of a C4 model that the web import check reads: the container with {@code web.root}, its components and the
 * relationships between them. Every list is immutable and sorted.
 *
 * @param root the container's {@code web.root}: the directory of its source files, relative to the repository root
 * @param container how problems and reports name the container: its DSL identifier
 * @param components its components, by id
 * @param relationships the relationships between those components, by source and then target
 */
public record WebModel(String root, String container, List<WebComponent> components,
                       List<WebRelationship> relationships) {

    public WebModel {
        components = components.stream().sorted(Comparator.comparing(WebComponent::id)).toList();
        relationships = relationships.stream()
                .sorted(Comparator.comparing(WebRelationship::source).thenComparing(WebRelationship::target))
                .toList();
    }

    /** The component with this {@code web.id}; relationships name only components of the model. */
    public WebComponent component(String id) {
        return components.stream().filter(component -> component.id().equals(id)).findFirst().orElseThrow();
    }

    /** The component a file belongs to by its directory, if any; paths do not overlap, so there is at most one. */
    public Optional<WebComponent> componentOf(String file) {
        return components.stream().filter(component -> component.owns(file)).findFirst();
    }
}
