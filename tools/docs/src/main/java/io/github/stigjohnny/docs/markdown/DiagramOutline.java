package io.github.stigjohnny.docs.markdown;

import java.util.List;

/**
 * A document's Mermaid fences and stray labels as data (ADR 0024), from {@link MarkdownRenderer#diagrams}.
 *
 * @param fences every Mermaid fence, at any depth, in document order
 * @param strayLabels the lines, counted from 1, of the unchecked-design labels that no sequence or state diagram
 *     follows in the same container
 */
public record DiagramOutline(List<MermaidFence> fences, List<Integer> strayLabels) {

    public DiagramOutline {
        fences = List.copyOf(fences);
        strayLabels = List.copyOf(strayLabels);
    }
}
