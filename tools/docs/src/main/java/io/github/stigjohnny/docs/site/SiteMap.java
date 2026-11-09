package io.github.stigjohnny.docs.site;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.generate.ArchitecturePages;
import io.github.stigjohnny.docs.generate.Brains;
import io.github.stigjohnny.docs.markdown.MarkdownRenderer;
import io.github.stigjohnny.docs.markdown.Section;
import io.github.stigjohnny.docs.markdown.SlideOutline;
import io.github.stigjohnny.docs.model.C4View;
import io.github.stigjohnny.docs.model.ViewNames;
import io.github.stigjohnny.docs.repo.Allowlist;
import io.github.stigjohnny.docs.repo.ApiContract;
import io.github.stigjohnny.docs.repo.Capabilities;
import io.github.stigjohnny.docs.repo.DeckRecord;
import io.github.stigjohnny.docs.repo.DemoRecord;
import io.github.stigjohnny.docs.repo.DocFile;
import io.github.stigjohnny.docs.repo.RiskPolicy;
import io.github.stigjohnny.docs.repo.Sources;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Where each published source appears on the site, and which anchors each page has. Every hand-written document under
 * {@code docs/} must have a page; a document without one fails the build instead of silently staying unpublished. A
 * milestone deck has two pages, its slides and its notes, and each has its slides' anchors and no other: the notes
 * headings have IDs on GitHub, but not on the site.
 */
final class SiteMap {

    static final String CONTRACT_DOWNLOAD = "downloads/shipping.openapi.yaml";
    static final String BRAIN_DOWNLOAD = "downloads/BRAIN.md";

    private final Map<String, String> pages = new LinkedHashMap<>();
    private final Map<String, Set<String>> anchors = new TreeMap<>();

    private SiteMap() {
    }

    static SiteMap of(Sources sources) {
        SiteMap map = new SiteMap();
        map.page(Sources.README, "start/repository.html");
        for (DocFile doc : sources.docs()) {
            String page = pageOf(doc.path());
            map.page(doc.path(), page);
            if (DeckRecord.isDeck(doc.path())) {
                for (SlideOutline slide : DeckRecord.outline(doc).slides()) {
                    map.anchor(page, slide.id());
                    map.anchor(DeckRecord.notesPage(page), slide.id());
                }
            } else {
                map.anchors(page, doc.body());
            }
        }
        map.anchors("start/repository.html", sources.readme());
        map.page(NoticesPage.SOURCE, NoticesPage.PAGE);
        map.page(LegalPages.LICENSE_SOURCE, LegalPages.LICENSE_PAGE);
        map.page(LegalPages.NOTICE_SOURCE, LegalPages.NOTICE_PAGE);
        map.anchors(NoticesPage.PAGE, Sources.text(sources.repo(), NoticesPage.SOURCE));
        map.anchors("trust/allowlist.html", sources.securityReadme().body());
        map.page(Sources.SECURITY_README, "trust/allowlist.html");
        map.page(Allowlist.PATH, "trust/allowlist.html");
        map.page(RiskPolicy.PATH, "trust/risk-levels.html");
        map.page(Capabilities.PATH, "brain/index.html");
        map.page(Sources.BRAIN, "brain/index.html");
        map.page(Sources.WORKSPACE, "model/index.html");
        map.page(ApiContract.PATH, CONTRACT_DOWNLOAD);
        for (C4View view : sources.workspace().model().views()) {
            String slug = ViewNames.slug(view);
            map.page(ArchitecturePages.DIRECTORY + slug + ".md", "model/" + slug + ".html");
            map.page(ArchitecturePages.DIRECTORY + slug + ".mmd", "model/" + slug + ".html");
        }
        sources.workspace().model().modules().forEach(module -> {
            String id = module.contractId().orElseThrow();
            map.page(Brains.DIRECTORY + id + ".md", "brain/index.html#module-" + id);
        });
        return map;
    }

    /** The site page of a hand-written document under docs/. */
    static String pageOf(String docPath) {
        if (docPath.startsWith("docs/adr/")) {
            return "decisions/" + html(docPath.substring("docs/adr/".length()));
        }
        if (docPath.startsWith("docs/modules/")) {
            return "modules/" + html(docPath.substring("docs/modules/".length()));
        }
        if (docPath.startsWith("docs/flows/")) {
            return "model/flow-" + html(docPath.substring("docs/flows/".length()));
        }
        if (docPath.startsWith(DemoRecord.DIRECTORY)) {
            return "demos/" + html(docPath.substring(DemoRecord.DIRECTORY.length()));
        }
        return switch (docPath) {
            case "docs/ui/workbench.md" -> "modules/web-workbench.html";
            case "docs/ui/documentation-site.md" -> "start/site-design.html";
            case "docs/risk-levels.md" -> "trust/risk-levels.html";
            case "docs/gaps.md" -> "trust/gaps.html";
            case "docs/contributions.md" -> "trust/contributions.html";
            case "docs/contributions/history.md" -> "trust/contribution-history.html";
            default -> throw new DocsException(docPath + " has no page on the site; give it one in "
                    + "tools/docs (SiteMap) before publishing it");
        };
    }

    private static String html(String markdownFile) {
        return markdownFile.substring(0, markdownFile.length() - ".md".length()) + ".html";
    }

    private void page(String source, String sitePath) {
        pages.put(source, sitePath);
    }

    private void anchors(String page, String markdown) {
        Set<String> ids = anchors.computeIfAbsent(page, p -> new HashSet<>());
        for (Section section : MarkdownRenderer.headings(markdown, page)) {
            if (section.level() > 1) {
                ids.add(section.id());
            }
        }
    }

    /** Adds anchors that a generated page has. */
    void anchor(String page, String id) {
        anchors.computeIfAbsent(page, p -> new HashSet<>()).add(id);
    }

    /** The site path (with a fragment for brain sections) of a repository file, when it is published. */
    Optional<String> site(String repoPath) {
        return Optional.ofNullable(pages.get(repoPath));
    }

    boolean hasAnchor(String page, String id) {
        return anchors.getOrDefault(page, Set.of()).contains(id);
    }

    boolean isDownload(String sitePath) {
        return sitePath.startsWith("downloads/");
    }
}
