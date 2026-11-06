package io.github.stigjohnny.docs.site;

import io.github.stigjohnny.docs.Html;
import io.github.stigjohnny.docs.generate.Brains;
import io.github.stigjohnny.docs.markdown.RenderedMarkdown;
import io.github.stigjohnny.docs.model.C4View;
import io.github.stigjohnny.docs.model.ViewNames;
import io.github.stigjohnny.docs.repo.Capability;
import io.github.stigjohnny.docs.repo.DocFile;
import io.github.stigjohnny.docs.repo.Sources;
import java.util.List;

/** The start page: purpose, state, the synthetic-data statement and the two reading paths; and the README. */
final class StartPages {

    static final String INDEX = "index.html";
    static final String README = "start/repository.html";

    private StartPages() {
    }

    static List<SitePage> of(SiteContext context) {
        return List.of(start(context), readme(context));
    }

    private static SitePage start(SiteContext context) {
        Sources sources = context.sources();
        List<Capability> all = sources.capabilities().all();
        StringBuilder body = new StringBuilder();
        body.append("<h2 id=\"the-data\">The data</h2>\n<p class=\"prose\">Everything on this site is synthetic: the ")
                .append("tenants, users, recipients and addresses are invented, and the carrier is simulated. The demo ")
                .append("tokens in the module documents are public test fixtures, not credentials.</p>\n")
                .append("<h2 id=\"public-site\">What this site publishes</h2>\n<p class=\"prose\">This site is ")
                .append("generated from the repository by <code>tools/docs</code> and scanned before publication. ")
                .append("Paths of files that it does not publish, such as source code and workflows, appear as plain ")
                .append("text, and it does not link to the repository on GitHub.</p>\n");
        body.append("<h2 id=\"reading-paths\">Two reading paths</h2>\n<div class=\"card-grid\">\n");
        body.append(path("path-coo", "Reading path for a COO", "What is promised, who approves what, and "
                + "what is not proven yet.", List.of(
                        step(INDEX, INDEX, "Start: what this is and its state"),
                        step(INDEX, flowPage(context), "The main flow: quote, book and download a label"),
                        step(INDEX, "trust/risk-levels.html", "Risk levels: who must approve a change"),
                        step(INDEX, "trust/gaps.html", "Known gaps: what a green build does not prove"))));
        body.append(path("path-java", "Reading path for a Java lead", "How the structure is written down and "
                + "checked, from the model to the dependencies.", List.of(
                        step(INDEX, componentView(context), "The API's components and their dependencies"),
                        step(INDEX, "modules/index.html", "Modules: what each one must do"),
                        step(INDEX, "api/index.html", "API: the HTTP contract"),
                        step(INDEX, "decisions/index.html", "Decisions: the architecture decision records"),
                        step(INDEX, "trust/allowlist.html", "Dependency allowlist: every reviewed component"))));
        body.append("</div>\n<h2 id=\"sections\">Sections</h2>\n<div class=\"card-grid\">\n");
        for (SiteSection section : SiteSection.values()) {
            if (section != SiteSection.START) {
                body.append("<div class=\"card\"><p class=\"card-title\">")
                        .append(SiteContext.link(INDEX, section.index(), section.label())).append("</p><p>")
                        .append(Html.escape(section.purpose())).append("</p></div>\n");
            }
        }
        body.append("<div class=\"card\"><p class=\"card-title\">")
                .append(SiteContext.link(INDEX, README, "The repository's README"))
                .append("</p><p>The idea, the product, the controls and how each can fail, and the honest limits.</p>"
                        + "</div>\n</div>\n");
        String counts = count(all, Capability.Status.PLANNED) + " of " + all.size()
                + " capabilities and controls are planned.";
        String state = sources.capabilities().specificationOnly()
                ? Badge.PLANNED.html("Specification only") + " " + Html.escape("No application code exists yet. " + counts)
                : Badge.PARTIAL.html("Partly built") + " " + Html.escape(counts);
        StatusPanel status = new StatusPanel(Statuses.design("accepted", "The model, the API contract, the "
                + "decisions and the module specifications are accepted; updated " + sources.documentationDate() + "."),
                state, Statuses.evidence(all, sources.evidence()));
        return new SitePage(INDEX, "Shipping reference platform", "Start", SiteSection.START,
                Brains.firstParagraph(sources.readme()), status, List.of(
                        new SitePage.Contents("the-data", "The data"),
                        new SitePage.Contents("public-site", "What this site publishes"),
                        new SitePage.Contents("reading-paths", "Two reading paths"),
                        new SitePage.Contents("sections", "Sections")),
                body.toString(), List.of(), Sources.README + " and the documentation sources", false);
    }

    private static SitePage readme(SiteContext context) {
        Sources sources = context.sources();
        RenderedMarkdown rendered = context.render(sources.readme(), Sources.README, README, true);
        List<Capability> all = sources.capabilities().all();
        return new SitePage(README, "The repository's README", "The repository's README", SiteSection.START,
                "The README of the private repository as committed: the idea, the product, the controls and how each "
                        + "can fail, and the honest limits.",
                new StatusPanel(Statuses.design("accepted", "The README summarises the accepted specification."),
                        Statuses.implementation(all), Statuses.evidence(all, sources.evidence())),
                DocPages.contents(rendered.headings()), rendered.html(),
                context.mentioned(sources.readme(), README), Sources.README, false);
    }

    private static String path(String id, String title, String purpose, List<String> steps) {
        StringBuilder out = new StringBuilder("<section class=\"card reading-path\" aria-labelledby=\"")
                .append(id).append("\">\n<h3 id=\"").append(id).append("\">").append(Html.escape(title))
                .append("</h3>\n<p>").append(Html.escape(purpose)).append("</p>\n<ol>\n");
        for (String step : steps) {
            out.append("<li>").append(step).append("</li>\n");
        }
        return out.append("</ol>\n</section>\n").toString();
    }

    private static String step(String from, String target, String text) {
        return target.equals(from) ? Html.escape(text) + " (this page)" : SiteContext.link(from, target, text);
    }

    private static String flowPage(SiteContext context) {
        List<DocFile> flows = context.sources().docsUnder("docs/flows/");
        if (flows.isEmpty()) {
            throw SiteContext.missing("a flow under docs/flows/ for the COO's reading path");
        }
        return context.map().site(flows.getFirst().path()).orElseThrow();
    }

    private static String componentView(SiteContext context) {
        C4View view = context.sources().workspace().model().views().stream()
                .filter(v -> v.kind() == C4View.Kind.COMPONENT).findFirst()
                .orElseThrow(() -> SiteContext.missing("a component view for the Java lead's reading path"));
        return "model/" + ViewNames.slug(view) + ".html";
    }

    private static long count(List<Capability> capabilities, Capability.Status status) {
        return capabilities.stream().filter(c -> c.status() == status).count();
    }
}
