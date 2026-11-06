package io.github.stigjohnny.docs.site;

import io.github.stigjohnny.docs.Html;
import io.github.stigjohnny.docs.generate.ViewText;
import io.github.stigjohnny.docs.mermaid.MermaidDiagram;
import io.github.stigjohnny.docs.model.C4Model;
import io.github.stigjohnny.docs.model.C4View;
import io.github.stigjohnny.docs.model.ModelElement;
import io.github.stigjohnny.docs.model.ViewNames;
import io.github.stigjohnny.docs.repo.Capability;
import io.github.stigjohnny.docs.repo.DocFile;
import io.github.stigjohnny.docs.repo.Sources;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * The Model section: an overview, one page per C4 view under the diagram template (purpose above, a white panel that
 * can be enlarged from the keyboard, the legend right below, then the element and relationship tables as the text
 * equivalent, and the view's limits), and the flows.
 */
final class ModelPages {

    static final String INDEX = "model/index.html";

    private ModelPages() {
    }

    static List<SitePage> of(SiteContext context) {
        List<SitePage> pages = new ArrayList<>();
        pages.add(index(context));
        for (MermaidDiagram diagram : context.diagrams()) {
            pages.add(view(context, context.sources().workspace().model().view(diagram.viewKey()), diagram));
        }
        for (DocFile flow : context.sources().docsUnder("docs/flows/")) {
            List<Capability> capabilities = new ArrayList<>();
            flow.frontmatter().strings("modules").forEach(m -> capabilities.addAll(
                    context.sources().capabilities().ofModule(m)));
            SitePage page = DocPages.of(context, flow, SiteSection.MODEL, flow.title(), "", capabilities);
            List<SitePage.Related> related = new ArrayList<>(page.related());
            for (C4View view : context.sources().workspace().model().views()) {
                if (view.description().contains(flow.path())) {
                    related.addFirst(new SitePage.Related(viewPage(view), ViewNames.title(view)));
                }
            }
            pages.add(new SitePage(page.path(), page.title(), page.navLabel(), page.section(), page.summary(),
                    page.status(), page.contents(), page.body(), related, page.source(), false));
        }
        return pages;
    }

    static String viewPage(C4View view) {
        return "model/" + ViewNames.slug(view) + ".html";
    }

    private static SitePage index(SiteContext context) {
        Sources sources = context.sources();
        StringBuilder body = new StringBuilder("<h2 id=\"views\">The views</h2>\n<div class=\"card-grid\">\n");
        for (C4View view : sources.workspace().model().views()) {
            body.append("<div class=\"card\"><p class=\"card-title\">")
                    .append(SiteContext.link(INDEX, viewPage(view), ViewNames.title(view)))
                    .append("</p><p class=\"card-meta\">").append(Html.escape(ViewNames.kind(view)))
                    .append("</p><p>").append(Html.escape(view.description())).append("</p></div>\n");
        }
        body.append("</div>\n<h2 id=\"flows\">Flows</h2>\n<ul class=\"prose\">\n");
        for (DocFile flow : sources.docsUnder("docs/flows/")) {
            body.append("<li>").append(SiteContext.link(INDEX, context.map().site(flow.path()).orElseThrow(),
                    flow.title())).append(": ").append(Html.escape(flow.description())).append("</li>\n");
        }
        body.append("</ul>\n<h2 id=\"checking\">How the model is checked</h2>\n<p class=\"prose\">The model is the "
                + "structural authority (ADR 0002). The contract kit in <code>libs/c4-contract-kit</code> is to compare "
                + "it with the compiled classes in both directions: a dependency the model does not allow fails the "
                + "build, and so does a REQUIRED dependency the code lacks. That check is planned, so these views are "
                + "the accepted design, not verified structure.</p>\n");
        return new SitePage(INDEX, "Model", "Overview", SiteSection.MODEL, "The C4 model in architecture/workspace.dsl, "
                + "from the context to the API's modules and one flow through them. Each view has its own page with "
                + "its text equivalent.", status(context, modules(sources)), List.of(
                        new SitePage.Contents("views", "The views"), new SitePage.Contents("flows", "Flows"),
                        new SitePage.Contents("checking", "How the model is checked")),
                body.toString(), context.mentioned("ADR 0002", INDEX), Sources.WORKSPACE, false);
    }

    private static SitePage view(SiteContext context, C4View view, MermaidDiagram diagram) {
        Sources sources = context.sources();
        C4Model model = sources.workspace().model();
        String page = viewPage(view);
        String slug = ViewNames.slug(view);
        String title = ViewNames.title(view);
        String figure = "figure-" + slug;
        StringBuilder body = new StringBuilder();
        body.append("<p class=\"prose\">").append(Html.escape(ViewNames.kind(view) + " of the "
                + (view.scopeName().isEmpty() ? "workspace" : view.scopeName()) + ". In the model this view is "))
                .append("<code>").append(Html.escape(view.key())).append("</code>.</p>\n")
                .append("<figure class=\"diagram\" id=\"").append(figure).append("\">\n")
                .append("<div class=\"diagram-toolbar\"><button type=\"button\" class=\"button\" data-enlarge=\"")
                .append(figure).append("\" aria-pressed=\"false\" hidden>Enlarge diagram</button></div>\n")
                .append("<div class=\"diagram-scroll\" role=\"region\" tabindex=\"0\" aria-label=\"")
                .append(Html.escape("Diagram: " + title + ". Scrolls when enlarged.")).append("\">\n")
                .append("<pre class=\"diagram-source\" data-diagram=\"").append(slug).append("\">")
                .append(Html.escape(diagram.definition())).append("</pre>\n</div>\n")
                .append("<noscript><p class=\"diagram-note\">A script draws this diagram. Without it you see the "
                        + "diagram's source; the tables below list the same elements and relationships.</p></noscript>\n")
                .append("<p class=\"diagram-note diagram-failed\" hidden>The diagram could not be drawn here. The "
                        + "tables below list the same elements and relationships.</p>\n")
                .append("<figcaption class=\"diagram-caption\">\n<p><strong>Legend.</strong> ");
        body.append(Html.escape(String.join(" ", ViewText.legend(diagram, model)))).append("</p>\n")
                .append("<p>The tables below list every element and relationship of the view.</p>\n")
                .append("</figcaption>\n</figure>\n");
        body.append("<h2 id=\"elements\">Elements</h2>\n");
        List<ViewText.ElementRow> elements = ViewText.elements(view, model, sources.elementStatus());
        HtmlTable table = HtmlTable.of("Elements of the view (" + elements.size() + ")", "Elements of " + title
                + ", scrolls sideways", "ID", "Element", "Type", "Technology", "Status", "Description");
        for (ViewText.ElementRow row : elements) {
            table.row(Html.escape(row.id()), Html.escape(row.name()), Html.escape(row.type()),
                    Html.escape(row.technology()), statusWords(row.status()), Html.escape(row.description()));
        }
        body.append(table.html()).append("<h2 id=\"relationships\">Relationships</h2>\n");
        List<ViewText.RelationshipRow> rows = ViewText.relationships(view, model, diagram);
        HtmlTable relationships = HtmlTable.of("Relationships of the view (" + rows.size() + "), in drawing order",
                "Relationships of " + title + ", scrolls sideways",
                view.kind() == C4View.Kind.DYNAMIC ? "Step" : "#", "From", "To", "Description", "Technology", "Line");
        for (ViewText.RelationshipRow row : rows) {
            relationships.row(Html.escape(row.number()), Html.escape(row.from()), Html.escape(row.to()),
                    Html.escape(row.description()), Html.escape(row.technology()), Html.escape(row.line()));
        }
        body.append(relationships.html()).append("<h2 id=\"limits\">What this view cannot show</h2>\n<ul class=\"prose\">\n");
        for (String limit : ViewText.limits(view)) {
            body.append("<li>").append(Html.escape(limit)).append("</li>\n");
        }
        body.append("</ul>\n");
        context.map().anchor(page, "elements");
        context.map().anchor(page, "relationships");
        context.map().anchor(page, "limits");
        List<ModelElement> modules = view.elementIds().stream().map(model::element)
                .filter(e -> e.contractId().isPresent()).toList();
        List<SitePage.Related> related = new ArrayList<>(context.mentioned(view.description() + " ADR 0002", page));
        for (ModelElement module : modules) {
            context.map().site("docs/modules/" + module.contractId().orElseThrow() + ".md")
                    .ifPresent(p -> related.add(new SitePage.Related(p, context.titleOf(p))));
        }
        return new SitePage(page, title, title, SiteSection.MODEL, view.description(),
                status(context, modules.isEmpty() ? modules(sources) : modules), List.of(
                        new SitePage.Contents("elements", "Elements"),
                        new SitePage.Contents("relationships", "Relationships"),
                        new SitePage.Contents("limits", "What this view cannot show")),
                body.toString(), related, Sources.WORKSPACE + ", view " + view.key(), true);
    }

    private static List<ModelElement> modules(Sources sources) {
        return sources.workspace().model().modules();
    }

    /** The model is the accepted design; the modules' state and the conformance controls say the rest. */
    private static StatusPanel status(SiteContext context, List<ModelElement> modules) {
        TreeMap<String, Long> byStatus = new TreeMap<>();
        modules.forEach(m -> byStatus.merge(m.contractStatus().orElse("unknown"), 1L, Long::sum));
        boolean allPlanned = byStatus.keySet().equals(Set.of("planned"));
        List<String> parts = new ArrayList<>();
        byStatus.forEach((status, count) -> parts.add(count + " " + (count == 1 ? "module" : "modules") + " "
                + status));
        String implementation = (allPlanned ? Badge.PLANNED.html("Planned") : Badge.PARTIAL.html("Partly built"))
                + " " + Html.escape(String.join(", ", parts) + " in the model; a planned module has no code yet.");
        List<Capability> conformance = context.sources().capabilities().controls().stream()
                .filter(c -> c.id().startsWith("control.c4-")).toList();
        return new StatusPanel(Statuses.design("accepted", "The structural authority (ADR 0002), in "
                + Sources.WORKSPACE + "."), implementation, Statuses.evidence(conformance, context.sources().evidence()));
    }

    private static String statusWords(String status) {
        return switch (status) {
            case "planned" -> Badge.PLANNED.html("Planned");
            case "built" -> Badge.SHIPPED.html("Built");
            default -> Html.escape(status);
        };
    }
}
