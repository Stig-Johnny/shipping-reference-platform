package io.github.stigjohnny.docs.site;

import io.github.stigjohnny.docs.Html;
import io.github.stigjohnny.docs.generate.ModuleFacts;
import io.github.stigjohnny.docs.repo.Capabilities;
import io.github.stigjohnny.docs.repo.Capability;
import io.github.stigjohnny.docs.repo.Sources;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Semantic HTML first; the site's script draws only these typed, source-backed relations. */
final class BrainMap {
    private record Node(String id, String name, String kind, String status, String description, String source,
            String more) { }
    private record Edge(String from, String to, String kind, String meaning, String source) { }

    private BrainMap() { }

    static String html(SiteContext context) {
        Map<String, Node> nodes = new LinkedHashMap<>();
        List<Edge> edges = new ArrayList<>();
        for (ModuleFacts module : context.modules()) {
            String spec = context.map().site(module.spec().path()).orElseThrow();
            String counts = module.capabilities().stream().filter(c -> c.status() == Capability.Status.SHIPPED).count()
                    + " shipped / " + module.capabilities().stream().filter(c -> c.status() == Capability.Status.PARTIAL)
                    .count() + " partial / " + module.capabilities().stream()
                    .filter(c -> c.status() == Capability.Status.PLANNED).count() + " planned capabilities.";
            nodes.put(module.id(), new Node(module.id(), module.name(), "module", module.status(),
                    module.description(), Sources.WORKSPACE,
                    "<p>" + counts + " Module status is separate from capability status.</p><p>"
                    + SiteContext.link(BrainPage.PAGE, spec, "Read specification") + "</p>"));
            for (ModuleFacts.Dependency dependency : module.uses()) {
                edges.add(new Edge(module.id(), dependency.other(), "dependency", "Depends on: "
                        + dependency.words() + ". " + dependency.description(), Sources.WORKSPACE));
            }
            for (ModuleFacts.Decision decision : module.decisions()) {
                String id = "adr-" + decision.number();
                nodes.putIfAbsent(id, new Node(id, "ADR " + decision.number(), "decision", decision.status(),
                        decision.title(), decision.path(), "<p>Decision status does not establish implementation.</p><p>"
                        + SiteContext.link(BrainPage.PAGE, context.map().site(decision.path()).orElseThrow(),
                        "Read decision") + "</p>"));
                edges.add(new Edge(module.id(), id, "decision", "Specification names this decision.",
                        module.spec().path()));
            }
        }
        for (Capability capability : context.sources().capabilities().all()) {
            if (!capability.control() && context.modules().stream().noneMatch(m -> m.id().equals(capability.module()))) {
                continue; // The separate web model remains linked from the full Brain catalogue.
            }
            String kind = capability.control() ? "control" : "capability";
            String specification = context.map().site(capability.docs())
                    .map(path -> SiteContext.link(BrainPage.PAGE, path, "Read specification"))
                    .orElse(Html.escape(capability.docs()) + " (repository source; not published as a sheet)");
            nodes.put(capability.id(), new Node(capability.id(), capability.id(), kind, capability.status().value(),
                    capability.title(), Capabilities.PATH,
                    "<p>" + specification + "</p>"));
            if (!capability.control() && nodes.containsKey(capability.module())) {
                edges.add(new Edge(capability.module(), capability.id(), "ownership", "Declares capability.",
                        Capabilities.PATH));
            }
            for (String test : capability.evidence()) {
                if (!context.sources().evidence().passed(test)) {
                    continue;
                }
                String id = "test-" + test;
                nodes.putIfAbsent(id, new Node(id, test, "test", "passed", "Passed in the build that generated this site. "
                        + "This is evidence for the named capability or control, not a runtime trace.",
                        "test source and this build's JUnit XML reports", ""));
                edges.add(new Edge(capability.id(), id, "evidence", "Evidenced by passing test.",
                        Capabilities.PATH + " and this build's JUnit XML reports"));
            }
        }
        StringBuilder out = new StringBuilder("""
                <section class="brain-explorer" aria-labelledby="map-title">
                <h2 id="map-title" class="visually-hidden">See how shipping fits together.</h2>
                <div class="brain-tools" hidden>
                <div class="brain-views" aria-label="Map view">
                <button type="button" data-brain-view="modules" aria-pressed="true">Modules</button>
                <button type="button" data-brain-view="decisions" aria-pressed="false">Decisions</button>
                <button type="button" data-brain-view="controls" aria-pressed="false">Evidence</button>
                <button type="button" data-brain-view="journey" aria-pressed="false">Walk the demo</button>
                </div>
                <label for="brain-find">Find a module, capability, decision, control or test</label>
                <input type="search" id="brain-find" placeholder="Try booking, tenant or control" autocomplete="off">
                <p id="brain-results-status" role="status"></p><div id="brain-results"></div>
                </div>
                <div class="brain-workspace" hidden>
                <div class="brain-visual">
                <div id="brain-map-view">
                <p id="brain-map-caption"></p>
                <svg id="brain-graph" viewBox="0 0 1000 650" role="group" aria-label="Repository relationship map"></svg>
                <p class="brain-legend">Cyan: built module · Violet: planned module · Gold: selected<br>
                Small points: capabilities. Size and position do not indicate importance.</p>
                </div>
                <div id="brain-journey" hidden>
                <h3>One parcel. A traceable journey.</h3>
                <ol>
                <li><button type="button" data-brain-select="tenants">Sign in</button><p>Choose a synthetic tenant member.</p></li>
                <li><button type="button" data-brain-select="quotes">Price a parcel</button><p>Compare the available offers.</p></li>
                <li><button type="button" data-brain-select="shipments">Book once, safely</button><p>Replay returns the same shipment.</p></li>
                <li><button type="button" data-brain-select="shipments">Find it and get the label</button><p>Open the shipment and download its simulated label.</p></li>
                </ol>
                <p>This is a runtime journey. Map arrows show static code dependencies.</p>
                """);
        out.append(SiteContext.link(BrainPage.PAGE, "model/flow-attended-demo.html", "Open the attended runbook"))
                .append("</div></div><aside id=\"brain-detail\" aria-label=\"Selected record\"></aside></div>\n")
                .append("<details id=\"brain-records\" open><summary>All map records, relationships and sources</summary>\n")
                .append("<p>API modules and their declared capabilities, decisions named by their specifications, "
                        + "and registered controls. The web components have their own Model sheet. Evidence arrows need "
                        + "a test source and an unambiguous passing report in this build. No arrow means no such "
                        + "evidence was read, not that a test failed. Module, capability and decision statuses are "
                        + "separate declarations.</p>\n");
        for (Node node : nodes.values()) {
            out.append("<article id=\"brain-node-").append(Html.escape(node.id())).append("\" data-brain-node=\"")
                    .append(Html.escape(node.id())).append("\" data-kind=\"").append(node.kind())
                    .append("\" data-state=\"").append(Html.escape(node.status())).append("\">\n")
                    .append("<h3 data-brain-title>").append(Html.escape(node.name())).append("</h3>\n<p class=\"brain-state\">")
                    .append(Html.escape(switch (node.kind()) {
                        case "module" -> "Module";
                        case "decision" -> "Decision";
                        case "control" -> "Control";
                        case "test" -> "Test";
                        default -> "Capability";
                    })).append(" status: ").append(Html.escape(node.status())).append("</p>\n")
                    .append("<p class=\"brain-description\">").append(Html.escape(node.description())).append("</p>\n")
                    .append(node.more()).append(relations(node, edges, nodes))
                    .append("<p class=\"brain-source\">Source: ").append(Html.escape(node.source()))
                    .append("</p></article>\n");
        }
        return out.append("</details></section>\n").toString();
    }

    private static String relations(Node node, List<Edge> edges, Map<String, Node> nodes) {
        List<Edge> outgoing = edges.stream().filter(edge -> edge.from().equals(node.id())).toList();
        StringBuilder out = new StringBuilder();
        relationList(out, outgoing.stream().filter(edge -> edge.kind().equals("dependency")).toList(), nodes);
        relationGroup(out, "decision", "Decisions named by this specification",
                outgoing.stream().filter(edge -> edge.kind().equals("decision")).toList(), nodes);
        relationGroup(out, "ownership", "Declared capabilities",
                outgoing.stream().filter(edge -> edge.kind().equals("ownership")).toList(), nodes);
        relationList(out, outgoing.stream().filter(edge -> edge.kind().equals("evidence")).toList(), nodes);
        return out.toString();
    }

    private static void relationGroup(StringBuilder out, String kind, String title, List<Edge> edges,
            Map<String, Node> nodes) {
        if (edges.isEmpty()) {
            return;
        }
        Edge first = edges.get(0);
        boolean shared = edges.stream().allMatch(edge -> edge.meaning().equals(first.meaning())
                && edge.source().equals(first.source()));
        out.append("<details class=\"brain-relation-group\" data-relation-group=\"").append(kind).append("\" open>\n")
                .append("<summary>").append(Html.escape(title)).append(" (").append(edges.size())
                .append(")</summary>\n");
        if (shared) {
            out.append("<p class=\"brain-group-meaning\">").append(Html.escape(first.meaning())).append("</p>\n")
                    .append("<p class=\"brain-group-source\">Source: ").append(Html.escape(first.source()))
                    .append("</p>\n");
        }
        out.append("<ul class=\"brain-relations\">\n");
        for (Edge edge : edges) {
            relationItem(out, edge, nodes, !shared);
        }
        out.append("</ul>\n</details>\n");
    }

    private static void relationList(StringBuilder out, List<Edge> edges, Map<String, Node> nodes) {
        if (edges.isEmpty()) {
            return;
        }
        out.append("<ul class=\"brain-relations\">\n");
        for (Edge edge : edges) {
            relationItem(out, edge, nodes, true);
        }
        out.append("</ul>\n");
    }

    private static void relationItem(StringBuilder out, Edge edge, Map<String, Node> nodes, boolean ownProvenance) {
        Node target = nodes.get(edge.to());
        if (target == null) {
            throw SiteContext.missing("a graph target for " + edge.to());
        }
        out.append("<li data-from=\"").append(Html.escape(edge.from())).append("\" data-to=\"")
                .append(Html.escape(edge.to())).append("\" data-relation=\"").append(edge.kind())
                .append("\" data-meaning=\"").append(Html.escape(edge.meaning())).append("\">")
                .append("<a href=\"#brain-node-").append(Html.escape(target.id())).append("\">")
                .append(Html.escape(target.name())).append("</a>");
        if (ownProvenance) {
            out.append("<p class=\"brain-meaning\">").append(Html.escape(edge.meaning())).append("</p><small>Source: ")
                    .append(Html.escape(edge.source())).append("</small>");
        }
        out.append("</li>\n");
    }
}
