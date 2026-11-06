package io.github.stigjohnny.docs.site;

import io.github.stigjohnny.docs.Html;
import io.github.stigjohnny.docs.repo.DocFile;
import io.github.stigjohnny.docs.repo.TestEvidence;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
        for (DocFile adr : adrs) {
            String page = context.map().site(adr.path()).orElseThrow();
            List<String> enforcedBy = adr.frontmatter().strings("enforcedBy");
            table.row(SiteContext.link(INDEX, page, adr.title()), Statuses.design(adr.status(), "").strip(),
                    Html.escape(adr.text("date")), Html.escape(String.join("; ", enforcedBy)));
            SitePage doc = DocPages.of(context, adr, SiteSection.DECISIONS, adr.title().replaceFirst("^ADR ", ""), "",
                    List.of());
            pages.add(new SitePage(doc.path(), doc.title(), doc.navLabel(), doc.section(), doc.summary(),
                    status(adr, context.sources().evidence()), doc.contents(), doc.body(), doc.related(),
                    doc.source(), false));
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

    /** An ADR's own status: its decision, its planned enforcement, and the enforcement that exists. */
    private static StatusPanel status(DocFile adr, TestEvidence evidence) {
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
        return new StatusPanel(Statuses.design(adr.status(), "Decided " + adr.text("date") + "; updated "
                + adr.updated() + "."), implementation, verified);
    }
}
