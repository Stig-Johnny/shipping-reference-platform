package io.github.stigjohnny.docs.generate;

import io.github.stigjohnny.docs.mermaid.MermaidDiagram;
import io.github.stigjohnny.docs.mermaid.MermaidDiagram.Arrow;
import io.github.stigjohnny.docs.mermaid.MermaidDiagram.Edge;
import io.github.stigjohnny.docs.model.C4Model;
import io.github.stigjohnny.docs.model.C4View;
import io.github.stigjohnny.docs.model.ModelElement;
import io.github.stigjohnny.docs.model.ModelRelationship;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The words around a diagram, shared by the committed view pages and the site: the legend, the status of each
 * element, the line of each relationship and what the view cannot show. Every diagram's text equivalent is built from
 * these.
 */
public final class ViewText {

    private ViewText() {
    }

    /** One row of the element table. */
    public record ElementRow(String id, String name, String type, String technology, String status,
            String description) {
    }

    /** One row of the relationship table, in drawing order. */
    public record RelationshipRow(String number, String from, String to, String description, String technology,
            String line) {
    }

    public static List<ElementRow> elements(C4View view, C4Model model) {
        List<ElementRow> rows = new ArrayList<>();
        for (String id : view.elementIds()) {
            ModelElement element = model.element(id);
            rows.add(new ElementRow(id, element.name(), element.type(), element.technology(), status(element),
                    element.description()));
        }
        return List.copyOf(rows);
    }

    public static List<RelationshipRow> relationships(C4View view, C4Model model, MermaidDiagram diagram) {
        List<RelationshipRow> rows = new ArrayList<>();
        int number = 0;
        boolean dynamic = view.kind() == C4View.Kind.DYNAMIC;
        for (C4View.Step step : view.steps()) {
            number++;
            ModelRelationship relationship = model.relationship(step.relationshipId());
            Edge edge = diagram.edges().stream().filter(e -> e.relationshipId().equals(relationship.id())
                    && e.label().startsWith(dynamic ? step.order() + ". " : "")).findFirst().orElseThrow();
            rows.add(new RelationshipRow(dynamic ? step.order() : Integer.toString(number),
                    model.element(relationship.sourceId()).name(), model.element(relationship.destinationId()).name(),
                    step.description(), relationship.technology(), line(edge.arrow(), relationship)));
        }
        return List.copyOf(rows);
    }

    /** An element's status in words: a component's contract status, or what its tags say. */
    public static String status(ModelElement element) {
        if (element.contractStatus().isPresent()) {
            return element.contractStatus().get();
        }
        List<String> words = new ArrayList<>();
        if (element.planned()) {
            words.add("planned");
        }
        if (element.external()) {
            words.add("external");
        }
        if (element.simulated()) {
            words.add("simulated");
        }
        return words.isEmpty() ? "no status: not a checked module" : String.join(", ", words);
    }

    /** The line pattern of a relationship and what it means, in words. */
    public static String line(Arrow arrow, ModelRelationship relationship) {
        return switch (arrow) {
            case REQUIRED -> "thick: REQUIRED dependency";
            case DOTTED -> relationship.mode().isPresent()
                    ? "dotted: PERMITTED dependency" + (relationship.planned() ? ", planned" : "")
                    : "dotted: planned";
            case SOLID -> "solid: not checked by the Java check";
        };
    }

    /** The legend: one sentence per element marking and line pattern the diagram actually uses. */
    public static List<String> legend(MermaidDiagram diagram, C4Model model) {
        Set<Arrow> arrows = EnumSet.noneOf(Arrow.class);
        diagram.edges().forEach(e -> arrows.add(e.arrow()));
        boolean permitted = diagram.edges().stream()
                .anyMatch(e -> "PERMITTED".equals(model.relationship(e.relationshipId()).mode().orElse("")));
        boolean planned = diagram.edges().stream().anyMatch(e -> e.arrow() == Arrow.DOTTED
                && model.relationship(e.relationshipId()).mode().isEmpty());
        List<String> legend = new ArrayList<>();
        legend.add("Each box is an element of the model, with its C4 type in brackets.");
        if (diagram.nodes().stream().anyMatch(n -> n.label().endsWith("(planned)"))) {
            legend.add("\"(planned)\" marks an element that has no code yet.");
        }
        if (arrows.contains(Arrow.REQUIRED)) {
            legend.add("A thick line is a REQUIRED dependency between modules: the code must reference it, and the "
                    + "build fails when it does not.");
        }
        if (permitted) {
            legend.add("A dotted line labelled PERMITTED is a dependency between modules that the code may have; "
                    + "without a reference the report says \"no static evidence\", and the build stays green.");
        }
        if (planned) {
            legend.add("A dotted line labelled Planned is a relationship that does not exist yet.");
        }
        if (arrows.contains(Arrow.SOLID)) {
            legend.add("A solid thin line is a relationship of people, systems or containers, which the Java check "
                    + "does not evaluate.");
        }
        legend.add("Arrows point from the element that depends on the other.");
        return List.copyOf(legend);
    }

    /** What a view of this kind cannot show. */
    public static List<String> limits(C4View view) {
        List<String> limits = new ArrayList<>();
        limits.add("A diagram shows the model, not the code. Only the API's modules are checked against the code "
                + "(docs/gaps.md, section 1).");
        switch (view.kind()) {
            case SYSTEM_CONTEXT, CONTAINER -> limits.add("Relationships of people, software systems and containers are "
                    + "documentation: they carry no contract mode, and Java static analysis does not check them. The "
                    + "contract and end-to-end tests cover the web app's calls.");
            case COMPONENT -> {
                limits.add("Arrows between modules are static code dependencies, not runtime data flow: an event "
                        + "listener's arrow points to the module whose event type it uses.");
                limits.add("A REQUIRED arrow proves that a reference exists, not that the behaviour behind it runs; "
                        + "reflection, configuration and SQL are invisible to the check.");
            }
            case DYNAMIC -> limits.add("Each numbered step is a relationship of the model, in the order of the flow. "
                    + "Where a listener handles another module's event, its arrow shows the code dependency, not the "
                    + "direction in which the event travels; the authored sequence diagrams under docs/flows/ "
                    + "describe runtime delivery.");
        }
        return List.copyOf(limits);
    }
}
