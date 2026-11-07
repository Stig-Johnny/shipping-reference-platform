package io.github.stigjohnny.docs.site;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.Html;
import io.github.stigjohnny.docs.mermaid.MermaidValidator;
import io.github.stigjohnny.docs.model.C4Model;
import io.github.stigjohnny.docs.model.C4Model.Participation;
import io.github.stigjohnny.docs.model.ElementStatus;
import io.github.stigjohnny.docs.model.ModelElement;
import io.github.stigjohnny.docs.repo.Capabilities;
import io.github.stigjohnny.docs.repo.Capability;
import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.repo.TestEvidence;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The use case overview, sheet M-07 of the Model section (docs/ui/documentation-site.md, "The use case overview"):
 * first a register table of every product capability of docs/capabilities/capabilities.yaml as a use case, with its
 * status in the registry and the stamp of what the build that generated the site observed of its acceptance tests
 * ({@link UseCaseStamp}); then the actor map, which the introduction links, drawing each module's use cases with the
 * actors that the registry names for them in a bounded region; the actors; and what a stamp means and which test
 * reports this build read. An arrow leads from an actor that starts a use case, and to an external system that a use
 * case relies on, as the model relates them ({@link C4Model#participation}). Arrows are declared, not traced: a stamp
 * says which named tests passed, never that a use case ran as drawn.
 */
final class UseCasePage {

    static final String PAGE = "model/use-cases.html";
    private static final String SLUG = "use-cases";
    /** The actor map's heading, which the introduction's jump link leads to. */
    private static final String MAP = "actor-map";
    private static final String TITLE = "Use cases";
    private static final String TABLE = "The table above lists every use case with its status, its test evidence and "
            + "its actors.";
    private static final String LEGEND = "A box is an actor: a person or an external system of the model, with its C4 "
            + "type in brackets. A rounded box is a use case, a capability of the registry with its status there in "
            + "parentheses, inside its module. An arrow from an actor: it starts the use case. An arrow to an external "
            + "system: the use case relies on it. A use case without an arrow names no actor in the registry; this "
            + "does not establish how it is triggered. Arrows are what the registry and the model declare; no test "
            + "traced them. A stamp in the table says that named tests passed in this build, not that the use case ran "
            + "as drawn.";

    private UseCasePage() {
    }

    /** Whether the sheet draws its diagram: it does when the registry has a product capability. */
    static boolean draws(Sources sources) {
        return !sources.capabilities().capabilities().isEmpty();
    }

    static SitePage of(SiteContext context) {
        Sources sources = context.sources();
        C4Model model = sources.workspace().model();
        List<Capability> product = sources.capabilities().capabilities();
        Map<String, List<Capability>> modules = new LinkedHashMap<>();
        product.forEach(c -> modules.computeIfAbsent(c.module(), m -> new ArrayList<>()).add(c));
        Map<String, UseCaseStamp> stamps = new LinkedHashMap<>();
        product.forEach(c -> stamps.put(c.id(), UseCaseStamp.of(c.acceptanceTests(), sources.evidence())));
        StringBuilder body = new StringBuilder("<p class=\"prose\">The table stamps each use case with what the build "
                + "that generated this site observed of its acceptance tests: which named tests passed, not that the "
                + "use case ran as drawn. The actor map after it draws who takes part in which use case. <a href=\"#"
                + MAP + "\">Jump to the actor map</a>.</p>\n");
        body.append("<h2 id=\"use-cases\">Use cases and their evidence</h2>\n")
                .append(useCases(modules, model, sources, stamps))
                .append("<h2 id=\"" + MAP + "\">Actor map</h2>\n");
        body.append(draws(sources)
                ? "<p class=\"prose\">Who takes part in which use case, as the registry and the model declare it. "
                        + "Enlarge the diagram and scroll to read its labels.</p>\n"
                        + ModelPages.figure(SLUG, Sheets.code(PAGE), TITLE, definition(modules, model,
                                sources.elementStatus()), LEGEND, TABLE, "Scrolls; enlarge it to read its labels.")
                : "<p class=\"prose\">The registry has no product capability yet, so there is nothing to draw.</p>\n");
        body.append("<h2 id=\"actors\">Actors</h2>\n").append(actors(product, model))
                .append("<h2 id=\"stamps\">What a stamp means</h2>\n").append(meanings(sources.evidence()));
        for (String id : draws(sources) ? List.of("use-cases", MAP, "figure-" + SLUG, "actors", "stamps")
                : List.of("use-cases", MAP, "actors", "stamps")) {
            context.map().anchor(PAGE, id);
        }
        return new SitePage(PAGE, TITLE, TITLE, SiteSection.MODEL, "Every product capability as a use case, grouped "
                + "by module, with the people and systems that take part, its status in the registry and what the "
                + "tests of the build that generated this site showed.",
                new StatusPanel(Statuses.design("accepted", "The capabilities of " + Capabilities.PATH + " and the "
                        + "people and external systems of " + Sources.WORKSPACE + "."),
                        Statuses.implementation(product), evidence(product, stamps, sources.evidence())),
                List.of(new SitePage.Contents("use-cases", "Use cases and their evidence"),
                        new SitePage.Contents(MAP, "Actor map"), new SitePage.Contents("actors", "Actors"),
                        new SitePage.Contents("stamps", "What a stamp means")),
                body.toString(), context.mentioned(Capabilities.PATH + " " + Sources.WORKSPACE + " docs/gaps.md", PAGE),
                Capabilities.PATH + ", " + Sources.WORKSPACE + " and this build's test reports", draws(sources));
    }

    /**
     * The drawing: the actors that start a use case, then each module's use cases as rounded boxes in a group, then
     * the systems that use cases rely on, so that it reads from who starts a use case to what it relies on; then the
     * arrows, in the registry's order. It passes the validator of the Mermaid review, like every definition the site
     * draws.
     */
    static String definition(Map<String, List<Capability>> modules, C4Model model, ElementStatus status) {
        Set<String> ids = new HashSet<>();
        List<String> edges = new ArrayList<>();
        Map<ModelElement, Boolean> actors = new LinkedHashMap<>();
        for (List<Capability> capabilities : modules.values()) {
            for (Capability capability : capabilities) {
                ModelElement part = part(capability, model);
                for (String identifier : capability.actors()) {
                    ModelElement actor = model.actor(identifier).orElseThrow(() -> new DocsException(capability.id()
                            + ": actor '" + identifier + "' is not in the model"));
                    boolean starts = participation(model, actor, part, capability) == Participation.STARTS;
                    actors.merge(actor, starts, Boolean::logicalOr);
                    edges.add(starts ? node("actor", identifier) + " --> " + node("uc", capability.id())
                            : node("uc", capability.id()) + " --> " + node("actor", identifier));
                }
            }
        }
        StringBuilder out = new StringBuilder("flowchart LR\n    accTitle: ").append(TITLE).append('\n')
                .append("    accDescr: Use case overview with ").append(modules.values().stream().mapToInt(List::size)
                        .sum()).append(" use cases in ").append(modules.size()).append(" groups and ")
                .append(actors.size()).append(" actors. The table on the page lists every use case with its actors, "
                        + "its status and its test evidence.\n");
        actors.forEach((actor, starts) -> {
            if (starts) {
                out.append(actor(actor, status, ids));
            }
        });
        for (Map.Entry<String, List<Capability>> module : modules.entrySet()) {
            out.append("    subgraph ").append(unique(node("module", module.getKey()), ids)).append(" [\"")
                    .append(part(module.getValue().getFirst(), model).name()).append("\"]\n");
            for (Capability capability : module.getValue()) {
                String name = capability.id().substring(capability.id().indexOf('.') + 1);
                out.append("        ").append(unique(node("uc", capability.id()), ids)).append("([\"").append(name)
                        .append(" (").append(capability.status().value()).append(")\"])\n");
            }
            out.append("    end\n");
        }
        actors.forEach((actor, starts) -> {
            if (!starts) {
                out.append(actor(actor, status, ids));
            }
        });
        edges.forEach(edge -> out.append("    ").append(edge).append('\n'));
        return MermaidValidator.validate(out.toString(), PAGE);
    }

    private static String actor(ModelElement actor, ElementStatus status, Set<String> ids) {
        String word = status.label(actor);
        return "    " + unique(node("actor", actor.identifier().orElseThrow()), ids) + "[\"" + actor.name() + " ["
                + actor.type() + "]" + (word.isEmpty() ? "" : " (" + word + ")") + "\"]\n";
    }

    /** A node ID that the validator accepts: a prefix, then the name with every other character as an underscore. */
    private static String node(String prefix, String name) {
        return prefix + "_" + name.replaceAll("[^A-Za-z0-9]", "_");
    }

    private static String unique(String id, Set<String> ids) {
        if (!ids.add(id)) {
            throw new DocsException(PAGE + ": two parts of the use case overview would be drawn as " + id);
        }
        return id;
    }

    private static ModelElement part(Capability capability, C4Model model) {
        return model.part(capability.module()).orElseThrow(() -> new DocsException(capability.id() + ": module '"
                + capability.module() + "' is not in the model"));
    }

    private static Participation participation(C4Model model, ModelElement actor, ModelElement part,
            Capability capability) {
        return model.participation(actor, part).orElseThrow(() -> new DocsException(capability.id() + ": the model "
                + "relates actor '" + actor.name() + "' to " + part.name() + " in no way"));
    }

    /** The register table: each module a group, each use case a row with its status, its stamp and its actors. */
    private static String useCases(Map<String, List<Capability>> modules, C4Model model, Sources sources,
            Map<String, UseCaseStamp> stamps) {
        int count = modules.values().stream().mapToInt(List::size).sum();
        HtmlTable table = HtmlTable.of("Use cases (" + count + "), by module", "Use cases, scrolls sideways",
                "Use case", "Status", "Test evidence", "Actors").wide(HtmlTable.Width.USE_CASES);
        for (Map.Entry<String, List<Capability>> module : modules.entrySet()) {
            ModelElement part = part(module.getValue().getFirst(), model);
            String word = sources.elementStatus().label(part);
            String kind = "Component".equals(part.type()) ? "module " : "container ";
            table.group("<span class=\"group-title\">" + Html.escape(part.name()) + "</span> "
                    + "<span class=\"group-record\">" + Html.escape(kind + module.getKey()
                            + (word.isEmpty() ? "" : ", " + word)) + "</span>");
            for (Capability capability : module.getValue()) {
                table.row("<code>" + Html.escape(capability.id()) + "</code> <span class=\"use-case-title\">"
                        + Html.escape(capability.title()) + "</span>", status(capability.status()),
                        Objects.requireNonNull(stamps.get(capability.id())).html(), actorsOf(capability, model, part));
            }
        }
        return table.html();
    }

    private static String actorsOf(Capability capability, C4Model model, ModelElement part) {
        if (capability.actors().isEmpty()) {
            return "No actor declared";
        }
        StringBuilder out = new StringBuilder("<ul class=\"use-case-actors\">");
        for (String identifier : capability.actors()) {
            ModelElement actor = model.actor(identifier).orElseThrow();
            out.append("<li>").append(Html.escape((participation(model, actor, part, capability)
                    == Participation.STARTS ? "Started by " : "Relies on ") + actor.name())).append("</li>");
        }
        return out.append("</ul>").toString();
    }

    private static String status(Capability.Status status) {
        return switch (status) {
            case SHIPPED -> Badge.SHIPPED.html(status.words());
            case PARTIAL -> Badge.PARTIAL.html(status.words());
            case PLANNED -> Badge.PLANNED.html(status.words());
        };
    }

    /** The model's people and external systems, with how many use cases each starts or is relied on by. */
    private static String actors(List<Capability> product, C4Model model) {
        HtmlTable table = HtmlTable.of("People and external systems of the model (" + model.actors().size() + ")",
                "Actors, scrolls sideways", "Actor", "Type", "Use cases", "Description").wide(HtmlTable.Width.MODEL);
        for (ModelElement actor : model.actors()) {
            String identifier = actor.identifier().orElse("");
            int starts = 0;
            int reliedOn = 0;
            for (Capability capability : product) {
                if (capability.actors().contains(identifier)) {
                    if (participation(model, actor, part(capability, model), capability) == Participation.STARTS) {
                        starts++;
                    } else {
                        reliedOn++;
                    }
                }
            }
            List<String> uses = new ArrayList<>();
            if (starts > 0) {
                uses.add("Starts " + starts + (starts == 1 ? " use case" : " use cases"));
            }
            if (reliedOn > 0) {
                uses.add((uses.isEmpty() ? "Relied on by " : "relied on by ") + reliedOn
                        + (reliedOn == 1 ? " use case" : " use cases"));
            }
            table.row(Html.escape(actor.name()), Html.escape(actor.type() + (actor.external() ? ", external" : "")
                            + (actor.simulated() ? ", simulated" : "")),
                    Html.escape(uses.isEmpty() ? "No use case names it." : String.join("; ", uses) + "."),
                    Html.escape(actor.description()));
        }
        return table.html();
    }

    /** What each stamp means, what no stamp means, and which reports this build read. */
    private static String meanings(TestEvidence evidence) {
        StringBuilder out = new StringBuilder("<dl class=\"facts\">\n");
        fact(out, UseCaseStamp.Kind.PASSED, "Every acceptance test that the registry names for the use "
                + "case ran in this build, at least one test each, and none failed, errored or was skipped.");
        fact(out, UseCaseStamp.Kind.FAILING, "At least one named test failed or errored in this build.");
        fact(out, UseCaseStamp.Kind.NOT_RUN, "At least one named test neither passed nor failed: this build "
                + "has no report of it, skipped it or ran no test in it; its name is ambiguous; it is not in the "
                + "repository yet; or it is a TypeScript or JavaScript test, whose reports this build does not read.");
        fact(out, UseCaseStamp.Kind.PLANNED, "Its tests are planned: the registry names no acceptance test for "
                + "the use case, or none of the tests it names is in the repository yet.");
        out.append("</dl>\n<p class=\"prose\">A stamp says which named tests passed in the build that generated this "
                + "site. It is not a runtime trace of the use case: no test recorded that an actor started it or which "
                + "modules it went through, and a test can pass while the drawing is wrong. Comparing the interactions "
                + "that a scenario executes with its diagram is the next increment (")
                .append(SiteContext.link(PAGE, "trust/gaps.html#1-what-the-architecture-check-cannot-prove",
                        "Known gaps, section 1")).append(").</p>\n<p class=\"prose\">");
        List<String> read = evidence.producers().stream().map(p -> (p.task().startsWith(":") ? "<code>"
                + Html.escape(p.task()) + "</code>" : Html.escape(p.task())) + " (" + p.reports()
                + (p.reports() == 1 ? " report)" : " reports)")).toList();
        out.append(read.isEmpty() ? "This build read no JUnit reports, so no named test shows as passed."
                : "This build read the JUnit reports of " + (read.size() == 1 ? read.getFirst()
                        : String.join(", ", read.subList(0, read.size() - 1)) + " and " + read.getLast())
                        + ". A named test that none of them ran shows as not run, never as passed.");
        return out.append("</p>\n").toString();
    }

    private static void fact(StringBuilder out, UseCaseStamp.Kind kind, String meaning) {
        out.append("<div><dt>").append(kind.badge().html(kind.words())).append("</dt><dd>").append(Html.escape(meaning))
                .append("</dd></div>\n");
    }

    /** The ledger's evidence: the acceptance tests that passed, and how many use cases have all of theirs passing. */
    private static String evidence(List<Capability> product, Map<String, UseCaseStamp> stamps, TestEvidence evidence) {
        List<String> passing = product.stream().flatMap(c -> c.acceptanceTests().stream()).filter(evidence::passed)
                .distinct().toList();
        if (passing.isEmpty()) {
            return Badge.NONE.html("None yet") + " No acceptance test of a use case passed in the build that generated "
                    + "this site.";
        }
        long green = stamps.values().stream().filter(s -> s.kind() == UseCaseStamp.Kind.PASSED).count();
        return Badge.SHIPPED.html("Passing") + " " + Statuses.passed(passing, "Every acceptance test passed for "
                + green + " of " + product.size() + " use cases. A passing test is not a runtime trace of its use "
                + "case.");
    }
}
