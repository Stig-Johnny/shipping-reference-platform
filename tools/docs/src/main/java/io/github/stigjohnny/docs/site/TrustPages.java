package io.github.stigjohnny.docs.site;

import io.github.stigjohnny.docs.Html;
import io.github.stigjohnny.docs.repo.Capability;
import io.github.stigjohnny.docs.repo.DocFile;
import io.github.stigjohnny.docs.repo.RiskPolicy;
import io.github.stigjohnny.docs.repo.Sources;
import java.util.ArrayList;
import java.util.List;

/**
 * The Trust section: the allowlist, the risk levels with the policy's rules, the known gaps, the repository's licence
 * and NOTICE, and the third-party notices.
 */
final class TrustPages {

    static final String INDEX = "trust/index.html";
    static final String RISK = "trust/risk-levels.html";

    private TrustPages() {
    }

    static List<SitePage> of(SiteContext context) {
        Sources sources = context.sources();
        List<SitePage> pages = new ArrayList<>();
        pages.add(index(context));
        pages.add(AllowlistPage.of(context));
        DocFile risk = sources.doc("docs/risk-levels.md").orElseThrow(() -> SiteContext.missing("docs/risk-levels.md"));
        SitePage riskPage = DocPages.of(context, risk, SiteSection.TRUST, "Risk levels", rules(context, sources.risk()),
                controls(sources, "control.risk-classification"));
        List<SitePage.Contents> contents = new ArrayList<>(riskPage.contents());
        contents.add(new SitePage.Contents("path-rules", "Path rules"));
        contents.add(new SitePage.Contents("behaviour-signals", "Behaviour signals"));
        contents.add(new SitePage.Contents("uncertainty-signals", "Uncertainty signals"));
        pages.add(new SitePage(riskPage.path(), riskPage.title(), riskPage.navLabel(), riskPage.section(),
                riskPage.summary(), riskPage.status(), contents, riskPage.body(), riskPage.related(),
                risk.path() + " and " + RiskPolicy.PATH, false));
        DocFile gaps = sources.doc("docs/gaps.md").orElseThrow(() -> SiteContext.missing("docs/gaps.md"));
        pages.add(DocPages.of(context, gaps, SiteSection.TRUST, "Known gaps", "", List.of()));
        pages.add(LegalPages.licence(context));
        pages.add(LegalPages.notice(context));
        pages.add(NoticesPage.of(context));
        pages.add(contributions(context, "docs/contributions.md", "Contribution records"));
        pages.add(contributions(context, "docs/contributions/history.md", "Contribution history"));
        return pages;
    }

    private static SitePage contributions(SiteContext context, String source, String navLabel) {
        DocFile doc = context.sources().doc(source).orElseThrow(() -> SiteContext.missing(source));
        String page = SiteMap.pageOf(source);
        return DocPages.of(context, doc, SiteSection.TRUST, navLabel, new StatusPanel(Statuses.design(doc),
                Badge.NEUTRAL.html("Not applicable") + " Contribution records describe authorship, not a capability.",
                Badge.NEUTRAL.html("Recorded provenance")
                        + " Settings and approvals are recorded from receipts; this build does not verify them."),
                context.links(source, page));
    }

    private static SitePage index(SiteContext context) {
        StringBuilder body = new StringBuilder("<div class=\"card-grid\">\n");
        card(body, AllowlistPage.PAGE, "Dependency allowlist", "Every third-party component, reviewed before use, "
                + "with the owner's acceptance of the reviewed set.");
        card(body, RISK, "Risk levels", "Who must approve a change, and the policy that decides the level.");
        card(body, "trust/gaps.html", "Known gaps", "What a green build does not prove, and what is not built yet.");
        card(body, LegalPages.LICENSE_PAGE, "Repository licence", "The Apache License 2.0, the licence of this "
                + "repository and of this site.");
        card(body, LegalPages.NOTICE_PAGE, "Repository NOTICE", "The attribution notice that every redistribution "
                + "keeps.");
        card(body, NoticesPage.PAGE, "Third-party notices", "The licence of the one third-party file the site "
                + "serves, and what it contains.");
        card(body, "trust/contributions.html", "Contribution records", "Which model and effort contributed to each "
                + "issue, with receipts and explicit unknowns.");
        body.append("</div>\n");
        return new SitePage(INDEX, "Trust", "Overview", SiteSection.TRUST, "How this repository decides what code may "
                + "run, who approves what, and what its checks cannot prove.", new StatusPanel(
                        Statuses.design("accepted", "ADR 0007 (the allowlist) and ADR 0008 (risk levels)."),
                        Statuses.implementation(controls(context.sources(), "control.dependency-allowlist",
                                "control.risk-classification")),
                        Statuses.evidence(controls(context.sources(), "control.dependency-allowlist",
                                "control.risk-classification"), context.sources().evidence())),
                List.of(), body.toString(), context.mentioned("ADR 0007 ADR 0008", INDEX), "security/, policy/ and "
                        + "docs/gaps.md", false);
    }

    private static void card(StringBuilder body, String target, String title, String text) {
        body.append("<div class=\"card\"><p class=\"card-title\">").append(SiteContext.link(INDEX, target, title))
                .append("</p><p>").append(Html.escape(text)).append("</p></div>\n");
    }

    static List<Capability> controls(Sources sources, String... ids) {
        List<String> wanted = List.of(ids);
        return sources.capabilities().controls().stream().filter(c -> wanted.contains(c.id())).toList();
    }

    /** The policy's rules and signals, as committed in policy/risk.json. */
    private static String rules(SiteContext context, RiskPolicy policy) {
        StringBuilder out = new StringBuilder("<h2 id=\"path-rules\">Path rules</h2>\n<p class=\"prose\">From "
                + "<code>policy/risk.json</code>. Under the accepted policy, every changed path is to be matched against "
                + "every rule; the highest level counts, and a path that matches no rule is level 3. No check applies "
                + "these rules yet.</p>\n");
        HtmlTable paths = HtmlTable.of("Path rules (" + policy.pathRules().size() + ")", "Path rules, scrolls sideways",
                "Rule", "Level", "Paths", "Reason");
        for (RiskPolicy.Rule rule : policy.pathRules()) {
            paths.row("<code>" + Html.escape(rule.id()) + "</code>", Html.escape(rule.level()), code(rule.patterns()),
                    Html.escape(rule.reason()));
        }
        out.append(paths.html()).append("<h2 id=\"behaviour-signals\">Behaviour signals</h2>\n");
        HtmlTable behaviour = HtmlTable.of("Behaviour signals (" + policy.behaviourSignals().size() + ")",
                "Behaviour signals, scrolls sideways", "Signal", "Level", "Applies to", "Reason");
        for (RiskPolicy.Rule rule : policy.behaviourSignals()) {
            behaviour.row("<code>" + Html.escape(rule.id()) + "</code>", Html.escape(rule.level()),
                    code(rule.patterns()), Html.escape(rule.reason()));
        }
        out.append(behaviour.html()).append("<h2 id=\"uncertainty-signals\">Uncertainty signals</h2>\n");
        HtmlTable uncertainty = HtmlTable.of("Uncertainty signals (" + policy.uncertaintySignals().size() + ")",
                "Uncertainty signals, scrolls sideways", "Signal", "Level", "Except", "Reason");
        for (RiskPolicy.Rule rule : policy.uncertaintySignals()) {
            uncertainty.row("<code>" + Html.escape(rule.id()) + "</code>", Html.escape(rule.level()),
                    code(rule.patterns()), Html.escape(rule.reason()));
        }
        for (String id : List.of("path-rules", "behaviour-signals", "uncertainty-signals")) {
            context.map().anchor(RISK, id);
        }
        return out.append(uncertainty.html()).toString();
    }

    private static String code(List<String> patterns) {
        return String.join(", ", patterns.stream().map(p -> "<code>" + Html.escape(p) + "</code>").toList());
    }
}
