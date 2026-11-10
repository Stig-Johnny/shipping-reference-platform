package io.github.stigjohnny.docs.check;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.markdown.LinkDecision;
import io.github.stigjohnny.docs.markdown.MarkdownRenderer;
import io.github.stigjohnny.docs.markdown.Section;
import io.github.stigjohnny.docs.model.C4Model;
import io.github.stigjohnny.docs.model.ModelElement;
import io.github.stigjohnny.docs.repo.Capability;
import io.github.stigjohnny.docs.repo.DocFile;
import io.github.stigjohnny.docs.repo.ModelRuns;
import io.github.stigjohnny.docs.repo.RepositoryLinks;
import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.repo.TestEvidence;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Consistency of the documentation sources, checked before anything is generated (ADR 0001 and ADR 0011):
 *
 * <ul>
 *   <li>every document under {@code docs/} has the required frontmatter: title, description, a known status and an
 *       ISO {@code updated} date;
 *   <li>module documents name a module of the model, their {@code moduleStatus} equals its {@code contract.status},
 *       and every module has one; flows name only modules of the model;
 *   <li>ADRs are numbered from 0001 without gaps, carry the MADR frontmatter and the required sections, and every test
 *       class an {@code enforcedBy} entry names exists; every "ADR NNNN" mentioned in a document exists;
 *   <li>a capability or control claims {@code shipped} or {@code partial} only with evidence, every evidence test
 *       exists and passed in this build, {@code planned} names no evidence, and a non-planned control's check is a
 *       workflow job; a capability's actors are people or external systems that the model relates to its module;
 *   <li>every link resolves: relative links to existing files and their anchors, no private or unsafe destination;
 *   <li>every demonstration under {@code docs/demos/} has its metadata, its sections, approved commands only and text
 *       only ({@link DemoCheck}), and every milestone deck there its slides, its notes apart, counts that the sources
 *       give and the record of its recorded output ({@link DeckCheck}).
 * </ul>
 */
public final class Consistency {

    static final Set<String> DOC_STATUSES = Set.of("draft", "proposed", "accepted", "rejected", "deprecated",
            "superseded");
    static final Set<String> ADR_STATUSES = Set.of("proposed", "accepted", "rejected", "deprecated", "superseded");
    static final List<String> ADR_SECTIONS = List.of("Context and Problem Statement", "Considered Options",
            "Decision Outcome", "Consequences", "Confirmation");
    private static final Pattern ADR_FILE = Pattern.compile("docs/adr/(\\d{4})-[a-z0-9]+(?:-[a-z0-9]+)*\\.md");
    private static final Pattern ADR_MENTION = Pattern.compile("\\bADR (\\d{4})\\b");
    private static final Pattern TEST_NAME = Pattern.compile("\\b[A-Z][A-Za-z0-9]*Test\\b");
    private static final String WEB = "web";

    private Consistency() {
    }

    public static void check(Sources sources) {
        Problems problems = new Problems();
        for (DocFile doc : sources.docs()) {
            frontmatter(doc, problems);
        }
        modules(sources, problems);
        flows(sources, problems);
        adrs(sources, problems);
        mentions(sources, problems);
        capabilities(sources, problems);
        links(sources, problems);
        DemoCheck.check(sources, problems);
        try {
            ModelRuns.read(sources);
        } catch (DocsException e) {
            problems.add(e.getMessage());
        }
        problems.throwIfAny("the documentation sources are inconsistent");
    }

    private static void frontmatter(DocFile doc, Problems problems) {
        try {
            if (doc.title().isBlank() || doc.description().isBlank()) {
                problems.add(doc.path() + ": title and description must not be empty");
            }
            if (!DOC_STATUSES.contains(doc.status())) {
                problems.add(doc.path() + ": status '" + doc.status() + "' is not one of " + DOC_STATUSES);
            }
            date(doc, "updated", problems);
        } catch (DocsException e) {
            problems.add(e.getMessage());
        }
    }

    private static void modules(Sources sources, Problems problems) {
        C4Model model = sources.workspace().model();
        Map<String, ModelElement> modules = new TreeMap<>();
        model.modules().forEach(m -> modules.put(m.contractId().orElseThrow(), m));
        Set<String> documented = new HashSet<>();
        for (DocFile doc : sources.docsUnder("docs/modules/")) {
            try {
                String module = doc.text("module");
                documented.add(module);
                ModelElement element = modules.get(module);
                if (!doc.fileName().equals(module + ".md")) {
                    problems.add(doc.path() + ": module '" + module + "' must be documented in " + module + ".md");
                }
                if (element == null) {
                    problems.add(doc.path() + ": module '" + module + "' is not a component of "
                            + Sources.WORKSPACE);
                    continue;
                }
                String status = element.contractStatus().orElse("");
                if (!doc.text("moduleStatus").equals(status)) {
                    problems.add(doc.path() + ": moduleStatus '" + doc.text("moduleStatus")
                            + "' is stale; the model's contract.status is '" + status + "'");
                }
                if (!Set.of("0", "1", "2", "3").contains(doc.text("riskLevel"))) {
                    problems.add(doc.path() + ": riskLevel must be 0, 1, 2 or 3");
                }
            } catch (DocsException e) {
                problems.add(e.getMessage());
            }
        }
        for (String module : modules.keySet()) {
            if (!documented.contains(module)) {
                problems.add("module '" + module + "' of " + Sources.WORKSPACE + " has no docs/modules/" + module
                        + ".md");
            }
        }
    }

    private static void flows(Sources sources, Problems problems) {
        Set<String> modules = moduleIds(sources);
        for (DocFile doc : sources.docsUnder("docs/flows/")) {
            try {
                for (String module : doc.frontmatter().strings("modules")) {
                    if (!modules.contains(module)) {
                        problems.add(doc.path() + ": modules names '" + module + "', which the model does not have");
                    }
                }
            } catch (DocsException e) {
                problems.add(doc.path() + ": " + e.getMessage());
            }
        }
    }

    private static void adrs(Sources sources, Problems problems) {
        Set<String> numbers = new HashSet<>();
        for (DocFile adr : sources.docsUnder("docs/adr/")) {
            Matcher file = ADR_FILE.matcher(adr.path());
            if (file.matches()) {
                numbers.add(file.group(1));
            }
        }
        int expected = 1;
        for (DocFile adr : sources.docsUnder("docs/adr/")) {
            Matcher file = ADR_FILE.matcher(adr.path());
            if (!file.matches()) {
                problems.add(adr.path() + ": an ADR file is named docs/adr/NNNN-kebab-case-title.md");
                continue;
            }
            String number = file.group(1);
            if (Integer.parseInt(number) != expected) {
                problems.add(adr.path() + ": ADR numbers run from 0001 without gaps; expected %04d".formatted(expected));
            }
            expected = Integer.parseInt(number) + 1;
            try {
                if (!adr.title().startsWith("ADR " + number + ": ")) {
                    problems.add(adr.path() + ": the title starts with 'ADR " + number + ": '");
                }
                if (!ADR_STATUSES.contains(adr.status())) {
                    problems.add(adr.path() + ": status '" + adr.status() + "' is not one of " + ADR_STATUSES);
                }
                Optional<String> successor = adr.optional("supersededBy");
                if ("superseded".equals(adr.status()) && successor.isEmpty()) {
                    problems.add(adr.path() + ": a superseded ADR names its successor in supersededBy");
                }
                if (successor.isPresent() && !"superseded".equals(adr.status())) {
                    problems.add(adr.path() + ": only a superseded ADR has supersededBy");
                }
                if (successor.isPresent() && (!numbers.contains(successor.get()) || successor.get().equals(number))) {
                    problems.add(adr.path() + ": supersededBy names ADR " + successor.get() + ", which does not exist");
                }
                date(adr, "date", problems);
                if (adr.text("decision-makers").isBlank()) {
                    problems.add(adr.path() + ": decision-makers must not be empty");
                }
                List<String> enforcedBy = adr.frontmatter().strings("enforcedBy");
                if (enforcedBy.isEmpty()) {
                    problems.add(adr.path() + ": enforcedBy lists the enforcement or says [not enforced yet]");
                }
                for (String entry : enforcedBy) {
                    Matcher test = TEST_NAME.matcher(entry);
                    while (test.find()) {
                        if (!sources.evidence().exists(test.group())) {
                            problems.add(adr.path() + ": enforcedBy names " + test.group()
                                    + ", which does not exist in the repository");
                        } else if (sources.evidence().ambiguous(test.group())) {
                            problems.add(adr.path() + ": enforcedBy names " + test.group() + ", which "
                                    + ambiguity(test.group()));
                        }
                    }
                }
            } catch (DocsException e) {
                problems.add(e.getMessage());
            }
            List<String> headings = MarkdownRenderer.headings(adr.body(), adr.path()).stream().map(Section::text)
                    .toList();
            for (String section : ADR_SECTIONS) {
                if (!headings.contains(section)) {
                    problems.add(adr.path() + ": the required section '" + section + "' is missing");
                }
            }
        }
    }

    private static void mentions(Sources sources, Problems problems) {
        Set<String> numbers = new HashSet<>();
        for (DocFile adr : sources.docsUnder("docs/adr/")) {
            Matcher file = ADR_FILE.matcher(adr.path());
            if (file.matches()) {
                numbers.add(file.group(1));
            }
        }
        Map<String, String> texts = new TreeMap<>();
        sources.docs().forEach(doc -> texts.put(doc.path(), doc.body()));
        texts.put(Sources.README, sources.readme());
        texts.forEach((path, text) -> {
            Matcher mention = ADR_MENTION.matcher(text);
            while (mention.find()) {
                if (!numbers.contains(mention.group(1))) {
                    problems.add(path + ": mentions ADR " + mention.group(1) + ", which does not exist");
                }
            }
        });
    }

    private static void capabilities(Sources sources, Problems problems) {
        Set<String> modules = moduleIds(sources);
        modules.add(WEB);
        Set<String> ids = new HashSet<>();
        TestEvidence evidence = sources.evidence();
        for (Capability capability : sources.capabilities().all()) {
            String where = "docs/capabilities/capabilities.yaml " + capability.id();
            if (!ids.add(capability.id())) {
                problems.add(where + ": the id is used twice");
            }
            if (!capability.control() && !modules.contains(capability.module())) {
                problems.add(where + ": module '" + capability.module() + "' is not a module of the model or web");
            }
            actors(capability, sources.workspace().model(), where, problems);
            if (!Files.isRegularFile(sources.repo().resolve(capability.docs()))) {
                problems.add(where + ": docs '" + capability.docs() + "' does not exist");
            }
            switch (capability.status()) {
                case PLANNED -> {
                    if (!capability.evidence().isEmpty()) {
                        problems.add(where + ": a planned capability names no evidence ('none yet')");
                    }
                    if (!capability.evidenceLabel().isEmpty()) {
                        problems.add(where + ": an evidence label names passing evidence, and a planned capability "
                                + "has none");
                    }
                }
                case SHIPPED, PARTIAL -> {
                    if (capability.evidence().isEmpty()) {
                        problems.add(where + ": claims " + capability.status().value() + " without evidence");
                    }
                    for (String test : capability.evidence()) {
                        evidenceProblem(test, evidence).ifPresent(p -> problems.add(where + ": evidence " + test
                                + " " + p));
                        if (!capability.acceptanceTests().contains(test)) {
                            problems.add(where + ": evidence " + test + " is not one of its acceptance tests");
                        }
                    }
                    if (capability.status() == Capability.Status.SHIPPED && (!capability.enforcedSubset().isEmpty()
                            || !capability.plannedRemainder().isEmpty())) {
                        problems.add(where + ": a shipped control has no planned remainder or enforced subset");
                    }
                    if (capability.status() == Capability.Status.SHIPPED) {
                        for (String test : capability.acceptanceTests()) {
                            if (!capability.evidence().contains(test)) {
                                problems.add(where + ": claims shipped, but acceptance test " + test
                                        + " is not in its evidence");
                            }
                        }
                    }
                    if (capability.control() && !sources.jobs().contains(capability.check())) {
                        problems.add(where + ": check '" + capability.check() + "' is not a job of any workflow");
                    }
                }
            }
        }
    }

    /**
     * A capability's actors: each is a person or external system of the model, named by its identifier there, that the
     * model relates to the capability's module, directly or through an element it uses.
     */
    private static void actors(Capability capability, C4Model model, String where, Problems problems) {
        Optional<ModelElement> part = model.part(capability.module());
        for (String identifier : capability.actors()) {
            Optional<ModelElement> actor = model.actor(identifier);
            if (actor.isEmpty()) {
                List<String> known = model.actors().stream().map(a -> a.identifier().orElse(a.name())).toList();
                problems.add(where + ": actor '" + identifier + "' is not a person or external system of "
                        + Sources.WORKSPACE + "; those are " + (known.size() < 2 ? String.join("", known)
                                : String.join(", ", known.subList(0, known.size() - 1)) + " and " + known.getLast()));
            } else if (part.isPresent() && model.participation(actor.get(), part.get()).isEmpty()) {
                problems.add(where + ": actor '" + identifier + "' (" + actor.get().name() + ") has no relationship in "
                        + "the model with " + part.get().name() + ", directly or through an element it uses");
            }
        }
    }

    private static String ambiguity(String test) {
        return "is ambiguous: more than one test class is called " + test + "; evidence names must be unique";
    }

    private static Optional<String> evidenceProblem(String test, TestEvidence evidence) {
        if (!evidence.exists(test)) {
            return Optional.of("does not exist in the repository");
        }
        if (evidence.ambiguous(test)) {
            return Optional.of(ambiguity(test));
        }
        Optional<TestEvidence.Result> result = evidence.result(test);
        if (result.isEmpty()) {
            return Optional.of("has no test report in this build");
        }
        TestEvidence.Result counts = result.get();
        if (counts.passed()) {
            return Optional.empty();
        }
        return Optional.of("did not pass in this build (%d tests, %d failed, %d errors, %d skipped)"
                .formatted(counts.tests(), counts.failures(), counts.errors(), counts.skipped()));
    }

    private static void links(Sources sources, Problems problems) {
        Map<String, String> texts = new TreeMap<>();
        sources.docs().forEach(doc -> texts.put(doc.path(), doc.body()));
        texts.put(Sources.README, sources.readme());
        texts.put(Sources.SECURITY_README, sources.securityReadme().body());
        texts.forEach((path, text) -> {
            try {
                MarkdownRenderer.render(text, path, destination -> link(sources, path, destination), false);
            } catch (DocsException e) {
                problems.add(e.getMessage());
            }
        });
    }

    private static LinkDecision link(Sources sources, String source, String destination) {
        return switch (RepositoryLinks.classify(sources.repo(), source, destination)) {
            case RepositoryLinks.External ignored -> new LinkDecision.Keep();
            case RepositoryLinks.PrivateRepository ignored -> new LinkDecision.Keep();
            case RepositoryLinks.Unsafe unsafe -> new LinkDecision.Reject(unsafe.reason());
            case RepositoryLinks.Missing missing -> new LinkDecision.Reject(missing.path() + " does not exist");
            case RepositoryLinks.RepoFile file -> {
                if (!file.fragment().isEmpty() && file.path().endsWith(".md")) {
                    String text = Sources.text(sources.repo(), file.path());
                    String body = text.startsWith("---\n") ? DocFile.parse(file.path(), text).body() : text;
                    boolean found = MarkdownRenderer.headings(body, file.path()).stream()
                            .anyMatch(h -> h.id().equals(file.fragment()));
                    if (!found) {
                        yield new LinkDecision.Reject("no heading of " + file.path() + " has the anchor #"
                                + file.fragment());
                    }
                }
                yield new LinkDecision.Keep();
            }
        };
    }

    private static Set<String> moduleIds(Sources sources) {
        Set<String> ids = new HashSet<>();
        sources.workspace().model().modules().forEach(m -> ids.add(m.contractId().orElseThrow()));
        return ids;
    }

    private static void date(DocFile doc, String key, Problems problems) {
        try {
            LocalDate.parse(doc.text(key));
        } catch (DateTimeParseException e) {
            problems.add(doc.path() + ": " + key + " '" + doc.text(key) + "' is not an ISO date (YYYY-MM-DD)");
        }
    }
}
