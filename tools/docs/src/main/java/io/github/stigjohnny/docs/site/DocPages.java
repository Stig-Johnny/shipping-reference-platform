package io.github.stigjohnny.docs.site;

import io.github.stigjohnny.docs.markdown.LinkResolver;
import io.github.stigjohnny.docs.markdown.MarkdownRenderer;
import io.github.stigjohnny.docs.markdown.RenderedMarkdown;
import io.github.stigjohnny.docs.markdown.Section;
import io.github.stigjohnny.docs.repo.Capability;
import io.github.stigjohnny.docs.repo.DocFile;
import java.util.ArrayList;
import java.util.List;

/** A page made from one hand-written document: its Markdown, rendered safely, under the page template. */
final class DocPages {

    private DocPages() {
    }

    static SitePage of(SiteContext context, DocFile doc, SiteSection section, String navLabel, String extraBody,
            List<Capability> capabilities) {
        return of(context, doc, section, navLabel, extraBody, capabilities, doc.body());
    }

    /** A page from a document whose Markdown the site has adapted, such as a flow with its dynamic view. */
    static SitePage of(SiteContext context, DocFile doc, SiteSection section, String navLabel, String extraBody,
            List<Capability> capabilities, String markdown) {
        String page = context.map().site(doc.path()).orElseThrow(() -> SiteContext.missing("a page for " + doc.path()));
        RenderedMarkdown rendered = context.render(markdown, doc.path(), page, true);
        return new SitePage(page, doc.title(), navLabel, section, doc.description(),
                new StatusPanel(Statuses.design(doc), Statuses.implementation(capabilities),
                        Statuses.evidence(capabilities, context.sources().evidence())),
                contents(rendered.headings()), rendered.html() + extraBody, context.mentioned(doc.body(), page),
                doc.path(), false);
    }

    /**
     * A page from a document that specifies no capability, such as a demonstration, whose ledger the caller scopes;
     * its Markdown is rendered with the caller's link policy.
     */
    static SitePage of(SiteContext context, DocFile doc, SiteSection section, String navLabel, StatusPanel status,
            LinkResolver links) {
        String page = context.map().site(doc.path()).orElseThrow(() -> SiteContext.missing("a page for " + doc.path()));
        RenderedMarkdown rendered = MarkdownRenderer.render(doc.body(), doc.path(), links, true);
        return new SitePage(page, doc.title(), navLabel, section, doc.description(), status,
                contents(rendered.headings()), rendered.html(), context.mentioned(doc.body(), page), doc.path(), false);
    }

    /** The level-2 sections of a page, when there are at least two. */
    static List<SitePage.Contents> contents(List<Section> headings) {
        List<SitePage.Contents> contents = new ArrayList<>();
        for (Section heading : headings) {
            if (heading.level() == 2) {
                contents.add(new SitePage.Contents(heading.id(), heading.text()));
            }
        }
        return contents.size() >= 2 ? contents : List.of();
    }
}
