package io.github.stigjohnny.docs.site;

import io.github.stigjohnny.docs.Html;
import io.github.stigjohnny.docs.generate.ModuleFacts;
import io.github.stigjohnny.docs.repo.Capability;
import io.github.stigjohnny.docs.repo.DocFile;
import io.github.stigjohnny.docs.repo.Sources;
import java.util.ArrayList;
import java.util.List;

/** The Modules section: an index of the model's modules and the web app, then each specification. */
final class ModulePages {

    static final String INDEX = "modules/index.html";
    private static final String WEB = "web";

    private ModulePages() {
    }

    static List<SitePage> of(SiteContext context) {
        List<SitePage> pages = new ArrayList<>();
        pages.add(index(context));
        for (ModuleFacts module : context.modules()) {
            pages.add(DocPages.of(context, module.spec(), SiteSection.MODULES, module.name(), "",
                    context.sources().capabilities().ofModule(module.id())));
        }
        for (DocFile doc : context.sources().docs()) {
            if (doc.optional("module").filter(WEB::equals).isPresent()) {
                pages.add(DocPages.of(context, doc, SiteSection.MODULES, "Web app workbench", "",
                        context.sources().capabilities().ofModule(WEB)));
            }
        }
        return pages;
    }

    private static SitePage index(SiteContext context) {
        Sources sources = context.sources();
        HtmlTable table = HtmlTable.of("The modules of the API in the model, and the web app",
                "Modules, scrolls sideways", "Module", "Status in the model", "Risk level", "Depends on",
                "Capabilities", "Specification");
        List<Capability> all = new ArrayList<>();
        for (ModuleFacts module : context.modules()) {
            List<Capability> capabilities = sources.capabilities().ofModule(module.id());
            all.addAll(capabilities);
            List<String> uses = module.uses().stream().map(d -> d.other() + " (" + d.words() + ")").toList();
            table.row(Html.escape(module.name()), status(module.status()), Html.escape(module.riskLevel()),
                    Html.escape(uses.isEmpty() ? "nothing" : String.join(", ", uses)), counts(capabilities),
                    SiteContext.link(INDEX, context.map().site(module.spec().path()).orElseThrow(),
                            module.spec().title()));
        }
        for (DocFile doc : sources.docs()) {
            if (doc.optional("module").filter(WEB::equals).isPresent()) {
                List<Capability> capabilities = sources.capabilities().ofModule(WEB);
                all.addAll(capabilities);
                table.row("Web app", Html.escape("a container, not a checked module"), "",
                        Html.escape("the API, through the generated client"), counts(capabilities),
                        SiteContext.link(INDEX, context.map().site(doc.path()).orElseThrow(), doc.title()));
            }
        }
        String body = "<p class=\"prose\">The model's components are the API's modules; each maps to one Java package "
                + "and depends only on the modules the model allows. Arrows in the model point from the module whose "
                + "code depends on the other.</p>\n" + table.html();
        return new SitePage(INDEX, "Modules", "Overview", SiteSection.MODULES, "The seven modules of the API as the "
                + "model defines them, and the web app; each links to its specification.",
                new StatusPanel(Statuses.design("accepted", "Each module's specification is accepted; the model "
                        + "lists its dependencies."), Statuses.implementation(all),
                        Statuses.evidence(all, sources.evidence())),
                List.of(), body, context.mentioned("ADR 0002 ADR 0003", INDEX), Sources.WORKSPACE
                        + " and docs/modules/", false);
    }

    private static String status(String status) {
        return switch (status) {
            case "planned" -> Badge.PLANNED.html("Planned");
            case "built" -> Badge.SHIPPED.html("Built");
            default -> Html.escape(status);
        };
    }

    private static String counts(List<Capability> capabilities) {
        List<String> parts = new ArrayList<>();
        for (Capability.Status status : Capability.Status.values()) {
            long count = capabilities.stream().filter(c -> c.status() == status).count();
            if (count > 0) {
                parts.add(count + " " + status.value());
            }
        }
        return Html.escape(parts.isEmpty() ? "none" : String.join(", ", parts));
    }
}
