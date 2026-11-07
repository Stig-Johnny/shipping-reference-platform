package io.github.stigjohnny.c4contractkit.rules;

import io.github.stigjohnny.c4contractkit.bytecode.CodeReference;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** One reference behind an edge or a violation; {@code kind} is the word reports use. */
public record EvidenceItem(String originClass, String originMember, String targetClass, String targetMember,
                           String kind, String sourceFile, int line) {

    /** Origin class, origin member, target class, target member, kind, line, then source file. */
    public static final Comparator<EvidenceItem> ORDER = Comparator.comparing(EvidenceItem::originClass)
            .thenComparing(EvidenceItem::originMember)
            .thenComparing(EvidenceItem::targetClass)
            .thenComparing(EvidenceItem::targetMember)
            .thenComparing(EvidenceItem::kind)
            .thenComparingInt(EvidenceItem::line)
            .thenComparing(EvidenceItem::sourceFile);

    static EvidenceItem of(CodeReference reference) {
        return new EvidenceItem(reference.originClass(), reference.originMember(), reference.targetClass(),
                reference.targetMember(), reference.kind().label(), reference.sourceFile(), reference.line());
    }

    /** The items without duplicates, in {@link #ORDER}. */
    static List<EvidenceItem> sorted(Collection<EvidenceItem> items) {
        return items.stream().distinct().sorted(ORDER).toList();
    }
}
