package io.github.stigjohnny.c4contractkit.rules;

import java.util.List;

/** One violation of the web import check: the broken rule, what it is about, a sentence and the evidence. */
public record WebFinding(WebFindingType type, List<String> identifiers, String message,
                         List<ImportEvidenceItem> evidence) {

    public WebFinding {
        identifiers = List.copyOf(identifiers);
        evidence = ImportEvidenceItem.sorted(evidence);
    }
}
