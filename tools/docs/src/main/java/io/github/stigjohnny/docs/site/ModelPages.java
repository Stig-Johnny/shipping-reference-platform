package io.github.stigjohnny.docs.site;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.Html;
import io.github.stigjohnny.docs.generate.ViewText;
import io.github.stigjohnny.docs.mermaid.MermaidDiagram;
import io.github.stigjohnny.docs.mermaid.MermaidValidator;
import io.github.stigjohnny.docs.model.C4Model;
import io.github.stigjohnny.docs.model.C4View;
import io.github.stigjohnny.docs.model.ModelElement;
import io.github.stigjohnny.docs.model.ViewNames;
import io.github.stigjohnny.docs.repo.Capability;
import io.github.stigjohnny.docs.repo.DocFile;
import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.repo.TestEvidence;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Model section: an overview, one page per C4 view under the diagram template (purpose above, a white panel that
 * can be enlarged from the keyboard, the legend right below, then the element and relationship tables as the text
 * equivalent, and the view's limits), the flows, and the use case overview ({@link UseCasePage}).
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
            pages.add(flow(context, flow));
        }
        pages.add(UseCasePage.of(context));
        return pages;
    }

    /**
     * A flow's page: its document, with the generated, validated diagram of its dynamic view drawn under "Dynamic
     * view" in place of the hand-written sequence's heading. The hand-written sequence stays an unrendered source;
     * Mermaid draws only definitions the adapter generated.
     */
    private static SitePage flow(SiteContext context, DocFile flow) {
        List<Capability> capabilities = new ArrayList<>();
        flow.frontmatter().strings("modules").forEach(m -> capabilities.addAll(
                context.sources().capabilities().ofModule(m)));
        C4Model model = context.sources().workspace().model();
        Optional<C4View> dynamic = model.views().stream()
                .filter(v -> v.kind() == C4View.Kind.DYNAMIC && v.description().contains(flow.path())).findFirst();
        String page = context.map().site(flow.path()).orElseThrow();
        SitePage doc;
        if (dynamic.isPresent()) {
            C4View view = dynamic.get();
            MermaidDiagram diagram = context.diagrams().stream().filter(d -> d.viewKey().equals(view.key()))
                    .findFirst().orElseThrow();
            SitePage rendered = DocPages.of(context, flow, SiteSection.MODEL, flow.title(), "", capabilities,
                    withDynamicView(flow.body()));
            String marker = "<p>" + FIGURE_MARKER + "</p>\n";
            if (rendered.body().indexOf(marker) != rendered.body().lastIndexOf(marker)
                    || !rendered.body().contains(marker)) {
                throw new DocsException(flow.path() + ": the dynamic view's place cannot be found");
            }
            String definition = MermaidValidator.validate(diagram.definition().replaceFirst("(?m)^(\\s*accDescr: ).*$",
                    "$1" + Matcher.quoteReplacement(flowDescription(view, flow.body()))), flow.path());
            String figure = figure(view, diagram, definition, model, "The view's page lists its elements and "
                    + "relationships: "
                    + SiteContext.link(page, viewPage(view), ViewNames.title(view)) + ". The step numbers in the "
                    + "tables below follow the sequence source, which has more steps than this view.");
            context.map().anchor(page, "dynamic-view");
            doc = new SitePage(rendered.path(), rendered.title(), rendered.navLabel(), rendered.section(),
                    rendered.summary(), rendered.status(), rendered.contents(), rendered.body().replace(marker, figure),
                    rendered.related(), rendered.source(), true);
        } else {
            doc = DocPages.of(context, flow, SiteSection.MODEL, flow.title(), "", capabilities);
        }
        List<SitePage.Related> related = new ArrayList<>(doc.related());
        for (C4View view : model.views()) {
            if (view.description().contains(flow.path())) {
                related.addFirst(new SitePage.Related(viewPage(view), ViewNames.title(view)));
            }
        }
        return new SitePage(doc.path(), doc.title(), doc.navLabel(), doc.section(), doc.summary(), doc.status(),
                doc.contents(), widenThreeColumnTables(doc.body()), related, doc.source(), doc.diagrams());
    }

    private static final Pattern TABLE_REGION = Pattern.compile(
            "<div class=\"table-scroll\"( role=\"region\" tabindex=\"0\" aria-label=\"[^\"]*\">\n)(<table>.*?</table>)",
            Pattern.DOTALL);
    private static final Pattern HEAD = Pattern.compile("<thead>(.*?)</thead>", Pattern.DOTALL);

    /** A flow's three-column tables, as the Markdown renderer wrote them, get the flow's minimum width and the hint. */
    static String widenThreeColumnTables(String html) {
        Matcher region = TABLE_REGION.matcher(html);
        StringBuilder out = new StringBuilder();
        while (region.find()) {
            Matcher head = HEAD.matcher(region.group(2));
            boolean threeColumns = head.find() && head.group(1).split("<th[ >]", -1).length - 1 == 3;
            region.appendReplacement(out, Matcher.quoteReplacement(threeColumns
                    ? "<div class=\"" + HtmlTable.Width.FLOW.regionClass() + "\"" + region.group(1) + HtmlTable.HINT
                            + region.group(2)
                    : region.group()));
        }
        region.appendTail(out);
        return out.toString();
    }

    private static final String FIGURE_MARKER = "SITEDYNAMICVIEWFIGURE";
    private static final Pattern SEQUENCE = Pattern.compile("```mermaid\n\\s*sequenceDiagram\n(.*?)```", Pattern.DOTALL);
    private static final Pattern MESSAGE = Pattern.compile(
            "^\\s*[A-Za-z0-9_]+\\s*(-->>|->>|-->|->|--x|-x|--\\)|-\\))\\s*[+-]?[A-Za-z0-9_]+\\s*:");

    /**
     * The flow figure's accessible description, from the data: the view's elements and relationships, the page that
     * lists them, and how many numbered steps the flow's hand-written sequence has, which its step tables follow.
     */
    static String flowDescription(C4View view, String flowMarkdown) {
        StringBuilder text = new StringBuilder("Dynamic view with " + view.elementIds().size() + " elements and "
                + view.steps().size() + " relationships. The linked " + ViewNames.title(view) + " page lists its "
                + "elements and relationships.");
        sequenceSteps(flowMarkdown).ifPresent(steps -> text.append(" The step tables below follow the separate ")
                .append(steps).append("-step sequence."));
        return text.toString();
    }

    /** The numbered messages of a document's first autonumbered sequence diagram, if it has one. */
    static OptionalInt sequenceSteps(String markdown) {
        Matcher fence = SEQUENCE.matcher(markdown);
        if (!fence.find() || fence.group(1).lines().noneMatch(line -> line.strip().equals("autonumber"))) {
            return OptionalInt.empty();
        }
        return OptionalInt.of((int) fence.group(1).lines().filter(line -> MESSAGE.matcher(line).find()).count());
    }

    /** The flow's Markdown with a "Dynamic view" section in place of its "Sequence" heading, or before its first one. */
    static String withDynamicView(String markdown) {
        String section = "## Dynamic view\n\n" + FIGURE_MARKER + "\n";
        List<String> lines = new ArrayList<>(markdown.lines().toList());
        int sequence = lines.indexOf("## Sequence");
        if (sequence >= 0) {
            lines.set(sequence, section);
        } else {
            int first = 0;
            while (first < lines.size() && !lines.get(first).startsWith("## ")) {
                first++;
            }
            lines.add(first, section);
        }
        return String.join("\n", lines) + "\n";
    }

    /**
     * The diagram template: the notes for a reader without the script and for a drawing that failed, then the panel
     * on the light sheet that can be enlarged from the keyboard once its drawing is fitted, with the generated
     * definition that the site's script draws, and the legend right below; {@code captionTail} says where the text
     * equivalent is, in the caption and in both notes.
     */
    static String figure(C4View view, MermaidDiagram diagram, String definition, C4Model model, String captionTail) {
        return figure(ViewNames.slug(view), Sheets.code(viewPage(view)), ViewNames.title(view), definition,
                String.join(" ", ViewText.legend(diagram, model)), captionTail, "Scrolls when enlarged.");
    }

    /**
     * The diagram template for any validated definition: {@code code} is the sheet code in its title, {@code slug}
     * names the figure and the drawing, {@code legend} is plain text, and {@code scrolling} completes the drawing
     * region's accessible name with when and how it scrolls.
     */
    static String figure(String slug, String code, String title, String definition, String legend,
            String captionTail, String scrolling) {
        String figure = "figure-" + slug;
        return "<figure class=\"diagram\" id=\"" + figure + "\">\n"
                + "<div class=\"diagram-toolbar\"><p class=\"diagram-title\">" + code + " · "
                + Html.escape(title) + "</p><button type=\"button\" class=\"button\" data-enlarge=\""
                + figure + "\" aria-pressed=\"false\" hidden>Enlarge diagram</button></div>\n"
                + "<noscript><p class=\"diagram-note\">A script draws this diagram; without it you see its source. "
                + captionTail + "</p></noscript>\n"
                + "<p class=\"diagram-note diagram-failed\" hidden>The diagram could not be drawn here; its source is "
                + "shown instead. " + captionTail + "</p>\n"
                + "<div class=\"diagram-scroll\" role=\"region\" tabindex=\"0\" aria-label=\""
                + Html.escape("Diagram: " + title + ". " + scrolling) + "\">\n"
                + "<pre class=\"diagram-source\" data-diagram=\"" + slug + "\">" + Html.escape(definition)
                + "</pre>\n</div>\n"
                + "<figcaption class=\"diagram-caption\">\n<p><strong>Legend.</strong> "
                + Html.escape(legend) + "</p>\n"
                + "<p>" + captionTail + "</p>\n</figcaption>\n</figure>\n";
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
        boolean allPlanned = modules(sources).stream().allMatch(ModelElement::planned);
        body.append("</ul>\n<h2 id=\"use-cases\">Use cases</h2>\n<p class=\"prose\">")
                .append(SiteContext.link(INDEX, UseCasePage.PAGE, "The use case overview"))
                .append(" draws every product capability with the people and systems that take part, and stamps it "
                        + "with what the tests of the build that generated this site showed.</p>\n");
        body.append("<h2 id=\"checking\">How the model is checked</h2>\n<p class=\"prose\">The model is the "
                + "structural authority (ADR 0002). The checker in <code>libs/c4-contract-kit</code> is implemented "
                + "and its own tests pass. It compares the direct static references in compiled classes with the model "
                + "in both directions: a dependency between two modules that the model does not declare fails the "
                + "check, and so does a REQUIRED relationship without any static reference; a PERMITTED relationship "
                + "without one is reported as \"no static evidence\". It does not evaluate a relationship that touches "
                + "a planned module. " + (allPlanned ? "Every module of the API is planned and has no code yet, so "
                        + "these views are the accepted design, not verified structure." : "Where a relationship "
                        + "touches a planned module, the views show the accepted design, not verified structure.")
                + "</p>\n");
        return new SitePage(INDEX, "Model", "Overview", SiteSection.MODEL, "The C4 model in architecture/workspace.dsl, "
                + "from the context to the API's modules and one flow through them. Each view has its own page with "
                + "its text equivalent.", status(context, modules(sources)), List.of(
                        new SitePage.Contents("views", "The views"), new SitePage.Contents("flows", "Flows"),
                        new SitePage.Contents("use-cases", "Use cases"),
                        new SitePage.Contents("checking", "How the model is checked")),
                body.toString(), context.mentioned("ADR 0002", INDEX), Sources.WORKSPACE, false);
    }

    private static SitePage view(SiteContext context, C4View view, MermaidDiagram diagram) {
        Sources sources = context.sources();
        C4Model model = sources.workspace().model();
        String page = viewPage(view);
        String title = ViewNames.title(view);
        StringBuilder body = new StringBuilder();
        body.append("<p class=\"prose\">").append(Html.escape(ViewNames.kind(view) + " of the "
                + (view.scopeName().isEmpty() ? "workspace" : view.scopeName()) + ". In the model this view is "))
                .append("<code>").append(Html.escape(view.key())).append("</code>.</p>\n")
                .append(figure(view, diagram, diagram.definition(), model, "The tables below list every element and "
                        + "relationship of the view."));
        body.append("<h2 id=\"elements\">Elements</h2>\n");
        List<ViewText.ElementRow> elements = ViewText.elements(view, model, sources.elementStatus());
        HtmlTable table = HtmlTable.of("Elements of the view (" + elements.size() + ")", "Elements of " + title
                + ", scrolls sideways", "ID", "Element", "Type", "Technology", "Status", "Description")
                .wide(HtmlTable.Width.MODEL);
        for (ViewText.ElementRow row : elements) {
            table.row(Html.escape(row.id()), Html.escape(row.name()), Html.escape(row.type()),
                    Html.escape(row.technology()), statusWords(row.status()), Html.escape(row.description()));
        }
        body.append(table.html()).append("<h2 id=\"relationships\">Relationships</h2>\n");
        List<ViewText.RelationshipRow> rows = ViewText.relationships(view, model, diagram);
        HtmlTable relationships = HtmlTable.of("Relationships of the view (" + rows.size() + "), in drawing order",
                "Relationships of " + title + ", scrolls sideways",
                view.kind() == C4View.Kind.DYNAMIC ? "Step" : "#", "From", "To", "Description", "Technology", "Line")
                .wide(HtmlTable.Width.MODEL);
        for (ViewText.RelationshipRow row : rows) {
            relationships.row(Html.escape(row.number()), Html.escape(row.from()), Html.escape(row.to()),
                    Html.escape(row.description()), Html.escape(row.technology()), Html.escape(row.line()));
        }
        body.append(relationships.html()).append("<h2 id=\"limits\">What this view cannot show</h2>\n<ul class=\"prose\">\n");
        for (String limit : ViewText.limits(view, model)) {
            body.append("<li>").append(Html.escape(limit)).append("</li>\n");
        }
        body.append("</ul>\n");
        context.map().anchor(page, "elements");
        context.map().anchor(page, "relationships");
        context.map().anchor(page, "limits");
        List<ModelElement> modules = view.elementIds().stream().map(model::element)
                .filter(e -> e.contractId().isPresent()).toList();
        List<ModelElement> webParts = view.elementIds().stream().map(model::element)
                .filter(e -> e.webId().isPresent()).toList();
        List<SitePage.Related> related = new ArrayList<>(context.mentioned(view.description() + " ADR 0002"
                + (webParts.isEmpty() ? "" : " ADR 0012"), page));
        for (ModelElement module : modules) {
            context.map().site("docs/modules/" + module.contractId().orElseThrow() + ".md")
                    .ifPresent(p -> related.add(new SitePage.Related(p, context.titleOf(p))));
        }
        StatusPanel status = !modules.isEmpty() ? status(context, modules)
                : !webParts.isEmpty() ? webStatus(context, webParts) : status(context, modules(sources));
        return new SitePage(page, title, title, SiteSection.MODEL, view.description(), status, List.of(
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
                + Sources.WORKSPACE + "."), implementation,
                checkerEvidence(conformance, context.sources().evidence(), allPlanned));
    }

    /** The web import check's control: its tests are the evidence of a view of the web app's parts. */
    static final String WEB_CONTROL = "control.web-import-boundaries";

    /**
     * A view of the web app's parts: their web status in the model, and the web import check's passing tests under a
     * badge that says what they verify. While every part is planned, the check has evaluated no import of the web app,
     * and the panel says so; the API's modules and their conformance tests are not this view's evidence.
     */
    private static StatusPanel webStatus(SiteContext context, List<ModelElement> parts) {
        TreeMap<String, Long> byStatus = new TreeMap<>();
        parts.forEach(p -> byStatus.merge(p.webStatus().orElse("unknown"), 1L, Long::sum));
        boolean allPlanned = byStatus.keySet().equals(Set.of("planned"));
        List<String> counts = new ArrayList<>();
        byStatus.forEach((status, count) -> counts.add(count + " web " + (count == 1 ? "part" : "parts") + " "
                + status));
        String implementation = (allPlanned ? Badge.PLANNED.html("Planned") : Badge.PARTIAL.html("Partly built"))
                + " " + Html.escape(String.join(", ", counts) + " in the model; a planned part has no code yet"
                + (allPlanned ? ", so the web import check evaluates none of the web app's imports." : "."));
        List<Capability> control = context.sources().capabilities().controls().stream()
                .filter(c -> c.id().equals(WEB_CONTROL)).toList();
        TestEvidence evidence = context.sources().evidence();
        List<String> passing = control.stream().flatMap(c -> c.evidence().stream()).filter(evidence::passed)
                .distinct().toList();
        String verified = passing.isEmpty() ? Statuses.evidence(control, evidence)
                : Badge.PARTIAL.html("Partly enforced") + " " + Statuses.passed(passing, allPlanned
                        ? "These tests verify the web import check on synthetic web apps; no import of the web app has "
                                + "been checked yet."
                        : "These tests verify the web import check, and the built parts' imports against the model, "
                                + "not their behaviour.");
        return new StatusPanel(Statuses.design("accepted", "The structural authority (ADR 0002), in "
                + Sources.WORKSPACE + "; the web app's parts and their import rules follow ADR 0012."),
                implementation, verified);
    }

    /** The API's own check against the model (ADR 0002); the conformance controls' other tests are the checker's. */
    static final String API_CONFORMANCE_TEST = "ArchitectureConformanceTest";

    /**
     * The model pages' evidence: the conformance controls' passing tests, by name, under a badge that says what they
     * verify: the checker, and the built modules' static dependencies once the API's own check passes too, never the
     * modules' behaviour. Without a passing test it is the evidence of any page.
     */
    static String checkerEvidence(List<Capability> conformance, TestEvidence evidence, boolean modulesPlanned) {
        List<String> passing = conformance.stream().flatMap(c -> c.evidence().stream()).filter(evidence::passed)
                .distinct().toList();
        if (passing.isEmpty()) {
            return Statuses.evidence(conformance, evidence);
        }
        if (!modulesPlanned && passing.contains(API_CONFORMANCE_TEST)) {
            return Badge.SHIPPED.html("Conformance tests passing") + " " + Statuses.passed(passing, "These tests "
                    + "verify the checker and the built modules' static dependencies against the model, not their "
                    + "behaviour.");
        }
        return Badge.SHIPPED.html("Checker tests passing") + " " + Statuses.passed(passing, modulesPlanned
                ? "These tests verify the checker; the shipping modules remain planned."
                : "These tests verify the checker, not the shipping modules.");
    }

    private static String statusWords(String status) {
        return switch (status) {
            case "planned" -> Badge.PLANNED.html("Planned");
            case "built" -> Badge.SHIPPED.html("Built");
            default -> Html.escape(status);
        };
    }
}
