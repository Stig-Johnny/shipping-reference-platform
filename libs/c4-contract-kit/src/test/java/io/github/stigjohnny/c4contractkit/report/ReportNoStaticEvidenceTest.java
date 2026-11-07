package io.github.stigjohnny.c4contractkit.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stigjohnny.c4contractkit.api.Conformance;
import io.github.stigjohnny.c4contractkit.api.ConformanceAssertions;
import io.github.stigjohnny.c4contractkit.api.EdgeResult;
import io.github.stigjohnny.c4contractkit.api.EdgeStatus;
import io.github.stigjohnny.c4contractkit.api.Mode;
import io.github.stigjohnny.c4contractkit.api.Report;
import io.github.stigjohnny.c4contractkit.testing.FixtureCompiler;
import io.github.stigjohnny.c4contractkit.testing.FixtureSource;
import io.github.stigjohnny.c4contractkit.testing.ShopFixture;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A PERMITTED relationship without static references is reported as "no static evidence", never as unused, and does
 * not fail the check. The fixture is the one docs/capabilities/capabilities.yaml names: built, non-empty audit and
 * quotes components, a PERMITTED audit -> quotes relationship, and no static reference between them.
 */
class ReportNoStaticEvidenceTest {

    private static final String MODEL = """
            workspace {
                !identifiers flat
                !impliedRelationships false
                model {
                    shop = softwareSystem "Shop" {
                        api = container "API" {
                            properties {
                                "contract.root" "fx.shipping"
                            }
                            quotes = component "Quotes" {
                                properties {
                                    "contract.id" "quotes"
                                    "contract.packages" "fx.shipping.quotes"
                                    "contract.status" "built"
                                }
                            }
                            audit = component "Audit" {
                                properties {
                                    "contract.id" "audit"
                                    "contract.packages" "fx.shipping.audit"
                                    "contract.status" "built"
                                }
                            }
                        }
                    }
                    audit -> quotes "May record QuoteIssued events" "Java" "Permitted" {
                        properties {
                            "contract.mode" "PERMITTED"
                        }
                    }
                }
            }
            """;

    @Test
    void aPermittedRelationshipWithoutReferencesIsNoStaticEvidenceAndPasses(@TempDir Path dir) throws IOException {
        Path model = ShopFixture.writeDsl(dir, MODEL);
        Path classes = FixtureCompiler.compile(
                new FixtureSource("fx/shipping/quotes/Quote.java", "package fx.shipping.quotes;\n\npublic class Quote {\n}\n"),
                new FixtureSource("fx/shipping/audit/AuditEntry.java",
                        "package fx.shipping.audit;\n\npublic class AuditEntry {\n}\n"));

        Report report = Conformance.check(model, List.of(classes));
        Path reports = dir.resolve("reports");
        ConformanceAssertions.assertConforms(report, reports);

        assertThat(report.passed()).isTrue();
        assertThat(report.edges()).containsExactly(
                new EdgeResult("audit", "quotes", Mode.PERMITTED, EdgeStatus.NO_STATIC_EVIDENCE, List.of()));
        String markdown = Files.readString(reports.resolve("report.md"), StandardCharsets.UTF_8);
        String json = Files.readString(reports.resolve("report.json"), StandardCharsets.UTF_8);
        assertThat(markdown).contains("| audit | quotes | PERMITTED | no static evidence | 0 |");
        assertThat(json).contains("\"status\": \"NO_STATIC_EVIDENCE\"");
        assertThat(markdown.toLowerCase(Locale.ROOT)).doesNotContain("unused");
        assertThat(json.toLowerCase(Locale.ROOT)).doesNotContain("unused");
    }
}
