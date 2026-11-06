package io.github.stigjohnny.docs.site;

import io.github.stigjohnny.docs.Html;
import io.github.stigjohnny.docs.repo.Sources;
import java.util.List;

/**
 * The repository's licence and NOTICE, published with the site as the Apache License 2.0 asks: each file complete and
 * unchanged, in a preformatted block, beside the third-party notices.
 */
final class LegalPages {

    static final String LICENSE_SOURCE = "LICENSE";
    static final String NOTICE_SOURCE = "NOTICE";
    static final String LICENSE_PAGE = "trust/license.html";
    static final String NOTICE_PAGE = "trust/notice.html";

    private LegalPages() {
    }

    static SitePage licence(SiteContext context) {
        return page(context, LICENSE_SOURCE, LICENSE_PAGE, "Repository licence", "The licence of this repository, "
                + "the Apache License 2.0, as its LICENSE file has it.", List.of(
                        new SitePage.Related(NOTICE_PAGE, "Repository NOTICE"),
                        new SitePage.Related(NoticesPage.PAGE, "Third-party notices")));
    }

    static SitePage notice(SiteContext context) {
        return page(context, NOTICE_SOURCE, NOTICE_PAGE, "Repository NOTICE", "The attribution notice that the Apache "
                + "License 2.0 asks every redistribution to keep, as the repository's NOTICE file has it.", List.of(
                        new SitePage.Related(LICENSE_PAGE, "Repository licence"),
                        new SitePage.Related(NoticesPage.PAGE, "Third-party notices")));
    }

    private static SitePage page(SiteContext context, String source, String path, String title, String summary,
            List<SitePage.Related> related) {
        String text = Sources.text(context.sources().repo(), source);
        // The parser drops one newline right after <pre>, so one is written for it and the file's own text stays whole.
        String body = "<pre class=\"license\">\n" + Html.escape(text) + "</pre>\n";
        return new SitePage(path, title, title, SiteSection.TRUST, summary, new StatusPanel(
                Badge.NEUTRAL.html("Not applicable") + " A legal text, not a design decision.",
                Badge.NEUTRAL.html("Not applicable") + " It describes no capability.",
                Badge.NEUTRAL.html("Copied") + " The page holds the complete file, unchanged."),
                List.of(), body, related, source, false);
    }
}
