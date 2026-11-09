package io.github.stigjohnny.docs.site;

import io.github.stigjohnny.docs.Html;
import io.github.stigjohnny.docs.generate.Brains;
import io.github.stigjohnny.docs.markdown.RenderedMarkdown;
import io.github.stigjohnny.docs.mermaid.MermaidDiagram;
import io.github.stigjohnny.docs.model.C4View;
import io.github.stigjohnny.docs.model.ViewNames;
import io.github.stigjohnny.docs.repo.Capability;
import io.github.stigjohnny.docs.repo.DeckRecord;
import io.github.stigjohnny.docs.repo.DocFile;
import io.github.stigjohnny.docs.repo.Sources;
import java.util.List;

/**
 * The start sheet: purpose, state, the system context drawn, the two reading paths (the COO's ends with the
 * demonstrations) with a compact link to each milestone deck beside them, the sections and the synthetic-data
 * statement; the README; and how the site itself is drawn.
 */
final class StartPages {

    static final String INDEX = "index.html";
    static final String README = "start/repository.html";
    static final String SITE_DESIGN = "start/site-design.html";
    static final String SITE_DESIGN_SOURCE = "docs/ui/documentation-site.md";

    private StartPages() {
    }

    static List<SitePage> of(SiteContext context) {
        DocFile design = context.sources().doc(SITE_DESIGN_SOURCE)
                .orElseThrow(() -> SiteContext.missing(SITE_DESIGN_SOURCE));
        return List.of(start(context), readme(context),
                DocPages.of(context, design, SiteSection.START, "How this site is drawn", "", List.of()));
    }

    private static SitePage start(SiteContext context) {
        Sources sources = context.sources();
        StringBuilder body = new StringBuilder();
        C4View systemContext = sources.workspace().model().views().stream()
                .filter(v -> v.kind() == C4View.Kind.SYSTEM_CONTEXT).findFirst()
                .orElseThrow(() -> SiteContext.missing("a system context view for the start page"));
        MermaidDiagram diagram = context.diagrams().stream().filter(d -> d.viewKey().equals(systemContext.key()))
                .findFirst().orElseThrow(() -> SiteContext.missing("the diagram of " + systemContext.key()));
        String viewPage = ModelPages.viewPage(systemContext);
        body.append("<section class=\"start-drawing\" aria-labelledby=\"start-drawing-title\">\n")
                .append("<h2 id=\"start-drawing-title\" class=\"visually-hidden\">The system in one drawing</h2>\n")
                .append(ModelPages.figure(systemContext, diagram, diagram.definition(), sources.workspace().model(),
                        "Sheet " + Sheets.code(viewPage) + " lists every element and relationship: "
                                + SiteContext.link(INDEX, viewPage, ViewNames.title(systemContext)) + "."))
                .append("</section>\n");
        body.append("<h2 id=\"reading-paths\">Two reading paths</h2>\n<div class=\"card-grid reading-paths\">\n");
        body.append(path("path-coo", "Reading path for a COO", "What is promised, who approves what, and "
                + "what is not proven yet.", List.of(
                        step(INDEX, INDEX, "Start: what this is and its state"),
                        step(INDEX, flowPage(context), "The main flow: quote, book and download a label"),
                        step(INDEX, "trust/risk-levels.html", "Risk levels: who must approve a change"),
                        step(INDEX, "trust/gaps.html", "Known gaps: what a green build does not prove"),
                        step(INDEX, DemoPages.INDEX, "Demos: what each change did, with its evidence"))));
        body.append(path("path-java", "Reading path for a Java lead", "How the structure is written down and "
                + "checked, from the model to the dependencies.", List.of(
                        step(INDEX, componentView(context), "The API's components and their dependencies"),
                        step(INDEX, "modules/index.html", "Modules: what each one must do"),
                        step(INDEX, "api/index.html", "API: the HTTP contract"),
                        step(INDEX, "decisions/index.html", "Decisions: the architecture decision records"),
                        step(INDEX, "trust/allowlist.html", "Dependency allowlist: every reviewed component"))));
        body.append("</div>\n");
        // Beside the reading paths, below the title, the ledger and the drawing: one compact line per deck.
        for (DeckRecord deck : DeckRecord.all(sources)) {
            String page = context.map().site(deck.doc().path())
                    .orElseThrow(() -> SiteContext.missing("a page for " + deck.doc().path()));
            body.append("<p class=\"start-deck\"><span class=\"register-code\">").append(Sheets.code(page))
                    .append("</span> ").append(DeckPages.link(INDEX, page, deck)).append(' ')
                    .append(Badge.NEUTRAL.html(DeckPages.PRESENTATION)).append("</p>\n");
        }
        body.append("<h2 id=\"sections\">Sections</h2>\n<div class=\"card-grid\">\n");
        for (SiteSection section : SiteSection.values()) {
            if (section != SiteSection.START) {
                body.append(card(section.index(), section.label(), section.purpose()));
            }
        }
        body.append(card(README, "The repository's README", "The idea, the product, the controls and how each can "
                + "fail, and the honest limits."))
                .append(card(SITE_DESIGN, "How this site is drawn", "Sheet codes, the status ledger and what each "
                        + "line and box means."))
                .append("</div>\n");
        body.append("<h2 id=\"the-data\">The data</h2>\n<p class=\"prose\">The domain is generic webshop parcel ")
                .append("shipping: quote a parcel, book it, download a label. Everything on this site is synthetic: ")
                .append("the tenants, users, recipients and addresses are invented, and the carrier is simulated. The ")
                .append("demo tokens in the module documents are public test fixtures, not credentials.</p>\n")
                .append("<h2 id=\"public-site\">What this site publishes</h2>\n<p class=\"prose\">The documentation ")
                .append("site and the repository are both public: the repository is ")
                .append("Stig-Johnny/shipping-reference-platform on GitHub.</p>\n")
                .append("<p class=\"prose\">The site is generated from the repository ")
                .append("by <code>tools/docs</code> and scanned before publication. Paths of files that it does not ")
                .append("publish, such as source code and workflows, appear as plain text.</p>\n");
        return new SitePage(INDEX, "Shipping reference platform", "Start", SiteSection.START,
                firstSentence(Brains.firstParagraph(sources.readme())), repositoryState(sources), List.of(),
                body.toString(), List.of(), Sources.README + " and the documentation sources", true);
    }

    /**
     * The repository's state in the ledger's three statements, from the sources: the start sheet's, which slide 02 of
     * a milestone deck carries as well. The counts come from docs/capabilities/capabilities.yaml, the evidence from the
     * tests that passed in the build that makes the site.
     */
    static StatusPanel repositoryState(Sources sources) {
        List<Capability> all = sources.capabilities().all();
        List<Capability> product = sources.capabilities().capabilities();
        List<Capability> controls = sources.capabilities().controls();
        String counts = count(product, Capability.Status.PLANNED) + " of " + product.size() + " product capabilities "
                + "are planned. Controls: " + count(controls, Capability.Status.SHIPPED) + " shipped, "
                + count(controls, Capability.Status.PARTIAL) + " partial and " + count(controls, Capability.Status.PLANNED)
                + " planned, of " + controls.size() + ".";
        String state = sources.capabilities().specificationOnly()
                ? Badge.PLANNED.html("Specification only") + " " + Html.escape("No application code exists yet. " + counts)
                : Badge.PARTIAL.html("Partly built") + " " + Html.escape(counts);
        return new StatusPanel(Statuses.design("accepted", "The model, the API contract, the decisions and the "
                + "module specifications are accepted; updated " + sources.documentationDate() + "."), state,
                Statuses.evidence(all, sources.evidence()));
    }

    /** The first sentence of a paragraph: the start page's purpose line. */
    static String firstSentence(String paragraph) {
        int end = paragraph.indexOf(". ");
        return end < 0 ? paragraph : paragraph.substring(0, end + 1);
    }

    private static String card(String target, String title, String text) {
        return "<div class=\"card\"><p class=\"card-title\"><span class=\"register-code\">" + Sheets.code(target)
                + "</span> " + SiteContext.link(INDEX, target, title) + "</p><p>" + Html.escape(text) + "</p></div>\n";
    }

    private static SitePage readme(SiteContext context) {
        Sources sources = context.sources();
        RenderedMarkdown rendered = context.render(sources.readme(), Sources.README, README, true);
        List<Capability> all = sources.capabilities().all();
        return new SitePage(README, "The repository's README", "The repository's README", SiteSection.START,
                "The README of the repository as committed: the idea, the product, the controls and how each "
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
        String mainFlow = "docs/flows/quote-book-label.md";
        return context.map().site(mainFlow).orElseThrow(() -> SiteContext.missing(mainFlow));
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
