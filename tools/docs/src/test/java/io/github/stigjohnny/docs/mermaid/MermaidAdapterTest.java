package io.github.stigjohnny.docs.mermaid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stigjohnny.docs.mermaid.MermaidDiagram.Arrow;
import io.github.stigjohnny.docs.mermaid.MermaidDiagram.Edge;
import io.github.stigjohnny.docs.mermaid.MermaidDiagram.Node;
import io.github.stigjohnny.docs.model.C4Model;
import io.github.stigjohnny.docs.model.C4View;
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

    @Test
    void everyViewBecomesAValidatedPlainTextFlowchart() {
        assertThat(WORKSPACE.exports()).hasSize(4).allSatisfy(raw -> {
            MermaidDiagram diagram = MermaidAdapter.adapt(raw, MODEL);

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
        assertThat(edge(edges, "8", "10").label())
                .isEqualTo("REQUIRED: Resolves the caller's tenant (tenants.api: CurrentTenant, TenantId) [Java]");
        assertThat(edge(edges, "11", "8").arrow()).isEqualTo(Arrow.DOTTED);
        assertThat(edge(edges, "11", "8").label()).startsWith("PERMITTED: May record QuoteIssued events");
        assertThat(edge(edges, "12", "8").label()).startsWith("PERMITTED, planned: Will request quotes");
        assertThat(edge(edges, "6", "10").arrow()).isEqualTo(Arrow.SOLID);
        assertThat(adapt("ApiComponents").definition()).contains("    8 ==>|\"REQUIRED: Resolves the caller's");
    }

    @Test
    void plannedElementsAreLabelledInWordsInsteadOfFaded() {
        MermaidDiagram components = adapt("ApiComponents");

        assertThat(components.nodes()).filteredOn(n -> n.id().equals("8")).extracting(Node::label)
                .containsExactly("Quotes [Component: Spring Modulith module] (planned)");
        assertThat(adapt("SystemContext").nodes()).filteredOn(n -> n.id().equals("1")).extracting(Node::label)
                .containsExactly("Shipping clerk [Person]");
        assertThat(edge(adapt("SystemContext").edges(), "3", "5").label())
                .isEqualTo("Planned: Will submit orders to ship (planned: orders module) [JSON over HTTPS]");
    }

    @Test
    void theDynamicViewIsNumberedAndDrawsEachElementOnce() {
        MermaidDiagram dynamic = adapt("QuoteBookLabel");

        assertThat(dynamic.nodes()).extracting(Node::id).doesNotHaveDuplicates()
                .containsExactlyInAnyOrder("1", "4", "6", "8", "9", "10", "11", "15");
        assertThat(dynamic.edges()).hasSize(15);
        assertThat(dynamic.edges().get(0).label()).startsWith("1. Prices a parcel, books an offer");
        assertThat(dynamic.edges().get(14).label()).startsWith("15. Renders the simulated SVG label");
        assertThat(dynamic.edges().get(3).label()).isEqualTo("4. REQUIRED: Resolves the caller's tenant [Java]");
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
        return MermaidAdapter.adapt(raw(key), MODEL);
    }

    private static MermaidDiagram adaptChanged(String key, UnaryOperator<String> change) {
        RawMermaid raw = raw(key);
        return MermaidAdapter.adapt(new RawMermaid(key, change.apply(raw.definition())), MODEL);
    }

    private static RawMermaid raw(String key) {
        return WORKSPACE.exports().stream().filter(r -> r.viewKey().equals(key)).findFirst().orElseThrow();
    }
}
