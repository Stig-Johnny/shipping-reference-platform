package io.github.stigjohnny.docs.site;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stigjohnny.docs.generate.Diagrams;
import io.github.stigjohnny.docs.generate.ModuleFacts;
import io.github.stigjohnny.docs.repo.Capabilities;
import io.github.stigjohnny.docs.repo.Capability;
import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.testing.Fixture;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BrainMapTest {
    @TempDir Path dir;

    @Test
    void mapPublishesTypedSourceableRelationsAndSeparateStatuses() {
        String html = brain(Fixture.copy(dir).passingEvidence());
        assertThat(html).contains("data-brain-node=\"shipments\"", "data-kind=\"module\"",
                "data-from=\"shipments\" data-to=\"quotes\" data-relation=\"dependency\"",
                "data-relation=\"decision\"", "data-relation=\"evidence\"",
                "Module status: built", "Decision status: accepted", "architecture/workspace.dsl",
                "data-brain-node=\"shipments.book-from-offer\"")
                .doesNotContain("data-brain-node=\"web.");
    }

    @Test
    void evidenceEdgesRequirePassingReportsInThisBuild() {
        String html = brain(Fixture.copy(dir));
        assertThat(html).contains("data-relation=\"dependency\"")
                .doesNotContain("data-relation=\"evidence\"");
    }

    @Test
    void editedModelDescriptionFlowsThroughToTheMap() {
        Fixture fixture = Fixture.copy(dir).passingEvidence()
                .edit("architecture/workspace.dsl", text -> text.replace("Books only against an existing, unexpired offer", "Reads the chosen offer"));
        assertThat(brain(fixture)).contains("Reads the chosen offer");
    }

    @Test
    void failedEvidenceCannotAcquireAnArrow() {
        Fixture fixture = Fixture.copy(dir).passingEvidence()
                .report("io.github.stigjohnny.docs.generate.BrainGeneratorTest", 1, 1, 0, 0);
        assertThat(brain(fixture)).contains("data-relation=\"evidence\"")
                .doesNotContain("data-to=\"test-BrainGeneratorTest\"");
    }

    @Test
    void removingAModelDependencyRemovesItsMapArrow() {
        Fixture fixture = Fixture.copy(dir).passingEvidence().edit("architecture/workspace.dsl",
                text -> text.replaceFirst("(?s)        returns -> shipments \"Will create.*?\\n        }", ""));
        assertThat(brain(fixture)).doesNotContain(
                "data-from=\"returns\" data-to=\"shipments\" data-relation=\"dependency\"");
    }

    @Test
    void decisionAndCapabilityGroupsPrintSharedProvenanceOnceAndKeepEveryTarget() {
        Fixture fixture = Fixture.copy(dir).passingEvidence();
        Sources sources = sources(fixture);
        ModuleFacts quotes = ModuleFacts.all(sources).stream().filter(module -> module.id().equals("quotes"))
                .findFirst().orElseThrow();
        String article = article(brain(sources), "quotes");

        assertThat(article).contains(
                "<details class=\"brain-relation-group\" data-relation-group=\"decision\" open>",
                "<summary>Decisions named by this specification (" + quotes.decisions().size() + ")</summary>",
                "<p class=\"brain-group-meaning\">Specification names this decision.</p>",
                "<p class=\"brain-group-source\">Source: " + quotes.spec().path() + "</p>",
                "<details class=\"brain-relation-group\" data-relation-group=\"ownership\" open>",
                "<summary>Declared capabilities (" + quotes.capabilities().size() + ")</summary>",
                "<p class=\"brain-group-meaning\">Declares capability.</p>",
                "<p class=\"brain-group-source\">Source: " + Capabilities.PATH + "</p>");
        assertThat(targets(article, "decision")).containsExactlyInAnyOrderElementsOf(
                quotes.decisions().stream().map(decision -> "adr-" + decision.number()).toList());
        assertThat(targets(article, "ownership")).containsExactlyInAnyOrderElementsOf(
                quotes.capabilities().stream().map(Capability::id).toList());
        String visibleText = article.replaceAll("<[^>]*>", "");
        assertThat(count(visibleText, "Specification names this decision.")).isEqualTo(1);
        assertThat(count(visibleText, "Declares capability.")).isEqualTo(1);
        assertThat(count(article, "Source: " + quotes.spec().path())).isEqualTo(1);
        assertThat(count(article, "Source: " + Capabilities.PATH)).isEqualTo(1);
    }

    @Test
    void groupCountsAndTargetsFollowTheChangedFixture() {
        Fixture fixture = Fixture.copy(dir).passingEvidence();
        String before = article(brain(fixture), "quotes");
        int decisions = targets(before, "decision").size();
        int capabilities = targets(before, "ownership").size();
        assertThat(before).contains("Decisions named by this specification (" + decisions + ")",
                "Declared capabilities (" + capabilities + ")");

        fixture.edit("docs/modules/quotes.md", text -> text.replace("(ADR 0016)", ""))
                .edit(Capabilities.PATH, text -> text.replaceFirst("module: quotes", "module: shipments"));
        String after = article(brain(fixture), "quotes");

        assertThat(targets(after, "decision")).hasSize(decisions - 1).doesNotContain("adr-0016");
        assertThat(targets(after, "ownership")).hasSize(capabilities - 1);
        assertThat(after).contains("Decisions named by this specification (" + (decisions - 1) + ")",
                "Declared capabilities (" + (capabilities - 1) + ")");
    }

    @Test
    void dependenciesKeepTheirOwnMeaningsAndAreNotMergedIntoAGroup() {
        String article = article(brain(Fixture.copy(dir).passingEvidence()), "shipments");
        assertThat(article)
                .contains("data-to=\"tenants\" data-relation=\"dependency\"",
                        "data-to=\"quotes\" data-relation=\"dependency\"",
                        "<p class=\"brain-meaning\">Depends on: REQUIRED. Resolves the caller&#39;s tenant "
                                + "(tenants.api: CurrentTenant, TenantId)</p>",
                        "<p class=\"brain-meaning\">Depends on: REQUIRED. Books only against an existing, unexpired "
                                + "offer (quotes.api: QuoteOffers, OfferLookup, Money, ServiceLevel)</p>")
                .doesNotContain("data-relation-group=\"dependency\"");
        assertThat(count(article, "<p class=\"brain-meaning\">Depends on: REQUIRED.")).isEqualTo(2);
        assertThat(article(brain(Fixture.copy(dir).passingEvidence()), "returns"))
                .contains("Depends on: PERMITTED, planned. Will create return shipments for delivered shipments");
    }

    @Test
    void aSharedDecisionKeepsEachMentioningModulesProvenance() {
        String html = brain(Fixture.copy(dir).passingEvidence());
        String quotes = article(html, "quotes");
        String tenants = article(html, "tenants");

        assertThat(quotes).contains("data-to=\"adr-0013\" data-relation=\"decision\"",
                "<p class=\"brain-group-source\">Source: docs/modules/quotes.md</p>");
        assertThat(tenants).contains("data-to=\"adr-0013\" data-relation=\"decision\"",
                "<p class=\"brain-group-source\">Source: docs/modules/tenants.md</p>");
        assertThat(quotes).doesNotContain("Source: docs/modules/tenants.md");
    }

    @Test
    void modelTextInRelationProseIsEscaped() {
        Fixture fixture = Fixture.copy(dir).passingEvidence().edit("architecture/workspace.dsl",
                text -> text.replace("Books only against an existing, unexpired offer",
                        "Reads <script>& offers"));
        Sources sources = sources(fixture);
        // Exercise the HTML renderer directly: Mermaid deliberately rejects this diagram label first.
        String article = article(BrainMap.html(new SiteContext(sources, SiteMap.of(sources), List.of(),
                "0123456789abcdef0123456789abcdef01234567")), "shipments");

        assertThat(article).contains("Reads &lt;script&gt;&amp; offers")
                .contains("data-meaning=\"Depends on: REQUIRED. Reads &lt;script&gt;&amp; offers")
                .doesNotContain("Reads <script>& offers");
    }

    @Test
    void everyRelationshipTargetResolvesAndCanonicalGroupsStartOpen() {
        String html = brain(Fixture.copy(dir).passingEvidence());
        assertThat(html).contains(
                "<details class=\"brain-relation-group\" data-relation-group=\"decision\" open>",
                "<details class=\"brain-relation-group\" data-relation-group=\"ownership\" open>");
        Matcher group = Pattern.compile("<details[^>]*data-relation-group=\"[^\"]+\"[^>]*>").matcher(html);
        while (group.find()) {
            assertThat(group.group()).contains(" open>");
        }

        Matcher target = Pattern.compile("data-to=\"([^\"]+)\"").matcher(html);
        while (target.find()) {
            assertThat(html).as(target.group(1)).contains("id=\"brain-node-" + target.group(1) + "\"");
        }
        Set<String> ids = new HashSet<>();
        Matcher id = Pattern.compile("<article id=\"(brain-node-[^\"]+)\"").matcher(html);
        while (id.find()) {
            assertThat(ids.add(id.group(1))).as(id.group(1)).isTrue();
        }
    }

    @Test
    void evidenceStaysVisibleAndStatusWordsStaySeparate() {
        Fixture fixture = Fixture.copy(dir).passingEvidence();
        String html = brain(fixture);
        assertThat(article(html, "quotes")).contains("Module status: built");
        assertThat(article(html, "quotes.synthetic-tariff"))
                .contains("Capability status: shipped", "data-relation=\"evidence\"")
                .doesNotContain("data-relation-group=\"evidence\"");
        assertThat(article(html, "control.brain-check"))
                .contains("Control status: shipped", "data-relation=\"evidence\"")
                .doesNotContain("data-relation-group=\"evidence\"");
    }

    private static Sources sources(Fixture fixture) {
        return Sources.read(fixture.root(), List.of(fixture.reports()));
    }

    private static String brain(Fixture fixture) {
        return brain(sources(fixture));
    }

    private static String brain(Sources sources) {
        return BrainMap.html(new SiteContext(sources, SiteMap.of(sources),
                Diagrams.of(sources.workspace(), sources.elementStatus()),
                "0123456789abcdef0123456789abcdef01234567"));
    }

    private static String article(String html, String id) {
        int start = html.indexOf("<article id=\"brain-node-" + id + "\"");
        assertThat(start).as("article " + id).isNotNegative();
        int end = html.indexOf("</article>", start);
        assertThat(end).as("end of article " + id).isNotNegative();
        return html.substring(start, end + "</article>".length());
    }

    private static List<String> targets(String article, String relation) {
        Matcher matcher = Pattern.compile("data-to=\"([^\"]+)\" data-relation=\"" + relation + "\"")
                .matcher(article);
        List<String> targets = new ArrayList<>();
        while (matcher.find()) {
            targets.add(matcher.group(1));
        }
        return targets;
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) {
            count++;
        }
        return count;
    }
}
