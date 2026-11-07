package io.github.stigjohnny.docs.site;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.repo.DemoRecord;
import io.github.stigjohnny.docs.repo.DocFile;
import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.repo.TestEvidence;
import io.github.stigjohnny.docs.testing.BuiltSite;
import io.github.stigjohnny.docs.testing.Demos;
import io.github.stigjohnny.docs.testing.Fixture;
import io.github.stigjohnny.docs.testing.Repository;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The Demos section as docs/ui/documentation-site.md draws it: the register, newest recording first; each sheet, with
 * its pull request, kind and recording beside the description and a ledger that counts only the named tests that
 * passed in this build, never the recording; See it links that reach pages of this site; and the empty register.
 */
class DemoPagesTest {

    private static final String SITE = "https://stig-johnny.github.io/" + "shipping-reference-platform/";

    @TempDir
    Path dir;

    private Fixture fixture;

    @BeforeEach
    void copyTheRepository() {
        fixture = Fixture.copy(dir).passingEvidence().withoutDemos();
    }

    @Test
    void withoutDemonstrationsTheRegisterSaysSoAndTheHowToIsPublished() {
        BuiltSite site = build();
        String index = site.text("demos/index.html");

        assertThat(index).contains("<h1>Demos</h1>", "<p class=\"prose\">There are no demonstrations yet.</p>",
                "<a href=\"how-to.html\">How to write a demo</a>").doesNotContain("<table");
        assertThat(site.text("demos/how-to.html")).contains("<h1>How to write a demo</h1>",
                "<span class=\"visually-hidden\">Sheet </span>X-GUIDE</p>");
    }

    @Test
    void theRegisterListsEachOutcomeWithWhyItMattersNewestRecordingFirst() {
        for (String kind : List.of("gate", "visual", "docs", "build", "feature")) {
            fixture.write(Demos.path(kind), Demos.text(kind));
        }
        // 44 is edited last but was recorded before 42, 43 and 46: an edit never moves a demonstration up.
        fixture.edit(Demos.path("docs"), text -> text.replace("updated: 2026-11-07", "updated: 2026-11-08"));

        String index = build().text("demos/index.html");

        assertThat(index).contains("<caption>Demonstrations (5), newest recording first</caption>",
                "<thead><tr><th scope=\"col\">Outcome</th><th scope=\"col\">Pull request</th><th scope=\"col\">Kind"
                        + "</th><th scope=\"col\">Recorded</th></tr></thead>",
                "<tr><th scope=\"row\"><a href=\"42-gate.html\">An unreviewed dependency now fails the check</a>"
                        + "<span class=\"register-why\">A library without a review can no longer reach the build."
                        + "</span></th><td>#42</td><td>Gate</td><td><time datetime=\"2026-11-07\">2026-11-07</time>"
                        + "</td></tr>",
                "<td>#44</td><td>Documentation</td><td><time datetime=\"2026-11-06\">2026-11-06</time></td>")
                .doesNotContain("github.com");
        // Newest recording first; on the same day the higher pull request first.
        assertThat(List.of("<td>#46</td>", "<td>#43</td>", "<td>#42</td>", "<td>#44</td>", "<td>#45</td>").stream()
                .map(index::indexOf).toList()).doesNotContain(-1).isSorted();
    }

    @Test
    void aSheetShowsItsPullRequestKindAndRecordingBesideTheDescription() {
        fixture.write(Demos.path("gate"), Demos.GATE);

        String sheet = build().text("demos/42-gate.html");

        assertThat(sheet).contains("<span class=\"visually-hidden\">Sheet </span>X-42</p>",
                "<h1>An unreviewed dependency now fails the check</h1>",
                "<p class=\"page-summary\">A library without a review can no longer reach the build.</p>\n"
                        + "<p class=\"page-facts\">Pull request #42 · Kind: gate · Recorded "
                        + "<time datetime=\"2026-11-07\">2026-11-07</time></p>\n<p class=\"page-facts\">The revision "
                        + "below is the commit this site was built from, not the one the demonstration was recorded "
                        + "at.</p>\n<dl class=\"sheet-meta\">",
                "<div><dt>Source</dt><dd>docs/demos/42-gate.md</dd></div>",
                "<div><dt>Updated</dt><dd>2026-11-07</dd></div>",
                "<summary id=\"site-contents-title\">On this sheet (4)</summary>",
                "<p class=\"fence-label\">Command</p>\n<pre><code class=\"language-sh\">java "
                        + "tools/policy/PolicyCheck.java .\n</code></pre>",
                "<p class=\"fence-label\">Recorded output</p>\n<pre><code class=\"language-text\">## Dependency "
                        + "allowlist: 1 finding\n</code></pre>",
                "Recorded output — not rerun by this site build.")
                .doesNotContain("Merged", "merged");
        assertThat(ledgerRow(sheet, "Accepted design"))
                .isEqualTo("<span class=\"badge badge-accepted\">Accepted</span> Updated 2026-11-07.");
        assertThat(ledgerRow(sheet, "Planned implementation")).isEqualTo("<span class=\"badge badge-neutral\">Not "
                + "applicable</span> A demonstration specifies no capability or control; it records what pull request "
                + "#42 changed.");
    }

    @Test
    void onlyNamedTestsThatPassedInThisBuildAreEvidenceAndOnlyThoseGetTheDoubleRule() {
        fixture.write(Demos.path("gate"), Demos.GATE.replace("tests: [BrainCheckTest]",
                "tests: [BrainCheckTest, SiteBuilderTest]"))
                .report("io.github.stigjohnny.docs.site.SiteBuilderTest", 20, 1, 0, 0);

        String sheet = build().text("demos/42-gate.html");

        assertThat(ledgerRow(sheet, "Verified evidence")).isEqualTo("<span class=\"badge badge-shipped\">Tests passing"
                + "</span> 1 test class named by this record passed in the build that generated this site. Named tests "
                + "are separate from the recorded output, which this build did not rerun. 1 other named test class did "
                + "not pass in this build and is not evidence.\n<details class=\"evidence-tests\"><summary>Named test "
                + "classes (2)</summary><p>BrainCheckTest: passed; SiteBuilderTest: failed in this build</p>"
                + "</details>");
        assertThat(body(sheet)).doesNotContain("badge-shipped", "badge-approved", "badge-matches");
    }

    @Test
    void aFailedSkippedOrUnreportedTestIsNeverShownAsPassing() {
        fixture.write(Demos.path("gate"), Demos.GATE.replace("tests: [BrainCheckTest]", "tests: [SiteBuilderTest]"));

        assertThat(evidenceOf(build())).isEqualTo(none("SiteBuilderTest: no report in this build"));

        fixture.report("io.github.stigjohnny.docs.site.SiteBuilderTest", 20, 0, 1, 0);
        assertThat(evidenceOf(build())).isEqualTo(none("SiteBuilderTest: failed in this build"));

        fixture.report("io.github.stigjohnny.docs.site.SiteBuilderTest", 20, 0, 0, 1);
        assertThat(evidenceOf(build())).isEqualTo(none("SiteBuilderTest: skipped in this build"));
    }

    @Test
    void aMissingOrAmbiguousTestIsNeverShownAsPassingEither() {
        DemoRecord demo = DemoRecord.of(DocFile.parse(Demos.path("gate"), Demos.GATE.replace("tests: [BrainCheckTest]",
                "tests: [GoneTest, TwinTest, FailedTest, SkippedTest, EmptyTest, PassedTest]")));
        TestEvidence evidence = new TestEvidence(Set.of("TwinTest", "FailedTest", "SkippedTest", "EmptyTest",
                "PassedTest"), Map.of(
                        "TwinTest", new TestEvidence.Result("a.TwinTest", 3, 0, 0, 0),
                        "FailedTest", new TestEvidence.Result("a.FailedTest", 3, 0, 1, 0),
                        "SkippedTest", new TestEvidence.Result("a.SkippedTest", 3, 1, 0, 0),
                        "EmptyTest", new TestEvidence.Result("a.EmptyTest", 0, 0, 0, 0),
                        "PassedTest", new TestEvidence.Result("a.PassedTest", 3, 0, 0, 0)), Set.of("TwinTest"));

        assertThat(DemoPages.evidence(demo, evidence)).isEqualTo("<span class=\"badge badge-shipped\">Tests passing"
                + "</span> 1 test class named by this record passed in the build that generated this site. Named tests "
                + "are separate from the recorded output, which this build did not rerun. 5 other named test classes "
                + "did not pass in this build and are not evidence.\n<details class=\"evidence-tests\"><summary>Named "
                + "test classes (6)</summary><p>GoneTest: not in the repository; TwinTest: ambiguous, more than one "
                + "test class has this name; FailedTest: failed in this build; SkippedTest: skipped in this build; "
                + "EmptyTest: ran no test in this build; PassedTest: passed</p></details>");
        DemoRecord unproven = DemoRecord.of(DocFile.parse(Demos.path("gate"), Demos.GATE.replace(
                "tests: [BrainCheckTest]", "tests: [GoneTest, TwinTest]")));
        assertThat(DemoPages.evidence(unproven, evidence)).startsWith("<span class=\"badge badge-none\">None in this "
                + "build</span> ").doesNotContain("badge-shipped");
    }

    @Test
    void aRecordingAloneNeverGetsTheDoubleRule() {
        fixture.write(Demos.path("gate"), Demos.GATE.replace("tests: [BrainCheckTest]\n", ""));

        BuiltSite site = build();
        String sheet = site.text("demos/42-gate.html");

        assertThat(evidenceOf(site)).isEqualTo("<span class=\"badge badge-none\">None in this build</span> This record "
                + "names no test class, so the build that generated this site checked nothing on it. Its evidence is "
                + "recorded output, which this build did not rerun.");
        assertThat(sheet).doesNotContain("badge-shipped", "badge-approved", "badge-matches");
    }

    @Test
    void seeItLinksBecomeRelativeLinksToPagesAndAnchorsOfThisSite() {
        fixture.write(Demos.path("visual"), Demos.VISUAL.replace("Open [how the site is drawn]("
                + "../ui/documentation-site.md)", "Open [how the site is drawn](../ui/documentation-site.md"
                + "#the-five-components), [the start sheet](" + SITE + "index.html#reading-paths), [the register]("
                + SITE + "demos/index.html) and [the site](" + SITE + ")"));

        BuiltSite site = build();
        String sheet = site.text("demos/43-visual.html");

        assertThat(sheet).contains("<a rel=\"nofollow\" href=\"../start/site-design.html#the-five-components\">how the "
                + "site is drawn</a>", "<a rel=\"nofollow\" href=\"../index.html#reading-paths\">the start sheet</a>",
                "<a rel=\"nofollow\" href=\"index.html\">the register</a>",
                "<a rel=\"nofollow\" href=\"../index.html\">the site</a>").doesNotContain("github.io");
        assertThat(site.ids("index.html")).contains("reading-paths");
    }

    @ParameterizedTest
    @ValueSource(strings = {"../../tools/policy/PolicyCheck.java", "../../contracts/shipping.openapi.yaml",
        "https://example.com/", SITE + "no-such-page.html", SITE + "index.html#no-such-anchor", SITE + "demos/"})
    void aSeeItLinkToAnUnpublishedFileADownloadOrAnotherSiteFailsTheBuild(String destination) {
        fixture.write("tools/policy/PolicyCheck.java", "class PolicyCheck {}\n");
        fixture.write(Demos.path("visual"), Demos.VISUAL.replace("Open [how the site is drawn](../ui/documentation-"
                + "site.md)", "Open [the page](" + destination + ")"));

        assertThatThrownBy(this::build).isInstanceOf(DocsException.class)
                .hasMessageContaining("docs/demos/43-visual.md: See it: '" + destination + "' ")
                .hasMessageContaining("; See it links pages of this site");
    }

    @Test
    void aLinkInTheSeeItHeadingIsHeldToTheSameRule() {
        fixture.write(Demos.path("visual"), Demos.VISUAL.replace("## See it", "## [See it](https://example.com/)"));

        assertThatThrownBy(this::build).isInstanceOf(DocsException.class).hasMessageContaining(
                "docs/demos/43-visual.md: See it: 'https://example.com/' is another site; See it links pages of this "
                        + "site");
    }

    @Test
    void theEvidenceOfAVisualChangeLinksThePageItInspected() {
        fixture.write(Demos.path("visual"), Demos.VISUAL.replace("[the design sheet](../ui/documentation-site.md)",
                "the design sheet"));

        assertThatThrownBy(this::build).isInstanceOf(DocsException.class).hasMessageContaining(
                "docs/demos/43-visual.md: the evidence of a visual change links no page of this site that it "
                        + "inspected");
    }

    @Test
    void theDemosComeSecondInTheRegisterAndTheStartSheetLinksThem() {
        BuiltSite site = build();
        String start = site.text("index.html");
        String nav = start.substring(start.indexOf("<nav class=\"site-nav\""), start.indexOf("</nav>",
                start.indexOf("<nav class=\"site-nav\"")));

        assertThat(List.of(nav.indexOf(">S-00<"), nav.indexOf(">X-00<"), nav.indexOf(">M-00<"))).doesNotContain(-1)
                .isSorted();
        assertThat(nav).contains("<span class=\"register-code\">X-00</span> <span class=\"register-title\">Demos"
                + "</span>");
        String paths = start.substring(start.indexOf("id=\"reading-paths\""), start.indexOf("id=\"sections\""));
        assertThat(paths).contains("<li><a href=\"demos/index.html\">Demos: what each change did, with its evidence"
                + "</a></li>");
        assertThat(start.substring(start.indexOf("id=\"sections\""))).contains("<span class=\"register-code\">X-00"
                + "</span> <a href=\"demos/index.html\">Demos</a>");
        assertThat(start.indexOf("class=\"start-drawing\"")).isLessThan(start.indexOf("id=\"reading-paths\""));
    }

    @Test
    void thePullRequestTemplateNamesTheDemonstrationAndItsRouteAsTheToolMapsThem() {
        String template = Repository.read(".github/pull_request_template.md");

        assertThat(template).contains("\nDemo: docs/demos/<pr>-<slug>.md (required for risk level 3 or visible "
                + "changes), or: none needed because <reason>\nPublic route: demos/<pr>-<slug>.html\nRecording "
                + "limits:\n");
        assertThat(SiteMap.pageOf("docs/demos/42-gate.md")).isEqualTo("demos/42-gate.html");
        assertThat(SiteMap.pageOf(DemoRecord.HOW_TO)).isEqualTo(DemoPages.HOW_TO);
    }

    private static String none(String states) {
        return "<span class=\"badge badge-none\">None in this build</span> No test class named by this record passed "
                + "in the build that generated this site, so none is evidence here. The recorded output below was not "
                + "rerun by this build.\n<details class=\"evidence-tests\"><summary>Named test classes (1)</summary><p>"
                + states + "</p></details>";
    }

    private static String evidenceOf(BuiltSite site) {
        return ledgerRow(site.text("demos/42-gate.html"), "Verified evidence");
    }

    private static String ledgerRow(String html, String term) {
        int start = html.indexOf("<dt>" + term + "</dt><dd>");
        assertThat(start).as(term).isNotNegative();
        return html.substring(start + ("<dt>" + term + "</dt><dd>").length(), html.indexOf("</dd></div>", start));
    }

    private static String body(String html) {
        return html.substring(html.indexOf("<div class=\"page-body\">"), html.indexOf("class=\"page-source\""));
    }

    private BuiltSite build() {
        Path out = dir.resolve("site");
        SiteBuilder.build(Sources.read(fixture.root(), List.of(fixture.reports())), out,
                "0123456789abcdef0123456789abcdef01234567");
        return BuiltSite.read(out);
    }
}
