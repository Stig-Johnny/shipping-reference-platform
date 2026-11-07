package io.github.stigjohnny.c4contractkit.rules;

import java.util.List;

/** One violation: the broken rule, what it is about, a sentence for people, and the evidence. */
public record Finding(FindingType type, List<String> identifiers, String message, List<EvidenceItem> evidence) {

    public Finding {
        identifiers = List.copyOf(identifiers);
        evidence = EvidenceItem.sorted(evidence);
    }
}
