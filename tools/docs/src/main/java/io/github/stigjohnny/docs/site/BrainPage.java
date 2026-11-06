package io.github.stigjohnny.docs.site;

import io.github.stigjohnny.docs.Html;
import io.github.stigjohnny.docs.generate.Brains;
import io.github.stigjohnny.docs.generate.ModuleFacts;
import io.github.stigjohnny.docs.repo.ApiContract;
import io.github.stigjohnny.docs.repo.Capabilities;
import io.github.stigjohnny.docs.repo.Capability;
import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.repo.TestEvidence;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The brain: the capability and control tables with status, risk and evidence, then one disclosure per module with
 * its API, dependencies, decisions and gaps, and the agent context (BRAIN.md) to copy or download.
 */
final class BrainPage {

    static final String PAGE = "brain/index.html";

    private BrainPage() {
    }

    static SitePage of(SiteContext context) {
        Sources sources = context.sources();
        TestEvidence evidence = sources.evidence();
        StringBuilder body = new StringBuilder("<div class=\"actions\">\n")
                .append("<button type=\"button\" class=\"button\" data-copy-target=\"agent-context\" "
                        + "data-status=\"copy-status\" hidden>Copy agent context</button>\n")
                .append(SiteContext.link(PAGE, SiteMap.BRAIN_DOWNLOAD, "Download BRAIN.md")).append('\n')
                .append("<p id=\"copy-status\" class=\"copy-status\" role=\"status\"></p>\n</div>\n");
        body.append("<h2 id=\"capabilities\">Capabilities</h2>\n");
        HtmlTable capabilities = HtmlTable.of("Capabilities (" + sources.capabilities().capabilities().size()
                + "), from docs/capabilities/capabilities.yaml", "Capabilities, scrolls sideways", "Capability",
                "Module", "Status", "Risk", "Evidence", "Acceptance targets");
        for (Capability capability : sources.capabilities().capabilities()) {
            capabilities.row(capabilityCell(context, capability), Html.escape(capability.module()),
                    status(capability), Html.escape(risk(context, capability.module())), evidence(capability, evidence),
                    targets(capability));
        }
        body.append(capabilities.html()).append("<h2 id=\"controls\">Controls</h2>\n");
        HtmlTable controls = HtmlTable.of("Controls (" + sources.capabilities().controls().size() + ")",
                "Controls, scrolls sideways", "Control", "Status", "Check", "Evidence", "Acceptance targets");
        for (Capability control : sources.capabilities().controls()) {
            controls.row(capabilityCell(context, control), status(control), "<code>" + Html.escape(control.check())
                    + "</code>", evidence(control, evidence), targets(control));
        }
        body.append(controls.html()).append("<h2 id=\"modules\">Modules</h2>\n");
        for (ModuleFacts module : context.modules()) {
            body.append(module(context, module));
        }
        body.append("<h2 id=\"agent-context-heading\">Agent context</h2>\n<p class=\"prose\">The text of "
                + "<code>BRAIN.md</code>, generated with this site, for an AI agent to start from.</p>\n")
                .append("<details class=\"agent-context\">\n<summary>Agent context (BRAIN.md)</summary>\n")
                .append("<pre id=\"agent-context\">").append(Html.escape(Brains.root(sources))).append("</pre>\n")
                .append("</details>\n");
        for (String id : List.of("capabilities", "controls", "modules", "agent-context-heading")) {
            context.map().anchor(PAGE, id);
        }

        return new SitePage(PAGE, "Project brain", "Brain", SiteSection.BRAIN, "What is shipped and what is only "
                + "planned, with the evidence for each, then one summary per module. Generated with the site from the "
                + "same sources as BRAIN.md, which agents read first.", new StatusPanel(
                        Statuses.design("accepted", "ADR 0011: a generated, budgeted brain that never claims more "
                                + "than its evidence."), overview(sources.capabilities()),
                        passing(sources.capabilities(), evidence)),
                List.of(new SitePage.Contents("capabilities", "Capabilities"),
                        new SitePage.Contents("controls", "Controls"), new SitePage.Contents("modules", "Modules"),
                        new SitePage.Contents("agent-context-heading", "Agent context")),
                body.toString(), context.mentioned("ADR 0011 docs/gaps.md", PAGE), Capabilities.PATH + ", "
                        + Sources.WORKSPACE + ", " + ApiContract.PATH + " and docs/", false);
    }

    private static String module(SiteContext context, ModuleFacts module) {
        String id = "module-" + module.id();
        context.map().anchor(PAGE, id);
        StringBuilder out = new StringBuilder("<details class=\"module\" id=\"").append(id).append("\">\n<summary>")
                .append(Html.escape(module.name())).append(": ").append(Html.escape(module.status()))
                .append(", risk level ").append(Html.escape(module.riskLevel())).append("</summary>\n<div class=\"module-body\">\n<p>")
                .append(Html.escape(module.description())).append(" ")
                .append(SiteContext.link(PAGE, context.map().site(module.spec().path()).orElseThrow(), "Specification"))
                .append(", ").append(SiteContext.link(PAGE, SiteMap.BRAIN_DOWNLOAD, "BRAIN.md")).append(".</p>\n");
        out.append("<h3>API</h3>\n");
        if (module.operations().isEmpty()) {
            out.append("<p>No HTTP operation.</p>\n");
        } else {
            out.append("<ul>\n");
            for (ApiContract.Operation operation : module.operations()) {
                out.append("<li>").append(SiteContext.link(PAGE, ApiPages.page(operation), operation.id()))
                        .append(": <code>").append(Html.escape(operation.method() + " "
                                + context.sources().contract().serverUrl() + operation.path())).append("</code></li>\n");
            }
            out.append("</ul>\n");
        }
        out.append("<h3>Dependencies</h3>\n<ul>\n");
        for (ModuleFacts.Dependency dependency : module.uses()) {
            out.append("<li>Uses ").append(Html.escape(dependency.other() + " (" + dependency.words() + "): "
                    + dependency.description())).append("</li>\n");
        }
        for (ModuleFacts.Dependency dependency : module.usedBy()) {
            out.append("<li>Used by ").append(Html.escape(dependency.other() + " (" + dependency.words() + "): "
                    + dependency.description())).append("</li>\n");
        }
        if (module.uses().isEmpty() && module.usedBy().isEmpty()) {
            out.append("<li>No dependency on or from another module.</li>\n");
        }
        out.append("</ul>\n<h3>Decisions</h3>\n");
        if (module.decisions().isEmpty()) {
            out.append("<p>Its specification names no decision.</p>\n");
        } else {
            out.append("<ul>\n");
            for (ModuleFacts.Decision decision : module.decisions()) {
                out.append("<li>").append(SiteContext.link(PAGE, context.map().site(decision.path()).orElseThrow(),
                        decision.title())).append("</li>\n");
            }
            out.append("</ul>\n");
        }
        out.append("<h3>Gaps</h3>\n");
        if (module.gaps().isEmpty()) {
            out.append("<p>No module-specific gaps are recorded here. See the ")
                    .append(SiteContext.link(PAGE, "trust/gaps.html", "project-wide known gaps")).append(".</p>\n");
        } else {
            out.append("<ul>\n");
            for (ModuleFacts.Gap gap : module.gaps()) {
                out.append("<li>").append(Html.escape(gap.section())).append(": ")
                        .append(context.fragment(gap.text(), "docs/gaps.md", PAGE)).append("</li>\n");
            }
            out.append("</ul>\n");
        }
        return out.append("</div>\n</details>\n").toString();
    }

    private static String capabilityCell(SiteContext context, Capability capability) {
        String docs = context.map().site(capability.docs()).map(p -> " " + SiteContext.link(PAGE, p, "spec"))
                .orElse("");
        return "<code>" + Html.escape(capability.id()) + "</code> " + Html.escape(capability.title()) + docs;
    }

    /** The overview of every capability and control, counted by status, so that it matches the tables below. */
    static String overview(Capabilities capabilities) {
        List<Capability> all = capabilities.all();
        Badge badge = all.stream().allMatch(c -> c.status() == Capability.Status.PLANNED) ? Badge.PLANNED
                : all.stream().allMatch(c -> c.status() == Capability.Status.SHIPPED) ? Badge.SHIPPED : Badge.PARTIAL;
        String words = badge == Badge.PLANNED ? "Planned" : badge == Badge.SHIPPED ? "Shipped" : "Partly shipped";
        return badge.html(words) + " " + Html.escape(counted(capabilities.capabilities(), "capability",
                "capabilities") + "; " + counted(capabilities.controls(), "control", "controls") + ".");
    }

    private static String counted(List<Capability> entries, String one, String many) {
        List<String> parts = new ArrayList<>();
        for (Capability.Status status : Capability.Status.values()) {
            long count = entries.stream().filter(c -> c.status() == status).count();
            if (count > 0) {
                parts.add(count + " " + (count == 1 ? one : many) + " " + status.value());
            }
        }
        return parts.isEmpty() ? "no " + many : String.join(" and ", parts);
    }

    /** The tests that passed in this build as evidence, and whose they are when only the documentation tool's. */
    static String passing(Capabilities capabilities, TestEvidence evidence) {
        List<Capability> evidenced = capabilities.all().stream()
                .filter(c -> c.evidence().stream().anyMatch(evidence::passed)).toList();
        Set<String> tests = new LinkedHashSet<>();
        evidenced.forEach(c -> c.evidence().stream().filter(evidence::passed).forEach(tests::add));
        if (tests.isEmpty()) {
            return Badge.NONE.html("None yet") + " No test passed as evidence in the build that generated this site.";
        }
        boolean documentationOnly = evidenced.stream().allMatch(c -> c.control() && c.where().startsWith("tools/docs"));
        return Badge.SHIPPED.html("Passing") + " " + Html.escape((documentationOnly ? "Documentation controls only: "
                : "") + String.join(", ", tests) + " passed in the build that generated this site.");
    }

    private static String status(Capability capability) {
        return switch (capability.status()) {
            case SHIPPED -> Badge.SHIPPED.html(capability.status().words());
            case PARTIAL -> Badge.PARTIAL.html(capability.status().words());
            case PLANNED -> Badge.PLANNED.html(capability.status().words());
        };
    }

    private static String evidence(Capability capability, TestEvidence evidence) {
        List<String> passing = new ArrayList<>();
        capability.evidence().stream().filter(evidence::passed).forEach(passing::add);
        return passing.isEmpty() ? "None yet" : Html.escape(String.join(", ", passing));
    }

    private static String targets(Capability capability) {
        return capability.acceptanceTests().isEmpty() ? "None named yet"
                : Html.escape(String.join(", ", capability.acceptanceTests()));
    }

    private static String risk(SiteContext context, String module) {
        return context.modules().stream().filter(m -> m.id().equals(module)).findFirst()
                .map(ModuleFacts::riskLevel).orElse("by path");
    }
}
