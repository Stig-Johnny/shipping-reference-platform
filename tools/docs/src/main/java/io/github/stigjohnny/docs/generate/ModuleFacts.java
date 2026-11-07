package io.github.stigjohnny.docs.generate;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.model.C4Model;
import io.github.stigjohnny.docs.model.ModelElement;
import io.github.stigjohnny.docs.model.ModelRelationship;
import io.github.stigjohnny.docs.repo.ApiContract;
import io.github.stigjohnny.docs.repo.Capability;
import io.github.stigjohnny.docs.repo.DocFile;
import io.github.stigjohnny.docs.repo.Sources;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the documentation knows about one module of the model, gathered from every source: the brains and the site's
 * module pages are written from the same facts.
 *
 * @param status the component's {@code contract.status} ({@code built} or {@code planned})
 * @param uses checked dependencies on other modules, in model order
 * @param usedBy modules that depend on this one
 * @param other relationships with people, containers and systems, which the Java check does not evaluate
 * @param decisions the ADRs the module's specification mentions
 * @param flows the flow documents whose frontmatter names the module
 * @param gaps entries of docs/gaps.md that name the module
 */
public record ModuleFacts(String id, String name, String description, String status, List<String> packages,
        String riskLevel, DocFile spec, List<Dependency> uses, List<Dependency> usedBy, List<Dependency> other,
        List<ApiContract.Operation> operations, List<Capability> capabilities, List<Decision> decisions,
        List<DocFile> flows, List<Gap> gaps) {

    private static final Pattern ADR_MENTION = Pattern.compile("\\bADR (\\d{4})\\b");
    private static final Pattern GAP_HEADING = Pattern.compile("^## (.+)$");

    public ModuleFacts {
        packages = List.copyOf(packages);
        uses = List.copyOf(uses);
        usedBy = List.copyOf(usedBy);
        other = List.copyOf(other);
        operations = List.copyOf(operations);
        capabilities = List.copyOf(capabilities);
        decisions = List.copyOf(decisions);
        flows = List.copyOf(flows);
        gaps = List.copyOf(gaps);
    }

    /**
     * A relationship as a module sees it.
     *
     * @param other the other module's id, or the other element's name
     * @param mode {@code REQUIRED}, {@code PERMITTED} or empty for relationships the Java check does not evaluate
     * @param outgoing whether this module is the source
     */
    public record Dependency(String other, String mode, boolean planned, boolean outgoing, String description,
            String technology) {

        /** The mode and plan in words, for example "REQUIRED" or "PERMITTED, planned". */
        public String words() {
            String words = mode.isEmpty() ? "not checked" : mode;
            return planned ? words + ", planned" : words;
        }
    }

    /** An ADR the specification mentions. */
    public record Decision(String number, String title, String status, String path) {
    }

    /** One entry of docs/gaps.md. */
    public record Gap(String section, String text) {
    }

    /** The facts of every module of the model, in model order. */
    public static List<ModuleFacts> all(Sources sources) {
        List<ModuleFacts> all = new ArrayList<>();
        for (ModelElement module : sources.workspace().model().modules()) {
            all.add(of(sources, module));
        }
        return List.copyOf(all);
    }

    static ModuleFacts of(Sources sources, ModelElement module) {
        C4Model model = sources.workspace().model();
        String id = module.contractId().orElseThrow();
        String specPath = "docs/modules/" + id + ".md";
        DocFile spec = sources.doc(specPath)
                .orElseThrow(() -> new DocsException("module " + id + " has no " + specPath));
        List<Dependency> uses = new ArrayList<>();
        List<Dependency> usedBy = new ArrayList<>();
        List<Dependency> other = new ArrayList<>();
        for (ModelRelationship relationship : model.relationships()) {
            boolean outgoing = relationship.sourceId().equals(module.id());
            boolean incoming = relationship.destinationId().equals(module.id());
            if (!outgoing && !incoming) {
                continue;
            }
            ModelElement counterpart = model.element(outgoing ? relationship.destinationId() : relationship.sourceId());
            String name = counterpart.contractId().orElse(counterpart.name());
            Dependency dependency = new Dependency(name, relationship.mode().orElse(""), relationship.planned(),
                    outgoing, relationship.description(), relationship.technology());
            if (counterpart.contractId().isEmpty()) {
                other.add(dependency);
            } else if (outgoing) {
                uses.add(dependency);
            } else {
                usedBy.add(dependency);
            }
        }
        return new ModuleFacts(id, module.name(), module.description(), module.contractStatus().orElse(""),
                module.contractPackages(), spec.optional("riskLevel").orElse(""), spec, uses, usedBy, other,
                sources.contract().taggedWith(id), sources.capabilities().ofModule(id), decisions(sources, spec),
                flows(sources, id), gaps(sources, id));
    }

    private static List<Decision> decisions(Sources sources, DocFile spec) {
        TreeSet<String> numbers = new TreeSet<>();
        Matcher mention = ADR_MENTION.matcher(spec.body());
        while (mention.find()) {
            numbers.add(mention.group(1));
        }
        List<Decision> decisions = new ArrayList<>();
        for (String number : numbers) {
            Optional<DocFile> adr = sources.docsUnder("docs/adr/").stream()
                    .filter(d -> d.fileName().startsWith(number + "-")).findFirst();
            adr.ifPresent(d -> decisions.add(new Decision(number, d.title(), d.status(), d.path())));
        }
        return decisions;
    }

    private static List<DocFile> flows(Sources sources, String id) {
        return sources.docsUnder("docs/flows/").stream().filter(f -> f.frontmatter().has("modules")
                && f.frontmatter().strings("modules").contains(id)).toList();
    }

    /** Bullets of docs/gaps.md that name the module: in code, by its document, or as "the X module". */
    static List<Gap> gaps(Sources sources, String id) {
        Optional<DocFile> gaps = sources.doc("docs/gaps.md");
        if (gaps.isEmpty()) {
            return List.of();
        }
        List<Gap> found = new ArrayList<>();
        String section = "";
        StringBuilder bullet = new StringBuilder();
        for (String line : (gaps.get().body() + "\n").split("\n", -1)) {
            Matcher heading = GAP_HEADING.matcher(line);
            boolean continuation = line.startsWith("  ") && !bullet.isEmpty();
            if (!continuation && !bullet.isEmpty()) {
                if (names(bullet.toString(), id)) {
                    found.add(new Gap(section, bullet.toString().strip()));
                }
                bullet.setLength(0);
            }
            if (heading.matches()) {
                section = heading.group(1).strip();
            } else if (line.startsWith("- ")) {
                bullet.append(line.substring(2));
            } else if (continuation) {
                bullet.append(' ').append(line.strip());
            }
        }
        return found;
    }

    private static boolean names(String text, String id) {
        String lower = text.toLowerCase(Locale.ROOT);
        return text.contains("`" + id + "`") || text.contains("docs/modules/" + id + ".md")
                || lower.contains(" " + id + " module") || lower.contains("module " + id);
    }
}
