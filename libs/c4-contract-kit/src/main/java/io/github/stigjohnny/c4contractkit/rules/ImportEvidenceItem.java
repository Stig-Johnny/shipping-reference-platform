package io.github.stigjohnny.c4contractkit.rules;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * One import (or refused construct) as evidence: file and target relative to the repository root.
 *
 * @param kind the import's kind as reports show it, or {@code refused}
 * @param target the file or package the specifier names; empty when it names nothing certain
 */
public record ImportEvidenceItem(String file, int line, String specifier, String kind, String target) {

    static final Comparator<ImportEvidenceItem> ORDER = Comparator.comparing(ImportEvidenceItem::file)
            .thenComparingInt(ImportEvidenceItem::line)
            .thenComparing(ImportEvidenceItem::specifier)
            .thenComparing(ImportEvidenceItem::kind)
            .thenComparing(ImportEvidenceItem::target);

    /** The items without duplicates, by file, line, specifier, kind and target. */
    public static List<ImportEvidenceItem> sorted(Collection<ImportEvidenceItem> items) {
        return items.stream().distinct().sorted(ORDER).toList();
    }
}
