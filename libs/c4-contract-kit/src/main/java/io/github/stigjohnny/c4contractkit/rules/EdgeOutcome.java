package io.github.stigjohnny.c4contractkit.rules;

import java.util.List;

/** An edge between two components with its verdict and the evidence for it. */
public record EdgeOutcome(String source, String target, EdgeMode mode, EdgeVerdict verdict,
                          List<EvidenceItem> evidence) {

    public EdgeOutcome {
        evidence = EvidenceItem.sorted(evidence);
    }
}
