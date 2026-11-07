package io.github.stigjohnny.docs.site;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.repo.Capability;
import io.github.stigjohnny.docs.repo.DemoRecord;
import io.github.stigjohnny.docs.repo.DocFile;
import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.repo.TestEvidence;
import io.github.stigjohnny.docs.testing.BuiltSite;
import io.github.stigjohnny.docs.testing.Decks;
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
 * passed in this build, never the recording; See it links that reach pages of this site; and the empty register. Then
 * the milestone deck: featured above the demonstrations as a presentation, its slides after the register, the overview
 * and the slide index, each slide a labelled section with its number, its controls and the revision, slide 02 with the
 * repository's ledger, and its notes on a page of their own.
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

        // On a narrow screen the register keeps readable columns and scrolls inside its labelled region, with the hint.
        assertThat(index).contains("<div class=\"table-scroll table-wide-demos\" role=\"region\" tabindex=\"0\" "
                + "aria-label=\"Demonstrations, scrolls sideways\">\n<p class=\"table-hint\">Scroll horizontally for "
                + "all columns.</p>\n<table>\n<caption>Demonstrations (5), newest recording first</caption>",
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
                + "names no test class, so no named-test evidence is available in this build. The documentation checks "
                + "still validate its form and links. Its recorded evidence was not rerun.");
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

    // The milestone deck: docs/demos/deck-<slug>.md, published as slides (X-D01) and speaker notes (X-N01).

    private static final String DECK = "demos/deck-milestone.html";
    private static final String NOTES = "demos/deck-milestone-notes.html";

    @Test
    void theRegisterFeaturesTheDeckAboveTheDemonstrationsAsAPresentationNeverAsEvidence() {
        fixture.write(Decks.PATH, Decks.SAMPLE).write(Demos.path("gate"), Demos.GATE);

        BuiltSite site = build();
        String index = body(site.text("demos/index.html"));

        String feature = "<div class=\"register-feature\">\n<p class=\"card-title\"><span class=\"register-code\">X-D01"
                + "</span> <a href=\"deck-milestone.html\">Milestone deck</a> <span class=\"badge badge-neutral\">"
                + "Presentation</span></p>\n<p>A sample deck: what is built, what is planned and one change the gate "
                + "rejects.</p>\n<p class=\"card-meta\">Milestone <time datetime=\"2026-11-10\">2026-11-10</time> · "
                + "Updated <time datetime=\"2026-11-07\">2026-11-07</time> · Speaker notes <a href=\""
                + "deck-milestone-notes.html\">X-N01</a></p>\n</div>\n";
        assertThat(index).contains(feature);
        assertThat(index.indexOf(feature)).isLessThan(index.indexOf("<table"));
        assertThat(index.substring(0, index.indexOf("<table"))).doesNotContain("badge-shipped", "badge-approved",
                "badge-matches");
        // The register of sheets lists the deck and its notes before the demonstrations, with the same neutral label.
        String nav = navigation(site.text("demos/index.html"));
        assertThat(List.of(nav.indexOf(">X-GUIDE<"), nav.indexOf(">X-D01<"), nav.indexOf(">X-N01<"),
                nav.indexOf(">X-42<"))).doesNotContain(-1).isSorted();
        assertThat(nav).contains("<span class=\"register-code\">X-D01</span> <span class=\"register-title\">Milestone "
                + "deck <span class=\"badge badge-neutral\">Presentation</span></span>",
                "<span class=\"register-code\">X-N01</span> <span class=\"register-title\">Milestone deck · speaker "
                        + "notes</span>");
    }

    @Test
    void theDeckPutsTheRegisterTheOverviewAndTheSlideIndexBeforeTheSlides() {
        fixture.write(Decks.PATH, Decks.SAMPLE);

        String deck = build().text(DECK);

        assertThat(deck).startsWith("<!DOCTYPE html>\n<html lang=\"en\" class=\"deck\">")
                .contains("<title>Milestone: an example · Shipping reference platform</title>",
                        "<span class=\"visually-hidden\">Sheet </span>X-D01</p>\n<h1>Milestone: an example</h1>",
                        "<p class=\"page-summary\">A sample deck: what is built, what is planned and one change the gate "
                                + "rejects.</p>",
                        // The milestone, the source's update date and the build's revision, each on its own.
                        "<p class=\"page-facts\">Presentation · Milestone <time datetime=\"2026-11-10\">2026-11-10"
                                + "</time> · 3 slides and 1 appendix slide · Speaker notes <a href=\""
                                + "deck-milestone-notes.html\">X-N01</a></p>",
                        "<p class=\"page-facts\">The milestone is the meeting the deck is made for, Updated is the date "
                                + "of its source, and Revision is the commit this site was built from.</p>",
                        "<p class=\"deck-start-line\"><a class=\"deck-start\" href=\"#what-is-built\">Start slides</a>"
                                + "</p>",
                        "<div><dt>Revision</dt><dd><code>01234567</code></dd></div>",
                        "<div><dt>Updated</dt><dd>2026-11-07</dd></div>");
        assertThat(ledgerRow(deck, "Verified evidence")).startsWith("<span class=\"badge badge-neutral\">Presentation"
                + "</span> ");
        String index = "<nav class=\"deck-index\" id=\"site-deck-index\" aria-labelledby=\"site-deck-index-title\">\n"
                + "<h2 id=\"site-deck-index-title\">Overview</h2>\n<ol class=\"deck-index-list\">\n"
                + "<li><a href=\"#what-is-built\"><span class=\"register-code\" aria-hidden=\"true\">01/03</span> "
                + "<span class=\"register-title\">What is built</span></a></li>\n"
                + "<li><a href=\"#state-today\"><span class=\"register-code\" aria-hidden=\"true\">02/03</span> "
                + "<span class=\"register-title\">State today</span></a></li>\n"
                + "<li><a href=\"#one-change-the-gate-rejects\"><span class=\"register-code\" aria-hidden=\"true\">03/03"
                + "</span> <span class=\"register-title\">One change the gate rejects</span></a></li>\n</ol>\n"
                + "<h3 id=\"site-deck-appendix-title\">Appendix</h3>\n<ol class=\"deck-index-list\">\n"
                + "<li><a href=\"#appendix-tools\"><span class=\"register-code\" aria-hidden=\"true\">A1/1</span> "
                + "<span class=\"register-title\">Appendix: tools</span></a></li>\n</ol>\n</nav>\n";
        assertThat(deck).contains(index);
        assertThat(List.of(deck.indexOf("<header class=\"site-header\""), deck.indexOf("<header class=\"sheet-header\""),
                deck.indexOf("class=\"status-panel\""), deck.indexOf(index), deck.indexOf("<section class=\"slide\"")))
                .doesNotContain(-1).isSorted();
        // The deck's chrome is the site's own: no inline script, no styles of its own, no script beyond site.js.
        assertThat(deck).doesNotContain("<script>", " style=", "mermaid.min.js").contains(SiteBuilderTest.STRICT_CSP);
    }

    @Test
    void eachSlideIsALabelledSectionWithItsNumberItsControlsAndTheRevision() {
        fixture.write(Decks.PATH, Decks.SAMPLE);

        String deck = build().text(DECK);

        assertThat(deck).contains("""
                <section class="slide" id="what-is-built" aria-labelledby="site-slide-1">
                <p class="slide-code">X-D01<span aria-hidden="true"> · 01/03</span><span class="visually-hidden">, \
                slide 1 of 3</span></p>
                <h2 id="site-slide-1">What is built</h2>
                <div class="slide-body">
                <p>The checker runs in every build.</p>
                <ul>
                <li>The model governs the structure</li>
                <li>The gate stops unreviewed dependencies</li>
                </ul>
                </div>
                <footer class="slide-foot">
                <ul class="slide-controls">
                <li class="slide-previous"><a href="#site-deck-index">Previous</a></li>
                <li class="slide-overview"><a href="#site-deck-index">Overview</a></li>
                <li class="slide-next"><a href="#state-today">Next</a></li>
                </ul>
                <p class="slide-source">docs/demos/deck-milestone.md · Revision <code>01234567</code></p>
                </footer>
                </section>
                """);
        assertThat(deck).contains("<section class=\"slide\" id=\"appendix-tools\" aria-labelledby=\"site-slide-4\">\n"
                + "<p class=\"slide-code\">X-D01<span aria-hidden=\"true\"> · A1/1</span><span class=\"visually-hidden\">, "
                + "appendix slide 1 of 1</span></p>\n<h2 id=\"site-slide-4\">Appendix: tools</h2>",
                "<li class=\"slide-previous\"><a href=\"#one-change-the-gate-rejects\">Previous</a></li>\n<li "
                        + "class=\"slide-overview\"><a href=\"#site-deck-index\">Overview</a></li>\n<li "
                        + "class=\"slide-next\"><a href=\"#site-deck-index\">Next</a></li>");
        assertThat(count(deck, "<section class=\"slide\"")).isEqualTo(4);
        assertThat(count(deck, "<ul class=\"slide-controls\">")).isEqualTo(4);
        assertThat(count(deck, "<p class=\"slide-source\">")).isEqualTo(4);
    }

    @Test
    void notesStandOnlyOnTheNotesPageAndEachLinksBackToItsSlide() {
        fixture.write(Decks.PATH, Decks.SAMPLE);

        BuiltSite site = build();
        String deck = site.text(DECK);
        String notes = site.text(NOTES);

        assertThat(deck).doesNotContain("Say first what is not built", "The numbers come from the sources",
                ">Notes</h3>", "id=\"notes");
        assertThat(notes).startsWith("<!DOCTYPE html>\n<html lang=\"en\">")
                .contains("<span class=\"visually-hidden\">Sheet </span>X-N01</p>\n<h1>Milestone: an example — "
                        + "notes</h1>",
                        "<section class=\"deck-note\" id=\"what-is-built\" aria-labelledby=\"site-note-1\">\n<h2 "
                                + "id=\"site-note-1\"><span class=\"register-code\" aria-hidden=\"true\">01/03</span> <span "
                                + "class=\"visually-hidden\">Slide 1 of 3: </span><a href=\"deck-milestone.html"
                                + "#what-is-built\">What is built</a></h2>\n<ul>\n<li>Say first what is not built</li>\n"
                                + "</ul>\n</section>",
                        "<a href=\"deck-milestone.html#appendix-tools\">Appendix: tools</a></h2>\n<p "
                                + "class=\"deck-note-none\">No notes.</p>\n</section>",
                        "<li>Recorded output — not rerun by this site build. Recorded 2026-11-07T14:56:19Z on "
                                + "revision 44ef312:")
                .doesNotContain("<section class=\"slide\"", ">Previous</a>", ">Next</a>");
        assertThat(count(notes, "<section class=\"deck-note\"")).isEqualTo(4);
        assertThat(titleBlock(notes)).contains("<div><dt>Source</dt><dd>docs/demos/deck-milestone.md</dd></div>",
                "<div><dt>Updated</dt><dd>2026-11-07</dd></div>");
    }

    @Test
    void slideTwoCarriesTheRepositorysLedgerFromTheSourcesAndStatesWhatIsDoneByHand() {
        fixture.write(Decks.PATH, Decks.SAMPLE);

        BuiltSite site = build();
        String second = slide(site.text(DECK), "state-today");
        String start = site.text("index.html");

        assertThat(second).contains("<div class=\"slide-ledger\">\n<dl class=\"status-grid\">\n");
        for (String term : List.of("Accepted design", "Planned implementation", "Verified evidence")) {
            // The same statements as the start sheet's, from the same sources.
            assertThat(ledgerRow(second, term)).as(term).startsWith(ledgerRow(start, term));
        }
        assertThat(ledgerRow(second, "Planned implementation")).endsWith(" Manual today: the author of a change states "
                + "its risk level, and the owner records the approval of a level 3 change; the risk-approval check that "
                + "is to enforce this (control.risk-classification) is planned.");
        // The double rule only beside passing evidence with a named scope.
        String evidence = ledgerRow(second, "Verified evidence");
        assertThat(evidence).startsWith("<span class=\"badge badge-shipped\">Tooling tests passing</span> ")
                .contains("These tests verify repository tooling, not shipping behaviour.");
        assertThat(count(site.text(DECK), "badge-shipped")).isEqualTo(1);
        assertThat(slide(site.text(DECK), "one-change-the-gate-rejects")).doesNotContain("badge-shipped")
                .contains("<p>Recorded output — not rerun by this site build. Full record in the notes.</p>");
    }

    @Test
    void theStartSheetLinksTheDeckBesideTheReadingPathsBelowTheTitleTheLedgerAndTheDrawing() {
        fixture.write(Decks.PATH, Decks.SAMPLE);

        String start = build().text("index.html");
        String paths = start.substring(start.indexOf("id=\"reading-paths\""), start.indexOf("id=\"sections\""));

        assertThat(paths).contains("</div>\n<p class=\"start-deck\"><span class=\"register-code\">X-D01</span> <a "
                + "href=\"demos/deck-milestone.html\">Milestone deck</a> <span class=\"badge badge-neutral\">"
                + "Presentation</span></p>\n");
        assertThat(count(start, "deck-milestone.html")).isEqualTo(1);
        assertThat(List.of(start.indexOf("<h1>"), start.indexOf("class=\"status-panel\""),
                start.indexOf("class=\"start-drawing\""), start.indexOf("id=\"reading-paths\""),
                start.indexOf("class=\"start-deck\""))).doesNotContain(-1).isSorted();
    }

    @Test
    void withoutADeckNothingLinksOne() {
        BuiltSite site = build();

        assertThat(site.pages()).noneMatch(page -> page.startsWith("demos/deck-"));
        assertThat(site.text("index.html")).doesNotContain("start-deck", "deck-milestone");
        assertThat(site.text("demos/index.html")).doesNotContain("register-feature", "Presentation");
    }

    @Test
    void aDemonstrationMaySendItsReaderToTheDeckAndToASlide() {
        fixture.write(Decks.PATH, Decks.SAMPLE).write(Demos.path("visual"), Demos.VISUAL.replace("Open [how the site is "
                + "drawn](../ui/documentation-site.md)", "Open [the deck](deck-milestone.md#state-today) and [its "
                + "notes](" + SITE + "demos/deck-milestone-notes.html#state-today)"));

        String sheet = build().text("demos/43-visual.html");

        assertThat(sheet).contains("<a rel=\"nofollow\" href=\"deck-milestone.html#state-today\">the deck</a>",
                "<a rel=\"nofollow\" href=\"deck-milestone-notes.html#state-today\">its notes</a>");
    }

    @Test
    void theRepositorysOwnDeckIsPublishedWithTenMainSlidesAndFourInTheAppendix() {
        fixture.write(Decks.PATH, Repository.read(Decks.PATH));

        BuiltSite site = build();
        String deck = site.text(DECK);

        assertThat(count(deck, "<section class=\"slide\"")).isEqualTo(14);
        assertThat(deck).contains("<span aria-hidden=\"true\"> · 01/10</span>", "<span aria-hidden=\"true\"> · 10/10"
                + "</span>", "<span aria-hidden=\"true\"> · A1/4</span>", "<span aria-hidden=\"true\"> · A4/4</span>");
        long planned = Sources.read(fixture.root(), List.of(fixture.reports())).capabilities().capabilities().stream()
                .filter(c -> c.status() == Capability.Status.PLANNED).count();
        assertThat(slide(deck, "the-promises-and-what-you-can-check-today")).contains(planned + " of ")
                .contains("<div class=\"slide-ledger\">");
        assertThat(count(site.text(NOTES), "<section class=\"deck-note\"")).isEqualTo(14);
    }

    private static String slide(String deck, String id) {
        int start = deck.indexOf("<section class=\"slide\" id=\"" + id + "\"");
        assertThat(start).as(id).isNotNegative();
        return deck.substring(start, deck.indexOf("</section>", start));
    }

    private static String navigation(String html) {
        int start = html.indexOf("<nav class=\"site-nav\"");
        return html.substring(start, html.indexOf("</nav>", start));
    }

    private static String titleBlock(String html) {
        int start = html.indexOf("<dl class=\"sheet-meta\">");
        assertThat(start).isNotNegative();
        return html.substring(start, html.indexOf("</dl>", start));
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) {
            count++;
        }
        return count;
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
