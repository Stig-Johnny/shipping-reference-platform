package io.github.stigjohnny.docs.site;

import io.github.stigjohnny.docs.Html;
import io.github.stigjohnny.docs.markdown.MarkdownRenderer;
import io.github.stigjohnny.docs.markdown.Section;
import io.github.stigjohnny.docs.repo.Capability;
import io.github.stigjohnny.docs.repo.DocFile;
import io.github.stigjohnny.docs.repo.TestEvidence;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The Decisions section: an index of the ADRs with their status and date, then each ADR. */
final class DecisionPages {

    static final String INDEX = "decisions/index.html";
    private static final String NOT_ENFORCED = "not enforced yet";
    private static final Pattern TEST_NAME = Pattern.compile("\\b[A-Z][A-Za-z0-9]*Test\\b");

    private DecisionPages() {
    }

    static List<SitePage> of(SiteContext context) {
        List<SitePage> pages = new ArrayList<>();
        List<DocFile> adrs = context.sources().docsUnder("docs/adr/");
        HtmlTable table = HtmlTable.of("Architecture decision records (" + adrs.size() + ")",
                "Decisions, scrolls sideways", "Decision", "Status", "Date", "Enforced by");
        Map<String, String> pageOfNumber = new TreeMap<>();
        for (DocFile adr : adrs) {
            pageOfNumber.put(number(adr), context.map().site(adr.path()).orElseThrow());
        }
        for (DocFile adr : adrs) {
            String page = context.map().site(adr.path()).orElseThrow();
            List<String> enforcedBy = adr.frontmatter().strings("enforcedBy");
            table.row(SiteContext.link(INDEX, page, adr.title()), Statuses.design(adr.status(), "").strip(),
                    Html.escape(adr.text("date")), Html.escape(String.join("; ", enforcedBy)));
            SitePage doc = DocPages.of(context, adr, SiteSection.DECISIONS, adr.title().replaceFirst("^ADR ", ""), "",
                    List.of());
            Optional<String> successor = adr.optional("supersededBy").filter(n -> "superseded".equals(adr.status()));
            List<String> predecessors = adrs.stream()
                    .filter(other -> adr.optional("supersededBy").isEmpty()
                            && other.optional("supersededBy").filter(number(adr)::equals).isPresent())
                    .map(DecisionPages::number).toList();
            StatusPanel status = successor.isPresent()
                    ? superseded(page, successor.get(), String.valueOf(pageOfNumber.get(successor.get())))
                    : status(adr, context.sources().evidence(), page, predecessors, pageOfNumber,
                            context.sources().capabilities().documentedIn(adr.path()));
            pages.add(new SitePage(doc.path(), doc.title(), doc.navLabel(), doc.section(), doc.summary(), status,
                    doc.contents(), doc.body(), doc.related(), doc.source(), false,
                    successor.isPresent() ? "Superseded" : null));
        }
        pages.addFirst(new SitePage(INDEX, "Decisions", "Overview", SiteSection.DECISIONS, "Every significant decision "
                + "is a numbered record with its context, the options considered, the outcome and the check that "
                + "confirms it (ADR 0001).", new StatusPanel(Statuses.design("accepted", "All records are accepted "
                        + "decisions unless their status says otherwise."),
                        Badge.NEUTRAL.html("See each record") + " Each record lists the enforcement it plans.",
                        Badge.NEUTRAL.html("See each record") + " Each record lists the checks that enforce it."),
                List.of(), table.html(), context.mentioned("ADR 0001", INDEX), "docs/adr/", false));
        return pages;
    }

    private static String linked(String text, String anchor) {
        return anchor.isEmpty() ? Html.escape(text) : "<a href=\"#" + Html.escape(anchor) + "\">" + Html.escape(text)
                + "</a>";
    }

    /** The four-digit number of an ADR, from its file name. */
    private static String number(DocFile adr) {
        return adr.path().substring("docs/adr/".length(), "docs/adr/".length() + 4);
    }

    /** A superseded ADR decides nothing any more: its status names and links the record that replaced it. */
    private static StatusPanel superseded(String page, String successor, String successorPage) {
        String link = SiteContext.link(page, successorPage, "ADR " + successor);
        return new StatusPanel(Badge.NEUTRAL.html("Superseded") + " Superseded by " + link + ".",
                Badge.NEUTRAL.html("Not applicable") + " A superseded record plans no enforcement.",
                Badge.NEUTRAL.html("Not applicable") + " The checks of " + link + " apply instead.");
    }

    /** An ADR's own status: its decision, its planned enforcement, and the enforcement that exists. */
    private static StatusPanel status(DocFile adr, TestEvidence evidence, String page, List<String> predecessors,
            Map<String, String> pageOfNumber, List<Capability> documented) {
        List<String> planned = adr.frontmatter().has("plannedEnforcement")
                ? adr.frontmatter().strings("plannedEnforcement") : List.of();
        String implementation = planned.isEmpty() ? Badge.NEUTRAL.html("None planned") + " Nothing is listed."
                : Badge.PLANNED.html("Planned") + " " + Html.escape(String.join("; ", planned) + ".");
        List<String> enforcedBy = adr.frontmatter().strings("enforcedBy");
        String verified;
        if (enforcedBy.contains(NOT_ENFORCED) || enforcedBy.isEmpty()) {
            verified = Badge.NONE.html("None yet") + " Not enforced yet.";
        } else {
            Set<String> passing = new LinkedHashSet<>();
            for (String entry : enforcedBy) {
                Matcher test = TEST_NAME.matcher(entry);
                while (test.find()) {
                    if (evidence.passed(test.group())) {
                        passing.add(test.group());
                    }
                }
            }
            verified = (passing.isEmpty() ? Badge.NEUTRAL.html("Enforced") : Badge.SHIPPED.html("Passing")) + " "
                    + Html.escape(String.join("; ", enforcedBy) + "." + (passing.isEmpty() ? ""
                            : " Passed in the build that generated this site: " + String.join(", ", passing) + "."));
        }
        List<Capability> summarised = documented.stream()
                .filter(c -> !c.enforcedSubset().isEmpty() || !c.plannedRemainder().isEmpty()).toList();
        if (!summarised.isEmpty()) {
            // The control's own two sentences, linked to the details in the record's implementation status.
            String anchor = MarkdownRenderer.headings(adr.body(), adr.path()).stream()
                    .filter(h -> h.text().startsWith("Implementation status")).map(Section::id).findFirst().orElse("");
            implementation = Badge.PLANNED.html("Planned") + " " + linked(String.join(" ", summarised.stream()
                    .map(Capability::plannedRemainder).filter(t -> !t.isEmpty()).toList()), anchor);
            verified = Badge.PARTIAL.html("Partly enforced") + " " + linked(String.join(" ", summarised.stream()
                    .map(Capability::enforcedSubset).filter(t -> !t.isEmpty()).toList()), anchor);
        }
        StringBuilder design = new StringBuilder(Statuses.design(adr.status(), "Decided " + adr.text("date")
                + "; updated " + adr.updated() + "."));
        for (String predecessor : predecessors) {
            design.append(" Supersedes ").append(SiteContext.link(page, String.valueOf(pageOfNumber.get(predecessor)),
                    "ADR " + predecessor)).append('.');
        }
        return new StatusPanel(design.toString(), implementation, verified);
    }
}
