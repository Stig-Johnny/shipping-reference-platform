package io.github.stigjohnny.c4contractkit.api;

import java.util.List;

/**
 * One edge between two components: a relationship the model declares, or a dependency the model does not declare.
 *
 * @param source the {@code contract.id} of the depending component
 * @param target the {@code contract.id} of the component depended on
 * @param mode how the model declares the edge
 * @param status what the comparison found
 * @param evidence the direct static references from classes of the source to classes of the target, sorted
 */
public record EdgeResult(String source, String target, Mode mode, EdgeStatus status, List<Evidence> evidence) {

    /** Copies the evidence list, so a result never changes after it is created. */
    public EdgeResult {
        evidence = List.copyOf(evidence);
    }
}
