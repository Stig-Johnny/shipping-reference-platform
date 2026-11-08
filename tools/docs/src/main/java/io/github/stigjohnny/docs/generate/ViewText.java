package io.github.stigjohnny.docs.generate;

import io.github.stigjohnny.docs.mermaid.MermaidDiagram;
import io.github.stigjohnny.docs.mermaid.MermaidDiagram.Arrow;
import io.github.stigjohnny.docs.mermaid.MermaidDiagram.Edge;
import io.github.stigjohnny.docs.model.C4Model;
import io.github.stigjohnny.docs.model.C4View;
import io.github.stigjohnny.docs.model.ElementStatus;
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

    public static List<ElementRow> elements(C4View view, C4Model model, ElementStatus status) {
        List<ElementRow> rows = new ArrayList<>();
        for (String id : view.elementIds()) {
            ModelElement element = model.element(id);
            rows.add(new ElementRow(id, element.name(), element.type(), element.technology(), status.words(element),
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
            String rowNumber = dynamic ? step.order() : Integer.toString(number);
            Edge edge = diagram.edges().stream().filter(e -> e.relationshipId().equals(relationship.id())
                    && e.number().equals(rowNumber)).findFirst().orElseThrow();
            rows.add(new RelationshipRow(rowNumber,
                    model.element(relationship.sourceId()).name(), model.element(relationship.destinationId()).name(),
                    step.description(), relationship.technology(), line(edge.arrow(), relationship)));
        }
        return List.copyOf(rows);
    }

    /** The line pattern of a relationship and what it means, in words. */
    public static String line(Arrow arrow, ModelRelationship relationship) {
        return switch (arrow) {
            case REQUIRED -> "thick: REQUIRED dependency";
            case DOTTED -> relationship.mode().isPresent()
                    ? "dotted: PERMITTED dependency" + (relationship.planned() ? ", planned" : "")
                    : "dotted: planned";
            case SOLID -> "solid: not covered by module conformance";
        };
    }

    /**
     * The legend: one sentence per element marking and line pattern the diagram actually uses, and what the checker in
     * libs/c4-contract-kit does with the lines between modules.
     */
    public static List<String> legend(MermaidDiagram diagram, C4Model model) {
        Set<Arrow> arrows = EnumSet.noneOf(Arrow.class);
        diagram.edges().forEach(e -> arrows.add(e.arrow()));
        boolean permitted = diagram.edges().stream()
                .anyMatch(e -> "PERMITTED".equals(model.relationship(e.relationshipId()).mode().orElse("")));
        boolean planned = diagram.edges().stream().anyMatch(e -> e.arrow() == Arrow.DOTTED
                && model.relationship(e.relationshipId()).mode().isEmpty());
        boolean plannedModules = diagram.edges().stream().map(e -> model.relationship(e.relationshipId()))
                .anyMatch(r -> r.mode().isPresent() && (model.element(r.sourceId()).planned()
                        || model.element(r.destinationId()).planned()));
        boolean web = diagram.edges().stream()
                .anyMatch(e -> model.relationship(e.relationshipId()).webMode().isPresent());
        List<String> legend = new ArrayList<>();
        legend.add("Each box is an element of the model: its name, its C4 type in brackets and, in parentheses, its "
                + "implementation status.");
        if (diagram.nodes().stream().anyMatch(n -> n.label().endsWith("(planned)"))) {
            legend.add("\"(planned)\" marks an element that has no code yet.");
        }
        legend.add("Each line is labelled with its number in the view's relationships table and, where it has one, its "
                + "mode.");
        if (arrows.contains(Arrow.REQUIRED) && web) {
            legend.add("A thick line is a REQUIRED import between parts of the web app. Where both parts are built, "
                    + "the partial web import check in libs/c4-contract-kit fails without a detected direct import "
                    + "from one to the other.");
        } else if (arrows.contains(Arrow.REQUIRED)) {
            legend.add("A thick line is a REQUIRED dependency between modules. Where both modules are built, the "
                    + "checker in libs/c4-contract-kit fails when the compiled classes have no static reference for "
                    + "it.");
        }
        if (permitted && web) {
            legend.add("A dotted line labelled PERMITTED is an import between parts of the web app that the sources "
                    + "may have; the check requires none for it and reports one without a detected import as \"no static "
                    + "evidence\".");
        } else if (permitted) {
            legend.add("A dotted line labelled PERMITTED is a dependency between modules that the code may have; the "
                    + "checker requires no reference for it and reports one without any as \"no static evidence\".");
        }
        if (plannedModules && web) {
            legend.add("The check does not evaluate a line that touches a planned part until both parts are built; a "
                    + "file in a planned part fails it.");
        } else if (plannedModules) {
            legend.add("The checker does not evaluate a line that touches a planned module until both modules are "
                    + "built.");
        }
        if (planned) {
            legend.add("A dotted line labelled planned is a relationship that does not exist yet.");
        }
        if (arrows.contains(Arrow.SOLID)) {
            legend.add("A solid thin line is a relationship of people, systems or containers, which module conformance "
                    + "does not cover.");
        }
        legend.add("Arrows point from the element that depends on the other.");
        return List.copyOf(legend);
    }

    /** What a view of this kind cannot show. */
    public static List<String> limits(C4View view, C4Model model) {
        List<String> limits = new ArrayList<>();
        limits.add("A diagram shows the model, not the code. The API's modules are checked against its compiled "
                + "classes and the web app's parts against detected TypeScript imports using partial lint "
                + "(docs/gaps.md, section 1).");
        boolean web = view.elementIds().stream().anyMatch(id -> model.element(id).webId().isPresent());
        if (view.kind() == C4View.Kind.COMPONENT && web) {
            limits.add("Arrows between the web app's parts are static imports of TypeScript files, read from "
                    + "apps/shipping-web/src with a restricted import grammar, not runtime calls.");
            limits.add("A REQUIRED arrow specifies an obligation; where both parts are built, a passing web import "
                    + "check establishes a detected direct import, not complete boundary or cycle coverage or runtime "
                    + "behaviour. The API adapter's calls to the API are "
                    + "network calls, which no static check sees.");
            limits.add("This is labelled partial lint over detected imports; spread-syntax bypasses remain open. "
                    + "The authoritative tsc import graph and browser-enforced strict Content-Security-Policy "
                    + "are planned in the web app pull request (ADR 0013).");
            return List.copyOf(limits);
        }
        switch (view.kind()) {
            case SYSTEM_CONTEXT, CONTAINER -> limits.add("Relationships of people, software systems and containers are "
                    + "documentation: they carry no contract mode, and module conformance does not cover them. The "
                    + "planned contract and end-to-end tests are to cover the web app's calls.");
            case COMPONENT -> {
                limits.add("Arrows between modules are static code dependencies, not runtime data flow: an event "
                        + "listener's arrow points to the module whose event type it uses.");
                limits.add("A REQUIRED arrow specifies an obligation; where both modules are built, a passing "
                        + "conformance check establishes a static reference, not runtime behaviour.");
            }
            case DYNAMIC -> limits.add("Each numbered step is a relationship of the model, in the order of the flow. "
                    + "Where a listener handles another module's event, its arrow shows the code dependency, not the "
                    + "direction in which the event travels; the authored sequence diagrams under docs/flows/ "
                    + "describe runtime delivery.");
        }
        return List.copyOf(limits);
    }
}
