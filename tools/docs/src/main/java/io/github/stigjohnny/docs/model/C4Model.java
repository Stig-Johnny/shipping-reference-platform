package io.github.stigjohnny.docs.model;

import io.github.stigjohnny.docs.DocsException;
import java.util.List;
import java.util.Optional;

/** The parts of {@code architecture/workspace.dsl} the documentation needs, as immutable records. */
public record C4Model(String workspaceName, List<ModelElement> elements, List<ModelRelationship> relationships,
        List<C4View> views) {

    public C4Model {
        elements = List.copyOf(elements);
        relationships = List.copyOf(relationships);
        views = List.copyOf(views);
    }

    public ModelElement element(String id) {
        return elements.stream().filter(e -> e.id().equals(id)).findFirst()
                .orElseThrow(() -> new DocsException("the model has no element " + id));
    }

    public ModelRelationship relationship(String id) {
        return relationships.stream().filter(r -> r.id().equals(id)).findFirst()
                .orElseThrow(() -> new DocsException("the model has no relationship " + id));
    }

    public C4View view(String key) {
        return views.stream().filter(v -> v.key().equals(key)).findFirst()
                .orElseThrow(() -> new DocsException("the model has no view " + key));
    }

    /** The checked components (those with a {@code contract.id}), in model order. */
    public List<ModelElement> modules() {
        return elements.stream().filter(e -> "Component".equals(e.type()) && e.contractId().isPresent()).toList();
    }

    public Optional<ModelElement> module(String contractId) {
        return modules().stream().filter(m -> m.contractId().orElseThrow().equals(contractId)).findFirst();
    }

    /** Relationships whose source is the given element. */
    public List<ModelRelationship> outgoing(String elementId) {
        return relationships.stream().filter(r -> r.sourceId().equals(elementId)).toList();
    }

    /** Relationships whose destination is the given element. */
    public List<ModelRelationship> incoming(String elementId) {
        return relationships.stream().filter(r -> r.destinationId().equals(elementId)).toList();
    }
}
