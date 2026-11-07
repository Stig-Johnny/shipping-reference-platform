package io.github.stigjohnny.docs.model;

import java.util.List;

/**
 * One view of the C4 model with what it shows.
 *
 * @param scopeName the software system or container the view is about
 * @param elementIds the elements in the view
 * @param steps the relationships in the view, in step order for dynamic views and in model order otherwise
 */
public record C4View(String key, Kind kind, String description, String scopeName, List<String> elementIds,
        List<Step> steps) {

    public C4View {
        elementIds = List.copyOf(elementIds);
        steps = List.copyOf(steps);
    }

    /** The view kinds the documentation site renders. */
    public enum Kind {
        SYSTEM_CONTEXT, CONTAINER, COMPONENT, DYNAMIC
    }

    /**
     * A relationship as the view shows it.
     *
     * @param order the step number of a dynamic view, empty otherwise
     * @param description the view's own description of the step (dynamic views), otherwise the relationship's
     */
    public record Step(String relationshipId, String order, String description) {
    }
}
