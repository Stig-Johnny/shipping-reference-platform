package io.github.stigjohnny.docs.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** One relationship of the C4 model, in the static code direction its source declares. */
public record ModelRelationship(String id, String sourceId, String destinationId, String description,
        String technology, List<String> tags, Map<String, String> properties) {

    public ModelRelationship {
        tags = List.copyOf(tags);
        properties = Map.copyOf(properties);
    }

    /** {@code REQUIRED} or {@code PERMITTED} on a checked component-to-component relationship. */
    public Optional<String> mode() {
        return Optional.ofNullable(properties.get("contract.mode"));
    }

    public boolean planned() {
        return tags.contains(ModelElement.PLANNED);
    }

    public boolean simulated() {
        return tags.contains(ModelElement.SIMULATED);
    }
}
