package io.github.stigjohnny.docs.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * One element of the C4 model.
 *
 * @param id Structurizr's element ID, which the Mermaid export uses as the node ID
 * @param type {@code Person}, {@code Software System}, {@code Container} or {@code Component}
 * @param parentId the enclosing software system or container, absent for people and software systems
 */
public record ModelElement(String id, String name, String type, String description, String technology,
        List<String> tags, Map<String, String> properties, @Nullable String parentId) {

    public static final String PLANNED = "Planned";
    public static final String EXTERNAL = "External";
    public static final String SIMULATED = "Simulated";
    /** The property in which Structurizr's DSL parser keeps the identifier that the model file gives an element. */
    private static final String IDENTIFIER = "structurizr.dsl.identifier";

    public ModelElement {
        tags = List.copyOf(tags);
        properties = Map.copyOf(properties);
    }

    public boolean planned() {
        return tags.contains(PLANNED);
    }

    public boolean external() {
        return tags.contains(EXTERNAL);
    }

    public boolean simulated() {
        return tags.contains(SIMULATED);
    }

    /** The identifier the model file gives the element, such as {@code clerk}, when it gives one. */
    public Optional<String> identifier() {
        return Optional.ofNullable(properties.get(IDENTIFIER));
    }

    /** Whether the element can be an actor of a use case: a person, or a software system outside the platform. */
    public boolean actor() {
        return "Person".equals(type) || ("Software System".equals(type) && external());
    }

    /** The checked component's {@code contract.id}, when it has one. */
    public Optional<String> contractId() {
        return Optional.ofNullable(properties.get("contract.id"));
    }

    /** A web part's {@code web.id}, when the element is a component of the web container. */
    public Optional<String> webId() {
        return Optional.ofNullable(properties.get("web.id"));
    }

    /** A web part's {@code web.status}. */
    public Optional<String> webStatus() {
        return Optional.ofNullable(properties.get("web.status"));
    }

    public Optional<String> contractStatus() {
        return Optional.ofNullable(properties.get("contract.status"));
    }

    public List<String> contractPackages() {
        String packages = properties.get("contract.packages");
        if (packages == null || packages.isBlank()) {
            return List.of();
        }
        return List.of(packages.split(",", -1)).stream().map(String::strip).filter(p -> !p.isEmpty()).toList();
    }
}
