package io.github.stigjohnny.c4contractkit.api;

import java.util.List;

/**
 * An edge between two components of the web container.
 *
 * @param source the importing component's {@code web.id}
 * @param target the imported component's {@code web.id}
 * @param mode how the model declares the edge; {@link Mode#UNDECLARED} for a divergence
 * @param status what the comparison found
 * @param evidence every import from a file of the source to a file of the target, sorted
 */
public record WebEdgeResult(String source, String target, Mode mode, EdgeStatus status, List<ImportEvidence> evidence) {

    /** Copies the evidence, so a result never changes after it is created. */
    public WebEdgeResult {
        evidence = List.copyOf(evidence);
    }
}
