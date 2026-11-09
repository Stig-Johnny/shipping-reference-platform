package io.github.stigjohnny.docs.site;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stigjohnny.docs.generate.Brains;
import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.testing.BuiltSite;
import io.github.stigjohnny.docs.testing.Fixture;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Contribution records are published source documents, not generated claims of implementation quality. */
class ContributionPagesTest {

    @TempDir
    Path dir;

    @Test
    void recordsKeepSourceTextAndStableIssueLinksAcrossTheSiteAndSearch() {
        Fixture fixture = Fixture.copy(dir).passingEvidence()
                .write("docs/contributions.md", document("Contribution records", """
                        # Contribution records

                        ## Find an issue

                        [Issue 123](contributions/history.md#issue-123)
                        """))
                .write("docs/contributions/history.md", document("Contribution history", """
                        # Contribution history

                        ## Issue 123

                        Configured model: unknown. Evidence receipt: synthetic-record-123.
                        """));
        Sources sources = Sources.read(fixture.root(), List.of(fixture.reports()));
        SiteBuilder.build(sources, dir.resolve("site"), "0123456789abcdef0123456789abcdef01234567");
        BuiltSite site = BuiltSite.read(dir.resolve("site"));

        assertThat(site.text("trust/contributions.html"))
                .contains("href=\"contribution-history.html#issue-123\"");
        assertThat(site.text("trust/contribution-history.html"))
                .contains("id=\"issue-123\"", "synthetic-record-123", "Configured model: unknown",
                        "docs/contributions/history.md")
                .doesNotContain("badge-shipped");
        assertThat(site.text("search/index.html")).contains("Contribution history", "Issue 123");
        assertThat(site.text("brain/index.html")).contains("href=\"../trust/contributions.html\"");
        assertThat(site.text("trust/index.html")).contains("href=\"contributions.html\"");
    }

    @Test
    void theGeneratedAgentBrainPointsToTheContributionConvention() {
        Fixture fixture = Fixture.copy(dir).passingEvidence();
        Sources sources = Sources.read(fixture.root(), List.of(fixture.reports()));

        assertThat(Brains.root(sources)).contains("[Contribution records](docs/contributions.md)");
    }

    private static String document(String title, String body) {
        return "---\ntitle: \"" + title + "\"\ndescription: \"Synthetic contribution fixture.\"\n"
                + "status: accepted\nupdated: 2026-11-09\n---\n\n" + body;
    }
}
