package io.github.stigjohnny.docs.site;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.scan.PublicationScanner;
import io.github.stigjohnny.docs.scan.ScanExceptions;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.github.stigjohnny.docs.testing.BuiltSite;
import io.github.stigjohnny.docs.testing.Fixture;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SearchPageTest {
    @TempDir Path dir;

    @Test
    void everyPublishedSheetHasOnePlainHtmlSearchEntry() {
        BuiltSite site = build(Fixture.copy(dir).passingEvidence());
        String search = site.text("search/index.html");
        for (String page : site.pages()) {
            if (!page.equals("404.html")) {
                assertThat(search).containsOnlyOnce("data-search-page=\"" + page + "\"");
            }
        }
        assertThat(search).contains("<label for=\"site-search\">", "<ol id=\"search-results\">")
                .doesNotContain("application/json", " hidden>\n<ol");
    }

    @Test
    void headingsComeFromPublishedBodyAndUnpublishedRepositoryTextIsNotIndexed() {
        Fixture fixture = Fixture.copy(dir).passingEvidence()
                .edit("docs/risk-levels.md", text -> text + "\n## Amber **parcel** heading\n\nPublished body only.\n")
                .write("not-published.txt", "UNPUBLISHED_SENTINEL");
        BuiltSite site = build(fixture);
        assertThat(site.text("trust/risk-levels.html")).contains("Amber <strong>parcel</strong> heading");
        assertThat(site.text("search/index.html")).contains("Amber parcel heading")
                .doesNotContain("UNPUBLISHED_SENTINEL", "Published body only.");
    }

    @Test
    void markupInATitleStaysTextInTheIndex() {
        Fixture fixture = Fixture.copy(dir).passingEvidence().edit("docs/risk-levels.md",
                text -> text.replace("Risk levels", "Risk <img src=x> levels"));
        String search = build(fixture).text("search/index.html");
        assertThat(search).contains("Risk &lt;img src=x&gt; levels").doesNotContain("<img");
    }

    @Test
    void everyIndexedFieldBelongsToItsPublishedSheet() {
        BuiltSite site = build(Fixture.copy(dir).passingEvidence());
        String index = site.text("search/index.html");
        for (String entry : index.split("(?=<li data-search-page=)", -1)) {
            if (!entry.startsWith("<li data-search-page=")) continue;
            String path = field(entry, "data-search-page=\"([^\"]+)\"");
            String page = site.text(path);
            assertThat(field(entry, "<h3 class=\"search-title\"><a [^>]*>(.*?)</a></h3>"))
                    .isEqualTo(field(page, "<h1>(.*?)</h1>"));
            assertThat(field(entry, "<p class=\"search-summary\">(.*?)</p>"))
                    .isEqualTo(field(page, "<p class=\"page-summary\">(.*?)</p>"));
            String plainPage = page.replaceAll("<[^>]*>", "");
            Matcher heading = Pattern.compile("<li>(.*?)</li>", Pattern.DOTALL)
                    .matcher(field(entry, "<ul class=\"search-headings\">(.*?)</ul>"));
            while (heading.find()) assertThat(plainPage).contains(heading.group(1));
        }
    }

    @Test
    void publicationScanSeesEscapedIndexText() {
        Fixture fixture = Fixture.copy(dir).passingEvidence().edit("docs/risk-levels.md",
                text -> text.replace("Risk levels", "Amber & parcel risk levels"));
        BuiltSite site = build(fixture);
        assertThat(site.text("search/index.html")).contains("Amber &amp; parcel");
        assertThat(PublicationScanner.scan(site.root(), "amber & parcel", ScanExceptions.none(), "").findings())
                .anySatisfy(finding -> {
                    assertThat(finding.rule()).isEqualTo("DICTIONARY");
                    assertThat(finding.file()).isEqualTo("search/index.html");
                });
    }

    private static String field(String text, String pattern) {
        Matcher matcher = Pattern.compile(pattern, Pattern.DOTALL).matcher(text);
        assertThat(matcher.find()).as(pattern).isTrue();
        return matcher.group(1);
    }

    private BuiltSite build(Fixture fixture) {
        Path out = dir.resolve("site");
        SiteBuilder.build(Sources.read(fixture.root(), List.of(fixture.reports())), out,
                "0123456789abcdef0123456789abcdef01234567");
        return BuiltSite.read(out);
    }
}
