package io.github.stigjohnny.docs.check;

import io.github.stigjohnny.docs.markdown.DiagramOutline;
import io.github.stigjohnny.docs.markdown.MarkdownRenderer;
import io.github.stigjohnny.docs.markdown.MermaidFence;
import java.util.Optional;

/**
 * The behavioural diagrams of current prose (ADR 0024). No test reads a diagram written in Markdown, so a sequence or
 * state diagram there is compared with the code by none, and must say so:
 *
 * <ul>
 *   <li>the block right before its fence, in the same container, is the label: a paragraph whose first inline is
 *       strong emphasis that holds exactly {@code Unchecked design.}, which GitHub shows in bold above the drawing and
 *       the site shows on the diagram's closed source;
 *   <li>a label that no sequence or state diagram follows fails, so that no label stands on a flowchart or on nothing;
 *   <li>a Mermaid fence, whose info string's first word is {@code mermaid} in any letter case, has the info string
 *       {@code mermaid} alone, and its first non-blank line names its type exactly: {@code flowchart} or {@code graph}
 *       with an optional direction, {@code sequenceDiagram}, {@code stateDiagram} or {@code stateDiagram-v2}. Later
 *       lines are free. A type that cannot be read cannot be checked.
 * </ul>
 *
 * <p>Fences in lists and quotes count. Flowcharts are not read as behaviour, nor are files other than current prose
 * ({@link CurrentProse}): generated files, records and superseded ADRs. The model's dynamic views are generated with the
 * label, and no test compares one in full with the code yet (issue 153).
 */
final class BehaviouralDiagrams {

    private static final String ADR = " (ADR 0024)";

    private BehaviouralDiagrams() {
    }

    /** Checks one document of current prose: its text after the frontmatter, which {@code offset} lines precede. */
    static void check(String path, String body, int offset, Problems problems) {
        DiagramOutline outline = MarkdownRenderer.diagrams(body, path);
        for (MermaidFence fence : outline.fences()) {
            String where = path + ":" + (fence.line() + offset);
            if (!MermaidFence.INFO.equals(fence.info())) {
                problems.add(where + ": write the info string of a Mermaid fence as mermaid alone, not '" + fence.info()
                        + "'" + ADR);
            }
            if (fence.firstLine().isEmpty()) {
                problems.add(where + ": the Mermaid fence is empty, so it names no type" + ADR);
                continue;
            }
            Optional<MermaidFence.Type> type = fence.type();
            if (type.isEmpty()) {
                problems.add(where + ": the first line of a Mermaid diagram names its type, one of flowchart, graph, "
                        + "sequenceDiagram, stateDiagram and stateDiagram-v2, and '" + fence.firstLine() + "' is none"
                        + ADR);
            } else if (type.get().behavioural() && !fence.labelled()) {
                problems.add(where + ": no test compares this " + (type.get() == MermaidFence.Type.SEQUENCE ? "sequence"
                        : "state") + " diagram with the code; write a paragraph that starts with **Unchecked design.** "
                        + "right before it" + ADR);
            }
        }
        for (int line : outline.strayLabels()) {
            problems.add(path + ":" + (line + offset) + ": **Unchecked design.** labels the sequence or state diagram "
                    + "right after it, and none follows" + ADR);
        }
    }
}
