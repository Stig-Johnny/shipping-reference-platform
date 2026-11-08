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
 * @param testRoots directories of tests only, relative to the repository root, sorted
 * @param publicRoots directories of files served as they are, relative to the repository root, sorted
 * @param testBuiltins the built-in modules of Node.js that tests may import, written with {@code node:}, sorted
 * @param testPackages the npm packages that tests may import, sorted
 */
public record WebModel(String root, String container, List<WebComponent> components,
                       List<WebRelationship> relationships, List<String> testRoots, List<String> publicRoots,
                       List<String> testBuiltins, List<String> testPackages) {

    public WebModel {
        components = components.stream().sorted(Comparator.comparing(WebComponent::id)).toList();
        relationships = relationships.stream()
                .sorted(Comparator.comparing(WebRelationship::source).thenComparing(WebRelationship::target))
                .toList();
        testRoots = testRoots.stream().sorted().toList();
        publicRoots = publicRoots.stream().sorted().toList();
        testBuiltins = testBuiltins.stream().sorted().toList();
        testPackages = testPackages.stream().sorted().toList();
    }

    /**
     * The app's directory, relative to the repository root: the directory above {@code web.root}, which holds the
     * app's configuration and names it in the dependency allowlist; empty when the web root is at the top.
     */
    public String app() {
        int slash = root.lastIndexOf('/');
        return slash < 0 ? "" : root.substring(0, slash);
    }

    /** The relationship from one component to another, if the model declares it. */
    public Optional<WebRelationship> relationship(String source, String target) {
        return relationships.stream().filter(relationship -> relationship.source().equals(source)
                && relationship.target().equals(target)).findFirst();
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
