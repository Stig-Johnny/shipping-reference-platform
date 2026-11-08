package io.github.stigjohnny.c4contractkit.rules;

import java.util.List;

/** An edge between two web components with its verdict and the imports behind it. */
public record WebEdgeOutcome(String source, String target, EdgeMode mode, EdgeVerdict verdict,
                             List<ImportEvidenceItem> evidence) {

    public WebEdgeOutcome {
        evidence = ImportEvidenceItem.sorted(evidence);
    }
}
