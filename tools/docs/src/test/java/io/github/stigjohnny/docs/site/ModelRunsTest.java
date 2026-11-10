package io.github.stigjohnny.docs.site;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.check.Consistency;
import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.repo.DocFile;
import io.github.stigjohnny.docs.repo.ModelRuns;
import io.github.stigjohnny.docs.scan.PublicationScanner;
import io.github.stigjohnny.docs.scan.ScanExceptions;
import io.github.stigjohnny.docs.testing.BuiltSite;
import io.github.stigjohnny.docs.testing.Fixture;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Records constrain accounting claims without authenticating receipts or claiming complete change coverage. */
class ModelRunsTest {

    private static final String SOURCE = "docs/contributions/runs.md";
    private static final String HEAD = "0123456789abcdef0123456789abcdef01234567";
    private static final String RECORD = """
              - id: sample-implementation
                issue: 123
                date: "2026-11-10"
                role: implementation
                tool: "Synthetic tool"
                model: "sample-model"
                checkpoint: "unknown"
                effort: "unknown"
                base: "0123456789abcdef0123456789abcdef01234567"
                outcome: rejected
                scope: "Synthetic task"
                environment: "Offline fixture; same acceptance rules"
                result: "Rejected patch; synthetic review receipt"
                limitations: "No backend checkpoint or reasoning receipt; no real billing"
                evidence: "synthetic-receipt-123"
                correctionOf: "none"
                usage:
                  state: reported
                  input: 1000
                  output: 20
                  scope: "One synthetic request; input includes cached input"
                  receipt: "synthetic-usage-123"
                cost:
                  basis: metered-estimate
                  amount: "0.000324"
                  currency: USD
                  scope: "Same synthetic request, no shared allocation"
                  method: "Synthetic rates dated 2026-11-10; input 0.30 and output 1.20 USD per million"
                  receipt: "synthetic-meter-123; not an invoice"
            """;

    @TempDir
    Path dir;

    @Test
    void malformedAccountingRecordsFailTheExistingConsistencyCheck() {
        Fixture fixture = Fixture.copy(dir).passingEvidence();
        List<String> invalid = List.of(
                document(RECORD).replace("schemaVersion: 1", "schemaVersion: 2"),
                document(RECORD).replace("schemaVersion: 1", "schemaVersion: \"1\""),
                document(RECORD).replace("coverage: \"", "extra: true\ncoverage: \""),
                document(RECORD).replace("  - id:", "  - typo: value\n    id:"),
                document(RECORD).replace("issue: 123", "issue: 0"),
                document(RECORD).replace("model: \"sample-model\"", "model: true"),
                document(RECORD).replace("role: implementation", "role: owner"),
                document(RECORD).replace("date: \"2026-11-10\"", "date: \"2026-02-30\""),
                document(RECORD).replace(HEAD, "123abc"),
                document(RECORD).replace("input: 1000", "input: -1"),
                document(RECORD).replace("output: 20", "output: 2.5"),
                document(RECORD).replace("input: 1000", "input: 1000000000001"),
                document(RECORD).replace("amount: \"0.000324\"", "amount: 0.000324"),
                document(RECORD).replace("amount: \"0.000324\"", "amount: \"NaN\""),
                document(RECORD).replace("amount: \"0.000324\"", "amount: \"1e999999\""),
                document(RECORD).replace("amount: \"0.000324\"", "amount: \"-1\""),
                document(RECORD).replace("basis: metered-estimate", "basis: unknown"),
                document(RECORD).replace("state: reported", "state: unknown"),
                document(RECORD).replace("currency: USD", "currency: usd"),
                document(RECORD).replace("scope: \"Synthetic task\"", "scope: \"" + "x".repeat(2001) + "\""),
                document(RECORD).replace("scope: \"Synthetic task\"\n", ""),
                document(RECORD).replace("outcome: rejected", "outcome: accepted"),
                document(RECORD).replace("role: implementation", "role: human-decision"),
                document(RECORD).replace("method: \"Synthetic rates dated 2026-11-10; input 0.30 and output 1.20 USD per million\"",
                        "method: \"\""),
                document(RECORD).replace("receipt: \"synthetic-usage-123\"", "receipt: \"\""),
                document(RECORD).replace("correctionOf: \"none\"", "correctionOf: sample-implementation"),
                document(RECORD).replace("correctionOf: \"none\"", "correctionOf: nonexistent-record"),
                document(RECORD + RECORD.replace("id: sample-implementation", "id: wrong-issue-correction")
                        .replace("issue: 123", "issue: 124")
                        .replace("correctionOf: \"none\"", "correctionOf: sample-implementation")),
                document(RECORD + RECORD));
        for (int i = 0; i < invalid.size(); i++) {
            fixture.write(SOURCE, invalid.get(i));
            assertThatThrownBy(() -> Consistency.check(read(fixture))).as("invalid case %s", i)
                    .isInstanceOf(DocsException.class).hasMessageContaining(SOURCE);
        }
    }

    @Test
    void exactAmountsUnknownCostsAndCorrectionsRemainSeparateAndEscaped() {
        String unknown = RECORD.replace("sample-implementation", "sample-review")
                .replace("role: implementation", "role: independent-review")
                .replace("outcome: rejected", "outcome: partial")
                .replace("state: reported", "state: unknown")
                .replace("input: 1000", "input: \"unknown\"").replace("output: 20", "output: \"unknown\"")
                .replace("basis: metered-estimate", "basis: unknown")
                .replace("amount: \"0.000324\"", "amount: \"unknown\"")
                .replace("currency: USD", "currency: \"unknown\"")
                .replace("synthetic-meter-123; not an invoice", "Subscription allocation unavailable");
        String correction = RECORD.replace("id: sample-implementation", "id: sample-correction")
                .replace("correctionOf: \"none\"", "correctionOf: sample-implementation")
                .replace("amount: \"0.000324\"", "amount: \"0.000300\"");
        Fixture fixture = Fixture.copy(dir).passingEvidence().write(SOURCE, document(RECORD + unknown + correction)
                .replace("Synthetic task", "Synthetic <script>bad()</script> task"));
        assertThatCode(() -> Consistency.check(read(fixture))).doesNotThrowAnyException();
        SiteBuilder.build(read(fixture), dir.resolve("site"), HEAD);
        BuiltSite site = BuiltSite.read(dir.resolve("site"));
        assertThat(site.text("trust/model-runs.html"))
                .contains("0.000324 USD", "0.000300 USD", "metered-estimate", "rejected", "partial",
                        "sample-review", "Subscription allocation unavailable", "Correction of sample-implementation",
                        "id=\"run-sample-implementation\"", "&lt;script&gt;bad()&lt;/script&gt;", "1000", "20")
                .doesNotContain("<script>bad()", "Total spend", "badge-shipped");
        assertThat(site.text("search/index.html")).contains("Model runs", "Issue 123");
        assertThat(site.text("brain/index.html")).contains("../trust/model-runs.html");
        assertThat(PublicationScanner.scan(site.root(), "synthetic-receipt-123", ScanExceptions.none(), "").findings())
                .anySatisfy(finding -> {
                    assertThat(finding.rule()).isEqualTo("DICTIONARY");
                    assertThat(finding.file()).isEqualTo("trust/model-runs.html");
                });
        fixture.write(SOURCE, document(unknown));
        SiteBuilder.build(read(fixture), dir.resolve("fresh"), HEAD);
        assertThat(BuiltSite.read(dir.resolve("fresh")).text("trust/model-runs.html"))
                .contains("Subscription allocation unavailable").doesNotContain("0.000324", "sample-implementation");
    }

    @Test
    void absentAndEmptyRegisterDoNotClaimCompleteCoverageOrFreeWork() {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write(SOURCE, document(""));
        SiteBuilder.build(read(fixture), dir.resolve("empty"), HEAD);
        assertThat(BuiltSite.read(dir.resolve("empty")).text("trust/model-runs.html"))
                .contains("No structured records supplied", "Completeness is reviewer-owned");
        fixture.delete(SOURCE);
        Sources sources = read(fixture);
        assertThat(ModelRuns.read(sources).records()).isEmpty();
        assertThat(ModelRunsPage.of(new SiteContext(sources, SiteMap.of(sources), List.of(), HEAD)).body())
                .contains("No structured records supplied", "Completeness is reviewer-owned");
    }

    @Test
    void knownMeasurementsCannotUseMissingEvidenceMarkers() {
        for (String marker : List.of("unknown", "not-applicable", "none", " N/A ")) {
            for (String changed : List.of(
                    RECORD.replace("synthetic-usage-123", marker),
                    RECORD.replace("synthetic-meter-123; not an invoice", marker),
                    RECORD.replace("Synthetic rates dated 2026-11-10; input 0.30 and output 1.20 USD per million", marker))) {
                assertThatThrownBy(() -> ModelRuns.parse(DocFile.parse(SOURCE, document(changed))))
                        .isInstanceOf(DocsException.class).hasMessageContaining(SOURCE);
            }
        }
    }

    @Test
    void correctionsAreIdentifiedBeforeDisclosureOpens() {
        String correction = RECORD.replace("id: sample-implementation", "id: sample-correction")
                .replace("correctionOf: \"none\"", "correctionOf: sample-implementation");
        Fixture fixture = Fixture.copy(dir).passingEvidence().write(SOURCE, document(RECORD + correction));
        Sources sources = read(fixture);
        SitePage page = ModelRunsPage.of(new SiteContext(sources, SiteMap.of(sources), List.of(), HEAD));
        assertThat(page.body()).contains("<summary>Correction (not an additional charge)", "<summary>Amended",
                "href=\"#run-sample-correction\"");
    }

    @Test
    void humanNotApplicableAndEvidencedZeroRemainDifferentFromUnknown() {
        String human = RECORD.replace("role: implementation", "role: human-decision")
                .replace("model: \"sample-model\"", "model: \"not-applicable\"")
                .replace("checkpoint: \"unknown\"", "checkpoint: \"not-applicable\"")
                .replace("effort: \"unknown\"", "effort: \"not-applicable\"")
                .replace("state: reported", "state: not-applicable")
                .replace("input: 1000", "input: \"not-applicable\"")
                .replace("output: 20", "output: \"not-applicable\"")
                .replace("basis: metered-estimate", "basis: unknown")
                .replace("amount: \"0.000324\"", "amount: \"unknown\"")
                .replace("currency: USD", "currency: unknown");
        ModelRuns.Run person = ModelRuns.parse(DocFile.parse(SOURCE, document(human))).records().getFirst();
        assertThat(person.usage().state()).isEqualTo("not-applicable");
        assertThat(person.cost().basis()).isEqualTo("unknown");
        ModelRuns.Run measured = ModelRuns.parse(DocFile.parse(SOURCE, document(RECORD
                .replace("basis: metered-estimate", "basis: billed")
                .replace("synthetic-meter-123; not an invoice", "synthetic-provider-bill-123; zero charge for this request")
                .replace("amount: \"0.000324\"", "amount: \"0\"")))).records().getFirst();
        assertThat(measured.cost().amount()).isEqualTo("0");
        assertThat(measured.cost().basis()).isEqualTo("billed");
    }

    private static Sources read(Fixture fixture) {
        return Sources.read(fixture.root(), List.of(fixture.reports()));
    }

    private static String document(String records) {
        return """
                ---
                title: "Model runs"
                description: "Synthetic accounting fixture"
                status: accepted
                updated: 2026-11-10
                schemaVersion: 1
                coverage: "Partial synthetic records; no completeness or billing assertion"
                records:
                """ + (records.isEmpty() ? "  []\n" : records)
                + "---\n\n# Model runs\n\nFixture only.\n\n## Backfill method\n\nSynthetic fixture only.\n";
    }
}
