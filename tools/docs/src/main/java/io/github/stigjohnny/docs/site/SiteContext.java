package io.github.stigjohnny.docs.site;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.Html;
import io.github.stigjohnny.docs.generate.ModuleFacts;
import io.github.stigjohnny.docs.markdown.LinkDecision;
import io.github.stigjohnny.docs.markdown.LinkResolver;
import io.github.stigjohnny.docs.markdown.MarkdownRenderer;
import io.github.stigjohnny.docs.markdown.RenderedMarkdown;
import io.github.stigjohnny.docs.mermaid.MermaidDiagram;
import io.github.stigjohnny.docs.repo.Capability;
import io.github.stigjohnny.docs.repo.DocFile;
import io.github.stigjohnny.docs.repo.RepositoryLinks;
import io.github.stigjohnny.docs.repo.Sources;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** What every page producer needs: the sources, the site map, the diagrams, and the safe rendering of Markdown. */
final class SiteContext {

    private static final Pattern ADR_MENTION = Pattern.compile("\\bADR (\\d{4})\\b");

    private final Sources sources;
    private final SiteMap map;
    private final List<MermaidDiagram> diagrams;
    private final List<ModuleFacts> modules;

    SiteContext(Sources sources, SiteMap map, List<MermaidDiagram> diagrams) {
        this.sources = sources;
        this.map = map;
        this.diagrams = List.copyOf(diagrams);
        this.modules = ModuleFacts.all(sources);
    }

    Sources sources() {
        return sources;
    }

    SiteMap map() {
        return map;
    }

    List<MermaidDiagram> diagrams() {
        return diagrams;
    }

    List<ModuleFacts> modules() {
        return modules;
    }

    /** Renders Markdown from a repository file onto a page, with the site's link policy. */
    RenderedMarkdown render(String markdown, String sourcePath, String page, boolean dropTitle) {
        return MarkdownRenderer.render(markdown, sourcePath, links(sourcePath, page), dropTitle);
    }

    /** Renders a short Markdown fragment (a title, a condition, a summary) for use inside a table cell or a list. */
    String fragment(String markdown, String sourcePath, String page) {
        String html = render(markdown, sourcePath, page, false).html().strip();
        if (html.startsWith("<p>") && html.endsWith("</p>") && html.indexOf("<p>", 3) < 0) {
            return html.substring(3, html.length() - 4);
        }
        return html;
    }

    /**
     * The site's link policy: links to published sources become relative site links (anchors must exist), external
     * links stay, links to files the public cannot open (source code, workflows) and to the private repository become
     * plain text, and anything unsafe or broken fails the build.
     */
    LinkResolver links(String sourcePath, String page) {
        return destination -> switch (RepositoryLinks.classify(sources.repo(), sourcePath, destination)) {
            case RepositoryLinks.External ignored -> new LinkDecision.Keep();
            case RepositoryLinks.PrivateRepository ignored -> new LinkDecision.Unlink("the repository on GitHub");
            case RepositoryLinks.Unsafe unsafe -> new LinkDecision.Reject(unsafe.reason());
            case RepositoryLinks.Missing missing -> new LinkDecision.Reject(missing.path() + " does not exist");
            case RepositoryLinks.RepoFile file -> {
                Optional<String> site = map.site(file.path());
                if (site.isEmpty()) {
                    yield new LinkDecision.Unlink("");
                }
                String target = site.get();
                if (!file.fragment().isEmpty()) {
                    if (map.isDownload(target) || target.contains("#")) {
                        yield new LinkDecision.Reject("the site has no anchor #" + file.fragment() + " in "
                                + file.path());
                    }
                    if (!map.hasAnchor(target, file.fragment())) {
                        yield new LinkDecision.Reject("no heading of " + file.path() + " has the anchor #"
                                + file.fragment());
                    }
                    target = target + "#" + file.fragment();
                }
                yield new LinkDecision.Rewrite(Urls.relative(page, target));
            }
        };
    }

    /** A link from one page to another, with its text escaped. */
    static String link(String from, String target, String text) {
        return "<a href=\"" + Html.escape(Urls.relative(from, target)) + "\">" + Html.escape(text) + "</a>";
    }

    /** The pages a text mentions: repository paths of published sources, and ADR numbers. */
    List<SitePage.Related> mentioned(String text, String self, String... extraSources) {
        TreeSet<String> targets = new TreeSet<>();
        for (DocFile doc : sources.docs()) {
            if (text.contains(doc.path())) {
                map.site(doc.path()).ifPresent(targets::add);
            }
        }
        for (String path : List.of(Sources.WORKSPACE, "contracts/shipping.openapi.yaml", "security/allowlist.json",
                "policy/risk.json", "docs/capabilities/capabilities.yaml", Sources.SECURITY_README)) {
            if (text.contains(path)) {
                map.site(path).filter(p -> !map.isDownload(p)).ifPresent(targets::add);
            }
        }
        Matcher mention = ADR_MENTION.matcher(text);
        while (mention.find()) {
            String number = mention.group(1);
            sources.docsUnder("docs/adr/").stream().filter(d -> d.fileName().startsWith(number + "-")).findFirst()
                    .flatMap(d -> map.site(d.path())).ifPresent(targets::add);
        }
        for (String extra : extraSources) {
            map.site(extra).ifPresent(targets::add);
        }
        targets.remove(self);
        List<SitePage.Related> related = new ArrayList<>();
        for (String target : targets) {
            related.add(new SitePage.Related(target, titleOf(target)));
        }
        return related;
    }

    /** A page's title for related links: the document's title, or the section's name for generated pages. */
    String titleOf(String page) {
        for (DocFile doc : sources.docs()) {
            if (map.site(doc.path()).filter(page::equals).isPresent()) {
                return doc.title();
            }
        }
        for (SiteSection section : SiteSection.values()) {
            if (section.index().equals(page)) {
                return section.label();
            }
        }
        return switch (page) {
            case "start/repository.html" -> "The repository's README";
            case "trust/allowlist.html" -> "Dependency allowlist";
            default -> page;
        };
    }

    /** The capabilities and controls whose specification is the given document. */
    List<Capability> capabilitiesOf(String docPath) {
        return sources.capabilities().documentedIn(docPath);
    }

    static DocsException missing(String what) {
        return new DocsException("the site needs " + what);
    }
}
