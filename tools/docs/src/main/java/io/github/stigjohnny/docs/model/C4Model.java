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

    /** The web app's parts (components with a {@code web.id}), in model order. */
    public List<ModelElement> webParts() {
        return elements.stream().filter(e -> "Component".equals(e.type()) && e.webId().isPresent()).toList();
    }

    public Optional<ModelElement> module(String contractId) {
        return modules().stream().filter(m -> m.contractId().orElseThrow().equals(contractId)).findFirst();
    }

    /** How an actor takes part in what an element of the platform does, as the model's relationships say. */
    public enum Participation {
        /** A relationship leads from the actor to the element, directly or through one element the actor uses. */
        STARTS,
        /** A relationship leads from the element to the actor: the element relies on it. */
        RELIED_ON
    }

    /** The people and external software systems: what a use case may name as its actors, in model order. */
    public List<ModelElement> actors() {
        return elements.stream().filter(ModelElement::actor).toList();
    }

    /** The person or external software system that the model file gives this identifier. */
    public Optional<ModelElement> actor(String identifier) {
        return actors().stream().filter(a -> a.identifier().filter(identifier::equals).isPresent()).findFirst();
    }

    /**
     * The element that does a capability's work: the module with this {@code contract.id}, or else the element that the
     * model file gives this identifier, such as the web app's container, {@code web}.
     */
    public Optional<ModelElement> part(String module) {
        return module(module).or(() -> elements.stream().filter(e -> e.identifier().filter(module::equals).isPresent())
                .findFirst());
    }

    /**
     * How an actor takes part in what a part of the platform does: it starts it when a relationship leads from the
     * actor to the part, directly or through one element the actor uses (a person reaches a module through the web
     * app); the part relies on it when a relationship leads from the part to the actor. Empty when the model relates
     * them neither way.
     */
    public Optional<Participation> participation(ModelElement actor, ModelElement part) {
        boolean starts = outgoing(actor.id()).stream().anyMatch(r -> r.destinationId().equals(part.id())
                || outgoing(r.destinationId()).stream().anyMatch(next -> next.destinationId().equals(part.id())));
        if (starts) {
            return Optional.of(Participation.STARTS);
        }
        return outgoing(part.id()).stream().anyMatch(r -> r.destinationId().equals(actor.id()))
                ? Optional.of(Participation.RELIED_ON) : Optional.empty();
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
