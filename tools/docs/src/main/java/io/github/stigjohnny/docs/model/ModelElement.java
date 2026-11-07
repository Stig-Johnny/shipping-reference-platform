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

    /** The checked component's {@code contract.id}, when it has one. */
    public Optional<String> contractId() {
        return Optional.ofNullable(properties.get("contract.id"));
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
