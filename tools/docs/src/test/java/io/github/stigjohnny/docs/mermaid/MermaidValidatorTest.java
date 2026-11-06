package io.github.stigjohnny.docs.mermaid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The validator of security/reviews/vendored-js--mermaid--11.17.2.md, condition 4, as negative fixtures. */
class MermaidValidatorTest {

    private static final String VALID = """
            flowchart LR
                accTitle: Fixture view
                accDescr: Two elements and one relationship; the table below lists them.
                1["Shipping clerk [Person]"]
                subgraph 5 ["Shipping reference platform"]
                    7[("Database [Container: PostgreSQL 18.6]")]
                    8["Quotes [Component: Spring Modulith module] (planned)"]
                end
                style 1 fill:#0f766e,stroke:#475569,color:#ffffff
                style 5 fill:#ffffff,stroke:#475569,color:#475569
                1 -->|"Prices parcels; no network call in v0.1 [HTTPS, browser]"| 8
                8 ==>|"REQUIRED: Resolves the caller's tenant (tenants.api: CurrentTenant) [Java]"| 7
                8 -.->|"PERMITTED: May record QuoteIssued events"| 7
                8 --> 7
            """;

    @Test
    void theAdaptersFormIsAccepted() {
        assertThat(MermaidValidator.validate(VALID, "fixture")).isEqualTo(VALID);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "%%{init: {'securityLevel': 'loose'}}%%",
        "click 1 href \"https://example.com\"",
        "click 1 call alert()",
        "click 1 \"https://example.com\"",
        "1@{ img: \"https://example.com/a.png\" }",
        "9[\"Formula $$x^2$$\"]",
        "9[\"<b>bold</b>\"]",
        "9[\"Danger <img src=x onerror=alert(1)>\"]",
        "9[\"a > b\"]",
        "1 --> 8 & 7",
        "style 1 fill:red",
        "style 1 fill:#fff,stroke-width:4px",
        "style 1 stroke-dasharray:5 5",
        "classDef planned fill:url(#x)",
        "classDef planned fill:#ffffff,font-family:x",
        "linkStyle 0 stroke:#ff0000,stroke-width:4px",
        "linkStyle default interpolate basis",
        "9[\"`**markdown** string`\"]",
        "9[\"entity #lt; code\"]",
        "9[\"pipe | inside\"]",
        "subgraph href [\"x\"]",
        "subgraph 9 [\"x\"] %% comment",
        "%% a comment",
        "direction TB",
        "9>\"asymmetric\"]",
        "9{\"rhombus\"}",
        "1 --x 8",
        "1 <--> 8",
        "1 ---> 8",
        "1 -->|unquoted| 8",
        "call 1",
        "class 1 planned",
        "9[\"tab\there\"]",
    })
    void eachForbiddenConstructFails(String line) {
        String definition = VALID + "    " + line + "\n";

        assertThatThrownBy(() -> MermaidValidator.validate(definition, "fixture view"))
                .isInstanceOf(UnsafeDiagramException.class).hasMessageContaining("fixture view");
    }

    @Test
    void theDefinitionMustStartWithAFlowchartHeader() {
        assertThatThrownBy(() -> MermaidValidator.validate("sequenceDiagram\n    a->>b: hi\n", "x"))
                .isInstanceOf(UnsafeDiagramException.class);
        assertThatThrownBy(() -> MermaidValidator.validate("graph LR\n    1 --> 2\n", "x"))
                .isInstanceOf(UnsafeDiagramException.class);
    }

    @Test
    void moreThanFiftyThousandCharactersFail() {
        StringBuilder big = new StringBuilder("flowchart LR\n");
        while (big.length() <= MermaidValidator.MAX_CHARACTERS) {
            big.append("    n").append(big.length()).append("[\"padding padding padding padding\"]\n");
        }

        assertThatThrownBy(() -> MermaidValidator.validate(big.toString(), "big"))
                .isInstanceOf(UnsafeDiagramException.class).hasMessageContaining("50000");
    }

    @Test
    void moreThanFiveHundredEdgesFail() {
        assertThat(MermaidValidator.validate(edges(MermaidValidator.MAX_EDGES), "ok")).isNotEmpty();
        assertThatThrownBy(() -> MermaidValidator.validate(edges(MermaidValidator.MAX_EDGES + 1), "many"))
                .isInstanceOf(UnsafeDiagramException.class).hasMessageContaining("500");
    }

    @Test
    void theAngleBracketsOfArrowsAreTheOnlyOnesAllowed() {
        assertThatThrownBy(() -> MermaidValidator.validate("flowchart LR\n    1 --> 2\n    3[\"x\"]>\n", "x"))
                .isInstanceOf(UnsafeDiagramException.class);
    }

    private static String edges(int count) {
        StringBuilder out = new StringBuilder("flowchart LR\n");
        for (int i = 0; i < count; i++) {
            out.append("    a").append(i).append(" --> b").append(i).append('\n');
        }
        return out.toString();
    }
}
