package io.github.stigjohnny.docs.generate;

import static io.github.stigjohnny.docs.generate.MarkdownText.cell;
import static io.github.stigjohnny.docs.generate.MarkdownText.code;
import static io.github.stigjohnny.docs.generate.MarkdownText.yaml;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.markdown.MarkdownRenderer;
import io.github.stigjohnny.docs.markdown.Section;
import io.github.stigjohnny.docs.model.ModelElement;
import io.github.stigjohnny.docs.repo.Acceptance;
import io.github.stigjohnny.docs.repo.Allowlist;
import io.github.stigjohnny.docs.repo.ApiContract;
import io.github.stigjohnny.docs.repo.Capability;
import io.github.stigjohnny.docs.repo.DocFile;
import io.github.stigjohnny.docs.repo.Sources;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * {@code BRAIN.md} and {@code docs/brain/<module>.md} (ADR 0011): a compact, current entry point for agents that
 * never claims more than the evidence supports. Each file is generated from the sources, links to the full documents
 * instead of copying them, and stays within its byte budget: 12 KiB for the root, 8 KiB per module.
 */
public final class Brains {

    public static final int ROOT_BUDGET = 12 * 1024;
    public static final int MODULE_BUDGET = 8 * 1024;
    public static final String DIRECTORY = "docs/brain/";
    private static final String NOTICE = ArchitecturePages.NOTICE;
    private static final String REGENERATE = code("./gradlew :tools:docs:generate") + "; "
            + code(":tools:docs:checkGenerated") + " fails on any difference.";

    private Brains() {
    }

    public static Map<String, String> generate(Sources sources) {
        Map<String, String> files = new TreeMap<>();
        List<ModuleFacts> modules = ModuleFacts.all(sources);
        files.put(Sources.BRAIN, budget(Sources.BRAIN, root(sources, modules), ROOT_BUDGET));
        for (ModuleFacts module : modules) {
            String path = DIRECTORY + module.id() + ".md";
            files.put(path, budget(path, module(sources, module), MODULE_BUDGET));
        }
        return files;
    }

    /** The text of BRAIN.md, which the site also offers as the agent context. */
    public static String root(Sources sources) {
        return root(sources, ModuleFacts.all(sources));
    }

    static String budget(String path, String text, int budget) {
        int bytes = text.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > budget) {
            throw new DocsException(path + " is " + bytes + " bytes; its budget is " + budget
                    + " bytes (ADR 0011). Shorten its sources or link instead of copying.");
        }
        return text;
    }

    private static String root(Sources sources, List<ModuleFacts> modules) {
        StringBuilder out = new StringBuilder();
        frontmatter(out, "Brain: shipping-reference-platform", "The generated entry point for agents: what the "
                + "repository is, what is built and what is only planned, and where each module, decision, control "
                + "and limit is written down.", sources.documentationDate());
        out.append("> ").append(NOTICE).append(". Sources: ").append(code(Sources.README)).append(", ")
                .append(code(Sources.WORKSPACE)).append(", ").append(code("docs/")).append(", ")
                .append(code("contracts/")).append(", ").append(code("policy/risk.json")).append(" and ")
                .append(code(Allowlist.PATH)).append(".\n> Regenerate with ").append(REGENERATE).append("\n\n")
                .append("# Brain: shipping-reference-platform\n\n")
                .append(firstParagraph(sources.readme())).append("\n\n")
                .append("**State at this commit.** ").append(state(sources)).append(" A planned capability has no "
                        + "passing acceptance test; the test names listed for it are targets, not evidence.\n\n");
        out.append("## Read first\n\n")
                .append("- [README.md](README.md): the idea, the controls and how each can fail.\n")
                .append("- [AGENTS.md](AGENTS.md): rules for agents, with scoped files in `apps/*`, `libs/*` and "
                        + "`security/`.\n")
                .append("- [docs/gaps.md](docs/gaps.md): what a green build does not prove.\n")
                .append("- [Human site design](docs/ui/documentation-site.md): generated page search and a visual map "
                        + "of API modules, decisions and passing evidence; prose does not create dependency arrows.\n")
                .append("- [docs/risk-levels.md](docs/risk-levels.md) and `policy/risk.json`: who approves what.\n\n");
        out.append("## Modules\n\nFrom ").append(code(Sources.WORKSPACE)).append(". Arrows in the model point from "
                + "the module whose code depends on the other.\n\n")
                .append("| Module | Status | Risk | Depends on | Brain | Specification |\n")
                .append("|---|---|---|---|---|---|\n");
        for (ModuleFacts module : modules) {
            List<String> uses = module.uses().stream().map(d -> d.other() + " (" + d.words() + ")").toList();
            out.append("| ").append(code(module.id())).append(" | ").append(module.status()).append(" | ")
                    .append(module.riskLevel()).append(" | ").append(cell(MarkdownText.list(uses, "nothing")))
                    .append(" | [").append(module.id()).append("](docs/brain/").append(module.id()).append(".md) | [")
                    .append(module.spec().path()).append("](").append(module.spec().path()).append(") |\n");
        }
        List<Capability> web = sources.capabilities().ofModule("web");
        List<ModelElement> parts = sources.workspace().model().webParts();
        long plannedParts = parts.stream().filter(ModelElement::planned).count();
        out.append("\nThe web app is a container, not an API module: ").append(web.size())
                .append(" capabilities, specified in [docs/ui/workbench.md](docs/ui/workbench.md). Its ")
                .append(parts.size()).append(" parts are in the model (").append(plannedParts)
                .append(" planned), and the partial web import check compares only detected TypeScript imports with ")
                .append("them (ADR 0015).\n\n");
        out.append("## Controls\n\nFrom ").append(code("docs/capabilities/capabilities.yaml"))
                .append(". Evidence is a test that exists and passed in this build.\n\n")
                .append("| Control | Status | Check | Evidence |\n").append("|---|---|---|---|\n");
        for (Capability control : sources.capabilities().controls()) {
            out.append("| ").append(code(control.id())).append(" | ").append(control.status().value()).append(" | ")
                    .append(code(control.check())).append(" | ")
                    .append(cell(MarkdownText.list(control.evidence(), "none yet"))).append(" |\n");
        }
        out.append("\n## Decisions\n\n| ADR | Status | Enforced by |\n|---|---|---|\n");
        for (DocFile adr : sources.docsUnder("docs/adr/")) {
            String status = adr.optional("supersededBy").filter(n -> "superseded".equals(adr.status()))
                    .map(n -> "superseded by ADR " + n).orElse(adr.status());
            out.append("| [").append(cell(adr.title())).append("](").append(adr.path()).append(") | ")
                    .append(status).append(" | ").append(cell(enforcement(adr))).append(" |\n");
        }
        Allowlist allowlist = sources.allowlist();
        Acceptance accepted = allowlist.current();
        out.append("\n## Third-party components\n\n").append(allowlist.entries().size())
                .append(" entries in ").append(code(Allowlist.PATH)).append(" (").append(allowlist.approved())
                .append(" approved, ").append(allowlist.rejected()).append(" rejected), each with its review record in ")
                .append(code("security/reviews/")).append(". The owner accepted this reviewed set");
        if (!accepted.record().isEmpty()) {
            out.append(" for ").append(accepted.record());
        }
        out.append(": ").append(code("reviewedSetSha256 " + accepted.reviewedSetSha256())).append(". How it works "
                + "and what it cannot stop: [security/README.md](security/README.md).\n\n");
        out.append("## Flows\n\n");
        for (DocFile flow : sources.docsUnder("docs/flows/")) {
            out.append("- [").append(flow.title()).append("](").append(flow.path()).append("): modules ")
                    .append(String.join(", ", flow.frontmatter().strings("modules"))).append(".\n");
        }
        out.append("\n## Known gaps\n\n");
        sources.doc("docs/gaps.md").ifPresent(gaps -> {
            for (Section section : MarkdownRenderer.headings(gaps.body(), gaps.path())) {
                if (section.level() == 2) {
                    out.append("- [").append(section.text()).append("](docs/gaps.md#").append(section.id())
                            .append(")\n");
                }
            }
        });
        out.append("\n## Commands\n\n")
                .append("- ").append(code("./gradlew :tools:docs:check")).append(": the docs tool's tests, the "
                        + "drift check of these generated files and the site build.\n")
                .append("- ").append(code("./gradlew :tools:docs:site")).append(": build the documentation site into ")
                .append(code("tools/docs/build/site/")).append(".\n")
                .append("- ").append(code("./gradlew :tools:docs:generate")).append(": regenerate this file, ")
                .append(code("docs/brain/")).append(" and ").append(code("docs/architecture/")).append(".\n")
                .append("- ").append(code("java tools/policy/PolicyCheck.java .")).append(": the dependency "
                        + "allowlist check.\n");
        return out.toString();
    }

    private static String module(Sources sources, ModuleFacts module) {
        StringBuilder out = new StringBuilder();
        frontmatter(out, "Brain: " + module.id(), "Generated summary of module " + module.id() + " for agents: "
                + "status, dependencies, HTTP operations, capabilities with their evidence, decisions, flows and gaps.",
                sources.documentationDate());
        out.append("> ").append(NOTICE).append(". Sources: ").append(code(Sources.WORKSPACE)).append(", ")
                .append(code(module.spec().path())).append(", ").append(code("docs/capabilities/capabilities.yaml"))
                .append(", ").append(code("contracts/shipping.openapi.yaml")).append(" and ").append(code("docs/"))
                .append(".\n> Regenerate with ").append(REGENERATE).append("\n\n")
                .append("# Brain: ").append(module.id()).append("\n\n")
                .append(module.description()).append("\n\n")
                .append("- **Status in the model:** ").append(module.status()).append(" (").append(code("contract.status"))
                .append(").").append("planned".equals(module.status()) ? " No production class may exist in its "
                        + "packages until the status is built." : "").append('\n')
                .append("- **Packages:** ").append(String.join(", ", module.packages().stream()
                        .map(MarkdownText::code).toList())).append('\n')
                .append("- **Risk level:** ").append(module.riskLevel()).append('\n')
                .append("- **Specification:** [").append(module.spec().path()).append("](../modules/")
                .append(module.id()).append(".md)\n")
                .append("- **Overview of all modules:** [BRAIN.md](../../BRAIN.md)\n\n");
        out.append("## Dependencies\n\n");
        dependencies(out, "Uses", module.uses());
        dependencies(out, "Used by", module.usedBy());
        dependencies(out, "Other relationships, not checked by the Java check", module.other());
        out.append("\n## HTTP operations\n\n");
        if (module.operations().isEmpty()) {
            out.append("None in ").append(code(ApiContract.PATH)).append(".\n");
        }
        for (ApiContract.Operation operation : module.operations()) {
            out.append("- ").append(code(operation.method() + " " + sources.contract().serverUrl() + operation.path()))
                    .append(" ").append(code(operation.id())).append(": ").append(operation.summary()).append('\n');
        }
        out.append("\n## Capabilities\n\n| Capability | Status | Acceptance tests | Evidence |\n|---|---|---|---|\n");
        for (Capability capability : module.capabilities()) {
            out.append("| ").append(code(capability.id())).append(" | ").append(capability.status().value())
                    .append(" | ").append(cell(MarkdownText.list(capability.acceptanceTests(), "none named yet")))
                    .append(" | ").append(cell(MarkdownText.list(capability.evidence(), "none yet"))).append(" |\n");
        }
        out.append("\n## Decisions its specification names\n\n");
        if (module.decisions().isEmpty()) {
            out.append("None.\n");
        }
        for (ModuleFacts.Decision decision : module.decisions()) {
            out.append("- [").append(decision.title()).append("](../adr/")
                    .append(decision.path().substring("docs/adr/".length())).append(") (").append(decision.status())
                    .append(")\n");
        }
        out.append("\n## Flows\n\n");
        if (module.flows().isEmpty()) {
            out.append("None.\n");
        }
        for (DocFile flow : module.flows()) {
            out.append("- [").append(flow.title()).append("](../flows/").append(flow.fileName()).append(")\n");
        }
        out.append("\n## Gaps that name it\n\n");
        if (module.gaps().isEmpty()) {
            out.append("No module-specific gaps are recorded here. See the project-wide known gaps in "
                    + "[docs/gaps.md](../gaps.md).\n");
        }
        for (ModuleFacts.Gap gap : module.gaps()) {
            out.append("- ").append(gap.section()).append(": ").append(gap.text()).append('\n');
        }
        return out.toString();
    }

    private static void dependencies(StringBuilder out, String heading, List<ModuleFacts.Dependency> dependencies) {
        out.append("- **").append(heading).append(":**");
        if (dependencies.isEmpty()) {
            out.append(" none.\n");
            return;
        }
        out.append('\n');
        for (ModuleFacts.Dependency dependency : dependencies) {
            out.append("  - ").append(dependency.mode().isEmpty() ? (dependency.outgoing() ? "to " : "from ") : "")
                    .append(dependency.other()).append(" (").append(dependency.words()).append("): ")
                    .append(dependency.description());
            if (!dependency.technology().isEmpty()) {
                out.append(" [").append(dependency.technology()).append(']');
            }
            out.append('\n');
        }
    }

    private static void frontmatter(StringBuilder out, String title, String description, String updated) {
        out.append("---\ntitle: ").append(yaml(title)).append("\ndescription: ").append(yaml(description))
                .append("\nstatus: accepted\nupdated: ").append(updated).append("\n---\n\n");
    }

    /** The counts of capabilities and controls by status, in words. */
    static String state(Sources sources) {
        return count("capabilities", sources.capabilities().capabilities()) + "; "
                + count("controls", sources.capabilities().controls()) + ".";
    }

    private static String count(String what, List<Capability> entries) {
        List<String> parts = new ArrayList<>();
        for (Capability.Status status : Capability.Status.values()) {
            long n = entries.stream().filter(c -> c.status() == status).count();
            if (n > 0) {
                parts.add(n + " " + status.value());
            }
        }
        return entries.size() + " " + what + ": " + String.join(", ", parts);
    }

    /** The checks an ADR's enforcedBy names (the part before each colon), or "not enforced yet". */
    static String enforcement(DocFile adr) {
        Set<String> checks = new LinkedHashSet<>();
        for (String entry : adr.frontmatter().strings("enforcedBy")) {
            if (entry.equals("not enforced yet")) {
                return "not enforced yet";
            }
            int colon = entry.indexOf(':');
            checks.add(colon > 0 ? entry.substring(0, colon).strip() : entry.strip());
        }
        return String.join(", ", checks);
    }

    /** The first paragraph after the README's title. */
    public static String firstParagraph(String readme) {
        List<String> lines = new ArrayList<>();
        for (String line : readme.split("\n", -1)) {
            if (line.startsWith("#")) {
                if (!lines.isEmpty()) {
                    break;
                }
                continue;
            }
            if (line.isBlank()) {
                if (!lines.isEmpty()) {
                    break;
                }
                continue;
            }
            lines.add(line.strip());
        }
        return String.join(" ", lines);
    }
}
