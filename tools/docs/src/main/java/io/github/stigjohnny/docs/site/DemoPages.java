package io.github.stigjohnny.docs.site;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.Html;
import io.github.stigjohnny.docs.generate.ArchitecturePages;
import io.github.stigjohnny.docs.markdown.LinkDecision;
import io.github.stigjohnny.docs.markdown.LinkResolver;
import io.github.stigjohnny.docs.markdown.MarkdownBlock;
import io.github.stigjohnny.docs.repo.DemoRecord;
import io.github.stigjohnny.docs.repo.DocFile;
import io.github.stigjohnny.docs.repo.RepositoryLinks;
import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.repo.TestEvidence;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Demos section, second in the register (docs/ui/documentation-site.md): the register of demonstrations (X-00),
 * newest recording first; the recipe for writing one (X-GUIDE); and one sheet per pull request (X- and its number).
 * A sheet keeps the ledger's meanings: the record's status, no capability of its own, and as verified evidence only the
 * named tests that passed in the build that made the site, so recorded output never gets the double rule. Its links
 * are checked against the pages as built: See it opens pages of this site, a visual change's evidence links the page it
 * inspected, and a link to the site's own address becomes a relative link to the page and anchor it names.
 */
final class DemoPages {

    static final String INDEX = "demos/index.html";
    static final String HOW_TO = "demos/how-to.html";
    private static final String SEE_IT = "See it";
    private static final Pattern ID = Pattern.compile(" id=\"([^\"]+)\"");

    /** Where a link of a demonstration leads: a page of this site and an anchor on it, or why it leads to none. */
    private record Opened(String page, String fragment, String reason) {

        static Opened at(String page, String fragment) {
            return new Opened(page, fragment, "");
        }

        static Opened none(String reason) {
            return new Opened("", "", reason);
        }

        String target() {
            return fragment.isEmpty() ? page : page + "#" + fragment;
        }
    }

    private DemoPages() {
    }

    /**
     * The section's pages: the register, the how-to, then the sheets, newest recording first. They are built after
     * every other page, so that their links are checked against the site as built, ids of generated pages included.
     */
    static List<SitePage> of(SiteContext context, List<SitePage> built) {
        Sources sources = context.sources();
        DocFile howTo = sources.doc(DemoRecord.HOW_TO).orElseThrow(() -> SiteContext.missing(DemoRecord.HOW_TO));
        List<DemoRecord> demos = DemoRecord.all(sources);
        Set<String> pages = new TreeSet<>(List.of(INDEX, HOW_TO));
        for (SitePage page : built) {
            pages.add(page.path());
            Matcher id = ID.matcher(page.body());
            while (id.find()) {
                context.map().anchor(page.path(), id.group(1));
            }
        }
        demos.forEach(demo -> pages.add(page(context, demo)));
        Set<String> problems = new TreeSet<>();
        for (DemoRecord demo : demos) {
            checkLinks(context, demo, pages, problems);
        }
        if (!problems.isEmpty()) {
            throw new DocsException("the demonstrations do not link pages of this site (" + problems.size()
                    + (problems.size() == 1 ? " problem" : " problems") + "):\n  - " + String.join("\n  - ", problems));
        }
        List<SitePage> section = new ArrayList<>();
        section.add(index(context, howTo, demos));
        section.add(DocPages.of(context, howTo, SiteSection.DEMOS, howTo.title(), new StatusPanel(
                Statuses.design(howTo), Badge.NEUTRAL.html("Not applicable") + " A guide for authors: it specifies no "
                        + "capability. The docs tool applies the checks it lists.",
                Badge.NEUTRAL.html("Not applicable") + " A guide makes no claim to verify; each demonstration states "
                        + "its own evidence."), links(context, howTo.path(), HOW_TO, pages)));
        for (DemoRecord demo : demos) {
            section.add(sheet(context, demo, pages));
        }
        return section;
    }

    /** The register: each outcome with why it matters, its pull request as plain text, its kind and its recording. */
    private static SitePage index(SiteContext context, DocFile howTo, List<DemoRecord> demos) {
        StringBuilder body = new StringBuilder("<p class=\"prose\">Each demonstration shows a reader who does not read "
                + "code what one pull request changed: what can now be seen or run, the evidence, and the limits. Its "
                + "output was recorded when the change was made; this site shows it as recorded and does not rerun "
                + "it. ")
                .append(SiteContext.link(INDEX, HOW_TO, howTo.title()))
                .append(" says when one is needed and how to write it.</p>\n");
        if (demos.isEmpty()) {
            body.append("<p class=\"prose\">There are no demonstrations yet.</p>\n");
        } else {
            HtmlTable table = HtmlTable.of("Demonstrations (" + demos.size() + "), newest recording first",
                    "Demonstrations, scrolls sideways", "Outcome", "Pull request", "Kind", "Recorded")
                    .wide(HtmlTable.Width.DEMOS);
            for (DemoRecord demo : demos) {
                table.row(SiteContext.link(INDEX, page(context, demo), demo.doc().title())
                                + "<span class=\"register-why\">" + Html.escape(demo.doc().description()) + "</span>",
                        Html.escape("#" + demo.pr()), Html.escape(demo.kind().words()), time(demo.recorded()));
            }
            body.append(table.html());
        }
        return new SitePage(INDEX, "Demos", "Overview", SiteSection.DEMOS, "What each pull request changed, for a "
                + "reader who does not read code, with its evidence and its limits.", new StatusPanel(
                        Statuses.design(howTo.status(), "A pull request that needs the owner's approval or changes "
                                + "what a reader sees supplies one; the how-to says how."),
                        Badge.NEUTRAL.html("Not applicable") + " Demonstrations record changes; they specify no "
                                + "capability of their own.",
                        Badge.NEUTRAL.html("See each record") + " Each counts only its named tests that passed in this "
                                + "build; recorded output is never rerun."),
                List.of(), body.toString(), List.of(), DemoRecord.DIRECTORY, false);
    }

    /** A demonstration's sheet: its facts beside the description, its ledger, then its sections as written. */
    private static SitePage sheet(SiteContext context, DemoRecord demo, Set<String> pages) {
        DocFile doc = demo.doc();
        String page = page(context, demo);
        StatusPanel status = new StatusPanel(Statuses.design(doc), Badge.NEUTRAL.html("Not applicable") + " "
                + Html.escape("A demonstration specifies no capability or control; it records what pull request #"
                        + demo.pr() + " changed."), evidence(demo, context.sources().evidence()));
        SitePage rendered = DocPages.of(context, doc, SiteSection.DEMOS, doc.title(), status,
                links(context, doc.path(), page, pages));
        return new SitePage(rendered.path(), rendered.title(), rendered.navLabel(), rendered.section(),
                rendered.summary(), rendered.status(), rendered.contents(), labelled(rendered.body()),
                rendered.related(), rendered.source(), false, null, facts(demo));
    }

    /**
     * The pull request, the kind and the recording, beside the description, and what the title block's revision is: the
     * commit the site was built from, which says nothing about when the demonstration was recorded.
     */
    private static String facts(DemoRecord demo) {
        return "<p class=\"page-facts\">" + Html.escape("Pull request #" + demo.pr() + " · Kind: "
                + demo.kind().words().toLowerCase(Locale.ROOT) + " · Recorded ") + time(demo.recorded())
                + "</p>\n<p class=\"page-facts\">The revision below is the commit this site was built from, not the "
                + "one the demonstration was recorded at.</p>\n";
    }

    /**
     * The verified evidence of a sheet: the named tests (its {@code tests}) that passed in the build that made the
     * site, as a count with the double rule, and what each named test did in this build; a missing, ambiguous, failed
     * or skipped test is never counted, and recorded output never is evidence of the build.
     */
    static String evidence(DemoRecord demo, TestEvidence evidence) {
        List<String> tests = demo.tests();
        if (tests.isEmpty()) {
            return Badge.NONE.html("None in this build") + " This record names no test class, so no named-test "
                    + "evidence is available in this build. The documentation checks still validate its form and "
                    + "links. Its recorded evidence was not rerun.";
        }
        List<String> passing = tests.stream().filter(evidence::passed).toList();
        String states = "\n<details class=\"evidence-tests\"><summary>Named test classes (" + tests.size()
                + ")</summary><p>" + Html.escape(String.join("; ", tests.stream()
                        .map(test -> test + ": " + state(test, evidence)).toList())) + "</p></details>";
        if (passing.isEmpty()) {
            return Badge.NONE.html("None in this build") + " No test class named by this record passed in the build "
                    + "that generated this site, so none is evidence here. The recorded output below was not rerun by "
                    + "this build." + states;
        }
        int others = tests.size() - passing.size();
        String notEvidence = others == 0 ? "" : others == 1
                ? " 1 other named test class did not pass in this build and is not evidence."
                : " " + others + " other named test classes did not pass in this build and are not evidence.";
        return Badge.SHIPPED.html("Tests passing") + " " + Html.escape((passing.size() == 1 ? "1 test class"
                : passing.size() + " test classes") + " named by this record passed in the build that generated this "
                + "site. Named tests are separate from the recorded output, which this build did not rerun."
                + notEvidence) + states;
    }

    /** What a named test did in this build, in words. */
    private static String state(String test, TestEvidence evidence) {
        if (!evidence.exists(test)) {
            return "not in the repository";
        }
        if (evidence.ambiguous(test)) {
            return "ambiguous, more than one test class has this name";
        }
        Optional<TestEvidence.Result> result = evidence.result(test);
        if (result.isEmpty()) {
            return "no report in this build";
        }
        TestEvidence.Result counts = result.get();
        if (counts.passed()) {
            return "passed";
        }
        if (counts.failures() > 0 || counts.errors() > 0) {
            return "failed in this build";
        }
        return counts.skipped() > 0 ? "skipped in this build" : "ran no test in this build";
    }

    /** Commands and recorded output, labelled in words above their blocks. */
    private static String labelled(String html) {
        return html.replace("<pre><code class=\"language-sh\">", "<p class=\"fence-label\">Command</p>\n"
                + "<pre><code class=\"language-sh\">").replace("<pre><code class=\"language-text\">",
                        "<p class=\"fence-label\">Recorded output</p>\n<pre><code class=\"language-text\">");
    }

    /**
     * See it opens at least one page of this site, and every link in it opens one, so that nothing a reader is sent to
     * inspect is plain text, a download or another site; a visual change's evidence links the page it inspected.
     */
    private static void checkLinks(SiteContext context, DemoRecord demo, Set<String> pages, Set<String> problems) {
        String path = demo.doc().path();
        String self = page(context, demo);
        demo.part(SEE_IT).ifPresent(seeIt -> {
            boolean failed = false;
            boolean opened = false;
            for (String destination : destinations(seeIt)) {
                Opened link = opens(context, path, self, destination, pages);
                if (!link.reason().isEmpty()) {
                    failed = true;
                    problems.add(path + ": See it: '" + destination + "' " + link.reason() + "; See it links pages of "
                            + "this site");
                } else if (!link.page().equals(self)) {
                    opened = true;
                }
            }
            if (!failed && !opened) {
                problems.add(path + ": See it links no page of this site");
            }
        });
        if (demo.kind() == DemoRecord.Kind.VISUAL) {
            boolean inspected = demo.part("Evidence").stream().flatMap(evidence -> destinations(evidence).stream())
                    .map(destination -> opens(context, path, self, destination, pages))
                    .anyMatch(link -> link.reason().isEmpty() && !link.page().equals(self));
            if (!inspected) {
                problems.add(path + ": the evidence of a visual change links no page of this site that it inspected");
            }
        }
    }

    private static List<String> destinations(DemoRecord.Part part) {
        return part.blocks().stream().flatMap((MarkdownBlock block) -> block.links().stream()).toList();
    }

    /** Where a link of a demonstration's source leads on this site. */
    private static Opened opens(SiteContext context, String source, String self, String destination,
            Set<String> pages) {
        if (destination.startsWith("#")) {
            return Opened.at(self, destination.substring(1));
        }
        if (destination.startsWith(ArchitecturePages.SITE)) {
            String target = destination.substring(ArchitecturePages.SITE.length());
            int hash = target.indexOf('#');
            String page = hash < 0 ? target : target.substring(0, hash);
            String fragment = hash < 0 ? "" : target.substring(hash + 1);
            page = page.isEmpty() ? StartPages.INDEX : page;
            if (page.contains("?") || !pages.contains(page)) {
                return Opened.none("names no page of this site");
            }
            if (!fragment.isEmpty() && !context.map().hasAnchor(page, fragment)) {
                return Opened.none("names no anchor of " + page);
            }
            return Opened.at(page, fragment);
        }
        return switch (RepositoryLinks.classify(context.sources().repo(), source, destination)) {
            case RepositoryLinks.RepoFile file -> {
                Optional<String> site = context.map().site(file.path());
                if (site.isEmpty()) {
                    yield Opened.none("is a file the site does not publish, so it would show as plain text");
                }
                if (context.map().isDownload(site.get())) {
                    yield Opened.none("is a download, not a page");
                }
                String page = site.get().contains("#") ? site.get().substring(0, site.get().indexOf('#'))
                        : site.get();
                yield Opened.at(page, file.fragment());
            }
            case RepositoryLinks.External ignored -> Opened.none("is another site");
            case RepositoryLinks.PrivateRepository ignored -> Opened.none("is the repository on GitHub, which the site "
                    + "never links");
            case RepositoryLinks.Missing missing -> Opened.none("leads to " + missing.path() + ", which does not "
                    + "exist");
            case RepositoryLinks.Unsafe unsafe -> Opened.none("is unsafe: " + unsafe.reason());
        };
    }

    /**
     * A demonstration's link policy: the site's own, and a link to the site's address, which becomes a relative link to
     * the page and anchor it names and fails the build when this site has no such page or anchor.
     */
    private static LinkResolver links(SiteContext context, String source, String page, Set<String> pages) {
        LinkResolver site = context.links(source, page);
        return destination -> {
            if (!destination.startsWith(ArchitecturePages.SITE)) {
                return site.resolve(destination);
            }
            Opened opened = opens(context, source, page, destination, pages);
            return opened.reason().isEmpty() ? new LinkDecision.Rewrite(Urls.relative(page, opened.target()))
                    : new LinkDecision.Reject(opened.reason());
        };
    }

    private static String page(SiteContext context, DemoRecord demo) {
        return context.map().site(demo.doc().path())
                .orElseThrow(() -> SiteContext.missing("a page for " + demo.doc().path()));
    }

    private static String time(String date) {
        return "<time datetime=\"" + Html.escape(date) + "\">" + Html.escape(date) + "</time>";
    }
}
