package io.github.stigjohnny.docs.mermaid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stigjohnny.docs.mermaid.MermaidDiagram.Arrow;
import io.github.stigjohnny.docs.mermaid.MermaidDiagram.Edge;
import io.github.stigjohnny.docs.mermaid.MermaidDiagram.Node;
import io.github.stigjohnny.docs.model.C4Model;
import io.github.stigjohnny.docs.model.C4View;
import io.github.stigjohnny.docs.model.ElementStatus;
import io.github.stigjohnny.docs.model.ParsedWorkspace;
import io.github.stigjohnny.docs.model.RawMermaid;
import io.github.stigjohnny.docs.model.Workspaces;
import io.github.stigjohnny.docs.testing.Repository;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * The adapter between Structurizr's exporter and the published definitions: the exporter's HTML labels, click
 * directives and styles never pass through as they are (security/reviews/maven--docs-tool-scope-extension.md).
 */
class MermaidAdapterTest {

    private static final ParsedWorkspace WORKSPACE =
            Workspaces.read(Repository.file("architecture/workspace.dsl"), "architecture/workspace.dsl");
    private static final C4Model MODEL = WORKSPACE.model();
    /**
     * Some product capabilities are built: the system and its containers are labelled planned only by their own
     * Planned tag, as the web app and the database are.
     */
    private static final ElementStatus STATUS = new ElementStatus(false);

    @Test
    void everyViewBecomesAValidatedPlainTextFlowchart() {
        assertThat(WORKSPACE.exports()).hasSize(6).allSatisfy(raw -> {
            MermaidDiagram diagram = MermaidAdapter.adapt(raw, MODEL, STATUS);

            assertThat(MermaidValidator.validate(diagram.definition(), raw.viewKey())).isEqualTo(diagram.definition());
            assertThat(diagram.definition()).startsWith("flowchart LR\n")
                    .doesNotContain("div", "<", "linkStyle", "subgraph diagram", "click", "opacity");
        });
    }

    @Test
    void idsBoundariesAndOrderArePreserved() {
        MermaidDiagram context = adapt("SystemContext");
        MermaidDiagram components = adapt("ApiComponents");

        assertThat(context.nodes()).extracting(Node::id).containsExactly("1", "2", "3", "4", "5");
        assertThat(context.edges()).extracting(e -> e.from() + ">" + e.to())
                .containsExactly("1>3", "1>5", "2>5", "3>5", "5>4");
        String definition = components.definition();
        assertThat(definition).contains("    subgraph 5 [\"Shipping reference platform\"]\n")
                .contains("        subgraph 7 [\"API\"]\n");
        assertThat(definition.indexOf("subgraph 7")).isGreaterThan(definition.indexOf("subgraph 5"));
    }

    @Test
    void requiredDependenciesAreThickAndPermittedOnesDotted() {
        List<Edge> edges = adapt("ApiComponents").edges();

        assertThat(edge(edges, "8", "10").arrow()).isEqualTo(Arrow.REQUIRED);
        assertThat(edge(edges, "8", "10").label()).isEqualTo("12 REQUIRED");
        assertThat(edge(edges, "11", "8").arrow()).isEqualTo(Arrow.DOTTED);
        assertThat(edge(edges, "11", "8").label()).isEqualTo("17 PERMITTED");
        assertThat(edge(edges, "12", "8").label()).isEqualTo("18 PERMITTED \u2014 planned");
        assertThat(edge(edges, "6", "10").arrow()).isEqualTo(Arrow.SOLID);
        assertThat(edge(edges, "6", "10").label()).isEqualTo("1");
        assertThat(adapt("ApiComponents").definition()).contains("    8 ==>|\"12 REQUIRED\"| 10\n");
    }

    @Test
    void plannedElementsAreLabelledInWordsInsteadOfFaded() {
        MermaidDiagram components = adapt("ApiComponents");

        assertThat(components.nodes()).filteredOn(n -> List.of("6", "8", "9", "10", "11", "15").contains(n.id()))
                .extracting(Node::label).containsExactlyInAnyOrder("Web app [Container] (planned)",
                        "Quotes [Component] (built)", "Shipments [Component] (built)", "Tenants [Component] (built)",
                        "Audit [Component] (planned)", "Database [Container] (planned)");
        MermaidDiagram context = adapt("SystemContext");
        assertThat(context.nodes()).extracting(Node::label).containsExactlyInAnyOrder("Shipping clerk [Person]",
                "Platform administrator [Person]", "Webshop [Software System] (external)",
                "Carrier (simulated) [Software System] (external, simulated)",
                "Shipping reference platform [Software System]");
        assertThat(edge(context.edges(), "3", "5").label()).isEqualTo("4 \u2014 planned");
        // While every product capability is planned, the system is labelled planned without a tag of its own.
        assertThat(MermaidAdapter.adapt(raw("SystemContext"), MODEL, new ElementStatus(true)).nodes())
                .extracting(Node::label).contains("Shipping reference platform [Software System] (planned)");
    }

    @Test
    void theDynamicViewIsNumberedAndDrawsEachElementOnce() {
        MermaidDiagram dynamic = adapt("QuoteBookLabel");

        assertThat(dynamic.nodes()).extracting(Node::id).doesNotHaveDuplicates()
                .containsExactlyInAnyOrder("1", "4", "6", "8", "9", "10", "11", "15");
        assertThat(dynamic.edges()).hasSize(15);
        assertThat(dynamic.edges().get(0).label()).isEqualTo("1");
        assertThat(dynamic.edges().get(14).label()).isEqualTo("15");
        assertThat(dynamic.edges().get(3).label()).isEqualTo("4 REQUIRED");
    }

    @Test
    void labelsAreShortTheTablesCarryTheRest() {
        for (C4View view : MODEL.views()) {
            MermaidDiagram diagram = adapt(view.key());

            assertThat(diagram.nodes()).extracting(Node::label).as(view.key())
                    .allMatch(label -> label.matches("[^\\[\\]]+ \\[(Person|Software System|Container|Component)]"
                            + "( \\((planned|built|external|external, simulated)\\))?"));
            assertThat(diagram.edges()).extracting(Edge::label).as(view.key())
                    .allMatch(label -> label.matches("\\d+( (REQUIRED|PERMITTED))?( \u2014 planned)?"));
        }
    }

    @Test
    void theNodesAndEdgesAreExactlyTheViewsElementsAndRelationships() {
        for (C4View view : MODEL.views()) {
            MermaidDiagram diagram = adapt(view.key());

            assertThat(diagram.nodes()).extracting(Node::id)
                    .containsExactlyInAnyOrderElementsOf(view.elementIds());
            assertThat(diagram.edges()).extracting(Edge::relationshipId)
                    .containsExactlyInAnyOrderElementsOf(view.steps().stream().map(C4View.Step::relationshipId)
                            .toList());
        }
    }

    @Test
    void aClickDirectiveFails() {
        assertThatThrownBy(() -> adaptChanged("SystemContext",
                raw -> raw.replace("\n  end", "\n    click 1 https://example.com \"https://example.com\"\n  end")))
                .isInstanceOf(UnsafeDiagramException.class).hasMessageContaining("SystemContext");
    }

    @Test
    void markupInALabelFails() {
        assertThatThrownBy(() -> adaptChanged("SystemContext",
                raw -> raw.replace(">Shipping clerk<", ">Shipping <script>clerk<")))
                .isInstanceOf(UnsafeDiagramException.class);
        assertThatThrownBy(() -> adaptChanged("SystemContext",
                raw -> raw.replace(">Webshop<", "><img src='https://example.com/icon.png' />Webshop<")))
                .isInstanceOf(UnsafeDiagramException.class);
    }

    @Test
    void anEdgeTheViewDoesNotHaveFails() {
        assertThatThrownBy(() -> adaptChanged("SystemContext", raw -> raw.replace("\n  end",
                "\n    1-. \"<div>Calls the carrier</div><div style='font-size: 70%'></div>\" .->4\n  end")))
                .isInstanceOf(UnsafeDiagramException.class).hasMessageContaining("1");
    }

    @Test
    void aMissingEdgeFails() {
        assertThatThrownBy(() -> adaptChanged("SystemContext", raw -> raw.lines()
                .filter(line -> !line.contains("Manages the orders"))
                .reduce("", (a, b) -> a + b + "\n")))
                .isInstanceOf(UnsafeDiagramException.class);
    }

    @Test
    void conflictingDefinitionsOfOneNodeFail() {
        assertThatThrownBy(() -> adaptChanged("QuoteBookLabel", raw -> {
            int second = raw.lastIndexOf(">Web app<");
            return raw.substring(0, second) + ">Web shop<" + raw.substring(second + ">Web app<".length());
        })).isInstanceOf(UnsafeDiagramException.class);
    }

    @Test
    void aStatementTheExporterDoesNotWriteFails() {
        assertThatThrownBy(() -> adaptChanged("SystemContext",
                raw -> raw.replace("\n  end", "\n    classDef planned fill:#ffffff\n  end")))
                .isInstanceOf(UnsafeDiagramException.class);
        assertThatThrownBy(() -> adaptChanged("SystemContext",
                raw -> raw.replace("\n  end", "\n    %%{init: {\"securityLevel\": \"loose\"}}%%\n  end")))
                .isInstanceOf(UnsafeDiagramException.class);
    }

    private static Edge edge(List<Edge> edges, String from, String to) {
        return edges.stream().filter(e -> e.from().equals(from) && e.to().equals(to)).findFirst().orElseThrow();
    }

    private static MermaidDiagram adapt(String key) {
        return MermaidAdapter.adapt(raw(key), MODEL, STATUS);
    }

    private static MermaidDiagram adaptChanged(String key, UnaryOperator<String> change) {
        RawMermaid raw = raw(key);
        return MermaidAdapter.adapt(new RawMermaid(key, change.apply(raw.definition())), MODEL, STATUS);
    }

    private static RawMermaid raw(String key) {
        return WORKSPACE.exports().stream().filter(r -> r.viewKey().equals(key)).findFirst().orElseThrow();
    }
}
