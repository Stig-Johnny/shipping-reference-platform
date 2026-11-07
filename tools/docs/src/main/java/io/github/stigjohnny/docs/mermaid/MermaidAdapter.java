package io.github.stigjohnny.docs.mermaid;

import io.github.stigjohnny.docs.mermaid.MermaidDiagram.Arrow;
import io.github.stigjohnny.docs.mermaid.MermaidDiagram.Edge;
import io.github.stigjohnny.docs.mermaid.MermaidDiagram.Node;
import io.github.stigjohnny.docs.mermaid.RawExport.Close;
import io.github.stigjohnny.docs.mermaid.RawExport.EdgeDef;
import io.github.stigjohnny.docs.mermaid.RawExport.NodeDef;
import io.github.stigjohnny.docs.mermaid.RawExport.Open;
import io.github.stigjohnny.docs.mermaid.RawExport.Statement;
import io.github.stigjohnny.docs.mermaid.RawExport.Style;
import io.github.stigjohnny.docs.model.C4Model;
import io.github.stigjohnny.docs.model.C4View;
import io.github.stigjohnny.docs.model.ElementStatus;
import io.github.stigjohnny.docs.model.ModelElement;
import io.github.stigjohnny.docs.model.ModelRelationship;
import io.github.stigjohnny.docs.model.RawMermaid;
import io.github.stigjohnny.docs.model.ViewNames;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The validating adapter between Structurizr's {@code MermaidDiagramExporter} and everything published. The exporter's
 * raw output is never published: it writes HTML labels, raw {@code click} URLs and unescaped text, and expects
 * Mermaid's loose security mode (security/reviews/maven--docs-tool-scope-extension.md).
 *
 * <p>The adapter reads the export statement by statement ({@link RawExport}), checks every node and edge against the
 * model view it came from, and writes a new flowchart with the same IDs, boundaries, directions and order:
 *
 * <ul>
 *   <li>node labels are plain text: the element's name, its C4 type in brackets and its implementation status in
 *       parentheses ({@link ElementStatus}); technology and descriptions stay outside the nodes, in the tables;
 *   <li>planned elements say "(planned)" instead of being faded, and line patterns show the contract: a thick line is a
 *       REQUIRED dependency, a dotted line a PERMITTED or planned one, a solid line a relationship that module
 *       conformance does not cover; each edge label is the relationship's number in the table, its mode and
 *       "planned" in words;
 *   <li>the exporter's frame and default link style are dropped, a node or style the exporter writes twice must equal
 *       its first definition and is written once, and
 *       {@code accTitle} and {@code accDescr} name the view for screen readers.
 * </ul>
 *
 * <p>The result must pass {@link MermaidValidator}; the adapter never returns a definition that does not.
 */
public final class MermaidAdapter {

    private static final String INDENT = "    ";

    private MermaidAdapter() {
    }

    public static MermaidDiagram adapt(RawMermaid raw, C4Model model, ElementStatus status) {
        String where = "view " + raw.viewKey();
        C4View view = model.view(raw.viewKey());
        RawExport.Export export = RawExport.parse(raw.definition(), where);
        List<C4View.Step> unmatched = new ArrayList<>(view.steps());
        Map<String, NodeDef> nodes = new HashMap<>();
        Map<String, String> styles = new HashMap<>();
        List<Node> drawnNodes = new ArrayList<>();
        List<Edge> drawnEdges = new ArrayList<>();
        StringBuilder out = new StringBuilder("flowchart ").append(export.direction()).append('\n');
        out.append(INDENT).append("accTitle: ").append(ViewNames.title(view)).append('\n');
        out.append(INDENT).append("accDescr: ").append(ViewNames.kind(view)).append(" with ")
                .append(view.elementIds().size()).append(" elements and ").append(view.steps().size())
                .append(" relationships. The tables on the page list every one of them.\n");
        Deque<String> open = new ArrayDeque<>();
        for (Statement statement : export.statements()) {
            String indent = INDENT.repeat(1 + (int) open.stream().filter(id -> !RawExport.FRAME.equals(id)).count());
            switch (statement) {
                case Open boundary -> {
                    if (!RawExport.FRAME.equals(boundary.id())) {
                        checkBoundary(boundary, model, where);
                        out.append(indent).append("subgraph ").append(boundary.id()).append(" [\"")
                                .append(boundary.label()).append("\"]\n");
                    }
                    open.push(boundary.id());
                }
                case Close ignored -> {
                    if (open.isEmpty()) {
                        throw new UnsafeDiagramException(where + ": 'end' without a subgraph");
                    }
                    if (!RawExport.FRAME.equals(open.pop())) {
                        out.append(INDENT.repeat((int) open.stream().filter(id -> !RawExport.FRAME.equals(id))
                                .count() + 1)).append("end\n");
                    }
                }
                case Style style -> {
                    if (style.id().isEmpty() || RawExport.FRAME.equals(style.id())) {
                        continue;
                    }
                    String first = styles.putIfAbsent(style.id(), style.pairs());
                    if (first == null) {
                        out.append(indent).append("style ").append(style.id()).append(' ').append(style.pairs())
                                .append('\n');
                    } else if (!first.equals(style.pairs())) {
                        throw new UnsafeDiagramException(where + ": element " + style.id()
                                + " is styled twice with different colours");
                    }
                }
                case NodeDef node -> {
                    NodeDef first = nodes.putIfAbsent(node.id(), node);
                    if (first == null) {
                        String label = nodeLabel(node, view, model, status, where);
                        drawnNodes.add(new Node(node.id(), label));
                        out.append(indent).append(node.id()).append(node.cylinder() ? "[(\"" : "[\"").append(label)
                                .append(node.cylinder() ? "\")]" : "\"]").append('\n');
                    } else if (!first.equals(node)) {
                        throw new UnsafeDiagramException(where + ": element " + node.id()
                                + " is drawn twice with different labels");
                    }
                }
                case EdgeDef edge -> {
                    Edge drawn = edge(edge, view, model, unmatched, where);
                    drawnEdges.add(drawn);
                    out.append(INDENT).append(drawn.from()).append(' ').append(drawn.arrow().token()).append("|\"")
                            .append(drawn.label()).append("\"| ").append(drawn.to()).append('\n');
                }
            }
        }
        if (!open.isEmpty()) {
            throw new UnsafeDiagramException(where + ": a subgraph is not closed");
        }
        if (!unmatched.isEmpty()) {
            throw new UnsafeDiagramException(where + ": the export lacks relationship "
                    + unmatched.getFirst().relationshipId() + " of the view");
        }
        String definition = MermaidValidator.validate(out.toString(), where);
        return new MermaidDiagram(view.key(), definition, drawnNodes, drawnEdges);
    }

    private static void checkBoundary(Open boundary, C4Model model, String where) {
        ModelElement element = model.element(boundary.id());
        if (!element.name().equals(boundary.label())) {
            throw new UnsafeDiagramException(where + ": boundary " + boundary.id() + " is not labelled with its name");
        }
    }

    private static String nodeLabel(NodeDef node, C4View view, C4Model model, ElementStatus status, String where) {
        if (!view.elementIds().contains(node.id())) {
            throw new UnsafeDiagramException(where + ": element " + node.id() + " is not in the view");
        }
        ModelElement element = model.element(node.id());
        if (!normalised(element.name()).equals(node.name())) {
            throw new UnsafeDiagramException(where + ": element " + node.id() + " is not labelled with its name");
        }
        String word = status.label(element);
        return node.name() + " [" + element.type() + "]" + (word.isEmpty() ? "" : " (" + word + ")");
    }

    /** Matches an exported edge with the first unused step of the view that it draws, and labels it in words. */
    private static Edge edge(EdgeDef edge, C4View view, C4Model model, List<C4View.Step> unmatched, String where) {
        boolean dynamic = view.kind() == C4View.Kind.DYNAMIC;
        for (C4View.Step step : unmatched) {
            ModelRelationship relationship = model.relationship(step.relationshipId());
            String expected = normalised((dynamic ? step.order() + ". " : "") + step.description());
            String technology = relationship.technology().isBlank() ? "" : "[" + relationship.technology() + "]";
            if (relationship.sourceId().equals(edge.from()) && relationship.destinationId().equals(edge.to())
                    && expected.equals(edge.description()) && normalised(technology).equals(edge.technology())) {
                unmatched.remove(step);
                String number = dynamic ? step.order() : Integer.toString(view.steps().indexOf(step) + 1);
                return new Edge(edge.from(), edge.to(), arrow(relationship), label(number, relationship),
                        relationship.id(), number);
            }
        }
        throw new UnsafeDiagramException(where + ": the export has an edge " + edge.from() + " to " + edge.to()
                + " that the view does not have");
    }

    private static Arrow arrow(ModelRelationship relationship) {
        String mode = relationship.mode().orElse("");
        if ("REQUIRED".equals(mode)) {
            return Arrow.REQUIRED;
        }
        return "PERMITTED".equals(mode) || relationship.planned() ? Arrow.DOTTED : Arrow.SOLID;
    }

    /** An edge's label: its number in the relationship table, its mode, and whether it is planned. */
    private static String label(String number, ModelRelationship relationship) {
        String mode = relationship.mode().orElse("");
        return number + (mode.isEmpty() ? "" : " " + mode) + (relationship.planned() ? " \u2014 planned" : "");
    }

    private static String normalised(String text) {
        return text.replaceAll("\\s+", " ").strip();
    }
}
