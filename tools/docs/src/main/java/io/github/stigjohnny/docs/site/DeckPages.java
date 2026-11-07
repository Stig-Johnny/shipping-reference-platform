package io.github.stigjohnny.docs.site;

import io.github.stigjohnny.docs.Html;
import io.github.stigjohnny.docs.markdown.LinkResolver;
import io.github.stigjohnny.docs.markdown.MarkdownRenderer;
import io.github.stigjohnny.docs.markdown.RenderedSlide;
import io.github.stigjohnny.docs.repo.Capabilities;
import io.github.stigjohnny.docs.repo.Capability;
import io.github.stigjohnny.docs.repo.DeckRecord;
import io.github.stigjohnny.docs.repo.DocFile;
import io.github.stigjohnny.docs.repo.Sources;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * A milestone deck's two pages in the Demos section (docs/ui/documentation-site.md, "Decks"), in the language the deck
 * declares, English like the rest of the site: its slides, sheet X-Dnn, and its speaker notes, sheet X-Nnn.
 *
 * <ul>
 *   <li>The slides' page puts the register, the overview (the sheet header with the milestone, the source's update
 *       date and the build's revision, each on its own, and the ledger) and the slide index before the slides, so that
 *       none of them takes a slide's width; "Start slides" leads to slide one.
 *   <li>Each slide is a section labelled by its title, with a stable anchor, its title's slug: the deck's code and the
 *       slide's number ({@code 01/10}, {@code A1/4} in the appendix, read out in words), the title, the content, the
 *       links "Previous", "Overview" and "Next", which are the slides' controls, and the source and revision it was
 *       built from. From the first slide "Previous", and from the last "Next", lead back to the index.
 *   <li>Slide 02 carries the repository's ledger from the sources, as the start sheet has it, and says what is still
 *       done by hand.
 *   <li>Notes never stand on a slide: the notes page has each slide's, under its number and title, which link back to
 *       the slide.
 * </ul>
 */
final class DeckPages {

    /** A deck's state word, a neutral label: a presentation is never evidence. */
    static final String PRESENTATION = "Presentation";
    private static final String INDEX_ID = "site-deck-index";
    private static final String RISK_CONTROL = "control.risk-classification";

    /**
     * A slide with its place: its position in the deck, its number among the main slides (0 in the appendix), and its
     * number as shown and as read out.
     */
    private record Numbered(RenderedSlide slide, int position, int main, String number, String words) {
    }

    private DeckPages() {
    }

    /** The deck's slides and its notes; {@code links} gives the link policy of each of the two pages. */
    static List<SitePage> of(SiteContext context, DeckRecord deck, Function<String, LinkResolver> links) {
        DocFile doc = deck.doc();
        String page = context.map().site(doc.path())
                .orElseThrow(() -> SiteContext.missing("a page for " + doc.path()));
        String notesPage = DeckRecord.notesPage(page);
        List<Numbered> slides = numbered(MarkdownRenderer.renderDeck(doc.body(), doc.path(), links.apply(page),
                DeckRecord.NOTES).slides());
        List<Numbered> notes = numbered(MarkdownRenderer.renderDeck(doc.body(), doc.path(), links.apply(notesPage),
                DeckRecord.NOTES).slides());
        return List.of(slides(context, deck, page, notesPage, slides), notes(context, deck, page, notesPage, notes));
    }

    /** A link to a deck, by its name. */
    static String link(String from, String page, DeckRecord deck) {
        return SiteContext.link(from, page, deck.name());
    }

    /** A deck in the register of demonstrations, above them: a presentation, with its dates and its notes. */
    static String feature(String from, String page, DeckRecord deck) {
        String notes = DeckRecord.notesPage(page);
        return "<div class=\"register-feature\">\n<p class=\"card-title\"><span class=\"register-code\">"
                + Sheets.code(page) + "</span> " + link(from, page, deck) + " " + Badge.NEUTRAL.html(PRESENTATION)
                + "</p>\n<p>" + Html.escape(deck.doc().description()) + "</p>\n<p class=\"card-meta\">Milestone "
                + DemoPages.time(deck.milestone()) + " · Updated " + DemoPages.time(deck.doc().updated())
                + " · Speaker notes " + SiteContext.link(from, notes, Sheets.code(notes)) + "</p>\n</div>\n";
    }

    private static SitePage slides(SiteContext context, DeckRecord deck, String page, String notesPage,
            List<Numbered> slides) {
        DocFile doc = deck.doc();
        String code = Sheets.code(page);
        long main = slides.stream().filter(s -> !DeckRecord.appendix(s.slide().title())).count();
        long appendix = slides.size() - main;
        String counted = main + (main == 1 ? " slide" : " slides") + (appendix == 0 ? ""
                : " and " + appendix + (appendix == 1 ? " appendix slide" : " appendix slides"));
        String facts = "<p class=\"page-facts\">" + Html.escape(PRESENTATION + " · Milestone ")
                + DemoPages.time(deck.milestone()) + Html.escape(" · " + counted + " · Speaker notes ")
                + SiteContext.link(page, notesPage, Sheets.code(notesPage)) + "</p>\n"
                + "<p class=\"page-facts\">The milestone is the meeting the deck is made for, Updated is the date of its "
                + "source, and Revision is the commit this site was built from.</p>\n"
                + "<p class=\"deck-start-line\"><a class=\"deck-start\" href=\"#"
                + Html.escape(slides.getFirst().slide().id()) + "\">Start slides</a></p>\n";
        StringBuilder body = new StringBuilder(index(slides));
        body.append("<div class=\"deck-slides\">\n");
        String ledger = "<div class=\"slide-ledger\">\n" + ledger(context.sources()).grid() + "</div>\n";
        for (int i = 0; i < slides.size(); i++) {
            Numbered slide = slides.get(i);
            String previous = i == 0 ? INDEX_ID : slides.get(i - 1).slide().id();
            String next = i == slides.size() - 1 ? INDEX_ID : slides.get(i + 1).slide().id();
            body.append(slide(code, slide, previous, next, slide.main() == 2 ? ledger : "", doc.path(),
                    context.revision()));
        }
        body.append("</div>\n");
        StatusPanel status = new StatusPanel(Statuses.design(doc), Badge.NEUTRAL.html("Not applicable") + " "
                + Html.escape("A deck presents the repository's state and specifies no capability of its own; slide "
                        + "02 carries the repository's ledger."),
                Badge.NEUTRAL.html(PRESENTATION) + " " + Html.escape("A deck is not evidence of this build: the "
                        + "recorded output it shows was not rerun, and only the ledger on slide 02 counts the tests "
                        + "that passed in it."));
        return new SitePage(page, doc.title(), deck.name(), SiteSection.DEMOS, doc.description(), status, List.of(),
                body.toString(), List.of(), doc.path(), false, PRESENTATION, facts, deck.lang(), SitePage.Layout.DECK);
    }

    /** The slide index, "Overview": a register of the slides, the main ones and then the appendix. */
    private static String index(List<Numbered> slides) {
        StringBuilder out = new StringBuilder("<nav class=\"deck-index\" id=\"" + INDEX_ID + "\" aria-labelledby=\""
                + INDEX_ID + "-title\">\n<h2 id=\"" + INDEX_ID + "-title\">Overview</h2>\n");
        List<Numbered> main = slides.stream().filter(s -> !DeckRecord.appendix(s.slide().title())).toList();
        List<Numbered> appendix = slides.stream().filter(s -> DeckRecord.appendix(s.slide().title())).toList();
        out.append(list(main));
        if (!appendix.isEmpty()) {
            out.append("<h3 id=\"site-deck-appendix-title\">Appendix</h3>\n").append(list(appendix));
        }
        return out.append("</nav>\n").toString();
    }

    private static String list(List<Numbered> slides) {
        StringBuilder out = new StringBuilder("<ol class=\"deck-index-list\">\n");
        for (Numbered slide : slides) {
            out.append("<li><a href=\"#").append(Html.escape(slide.slide().id())).append("\"><span class=\"register-code\" "
                    + "aria-hidden=\"true\">").append(slide.number()).append("</span> <span class=\"register-title\">")
                    .append(Html.escape(slide.slide().title())).append("</span></a></li>\n");
        }
        return out.append("</ol>\n").toString();
    }

    /** One slide, drawn as a sheet: code and number, title, content, then the controls and its provenance. */
    private static String slide(String code, Numbered numbered, String previous, String next, String ledger,
            String source, String revision) {
        RenderedSlide slide = numbered.slide();
        String title = "site-slide-" + numbered.position();
        return "<section class=\"slide\" id=\"" + Html.escape(slide.id()) + "\" aria-labelledby=\"" + title + "\">\n"
                + "<p class=\"slide-code\">" + code + "<span aria-hidden=\"true\"> · " + numbered.number()
                + "</span><span class=\"visually-hidden\">, " + numbered.words() + "</span></p>\n"
                + "<h2 id=\"" + title + "\">" + slide.titleHtml() + "</h2>\n"
                + "<div class=\"slide-body\">\n" + slide.html() + ledger + "</div>\n"
                + "<footer class=\"slide-foot\">\n<ul class=\"slide-controls\">\n"
                + "<li class=\"slide-previous\"><a href=\"#" + Html.escape(previous) + "\">Previous</a></li>\n"
                + "<li class=\"slide-overview\"><a href=\"#" + INDEX_ID + "\">Overview</a></li>\n"
                + "<li class=\"slide-next\"><a href=\"#" + Html.escape(next) + "\">Next</a></li>\n</ul>\n"
                + "<p class=\"slide-source\">" + Html.escape(source) + " · Revision <code>"
                + Html.escape(revision.substring(0, 8)) + "</code></p>\n</footer>\n</section>\n";
    }

    /**
     * The repository's ledger for slide 02: the start sheet's statements from the sources, and, while the risk
     * classification is not shipped, what is done by hand until it is.
     */
    private static StatusPanel ledger(Sources sources) {
        StatusPanel state = StartPages.repositoryState(sources);
        Capability control = sources.capabilities().controls().stream().filter(c -> c.id().equals(RISK_CONTROL))
                .findFirst().orElseThrow(() -> SiteContext.missing(RISK_CONTROL + " in " + Capabilities.PATH
                        + ", from which slide 02 says what is done by hand"));
        String manual = control.status() == Capability.Status.SHIPPED ? "" : " " + Html.escape("Manual today: the "
                + "author of a change states its risk level, and the owner records the approval of a level 3 change; "
                + "the " + control.check() + " check that is to enforce this (" + control.id() + ") is "
                + control.status().value() + ".");
        return new StatusPanel(state.design(), state.implementation() + manual, state.evidence());
    }

    private static SitePage notes(SiteContext context, DeckRecord deck, String page, String notesPage,
            List<Numbered> slides) {
        DocFile doc = deck.doc();
        StringBuilder body = new StringBuilder();
        for (Numbered numbered : slides) {
            RenderedSlide slide = numbered.slide();
            String title = "site-note-" + numbered.position();
            body.append("<section class=\"deck-note\" id=\"").append(Html.escape(slide.id()))
                    .append("\" aria-labelledby=\"").append(title).append("\">\n<h2 id=\"").append(title)
                    .append("\"><span class=\"register-code\" aria-hidden=\"true\">").append(numbered.number())
                    .append("</span> <span class=\"visually-hidden\">")
                    .append(numbered.words().substring(0, 1).toUpperCase(Locale.ROOT))
                    .append(numbered.words().substring(1)).append(": </span>")
                    .append(SiteContext.link(notesPage, page + "#" + slide.id(), slide.title())).append("</h2>\n")
                    .append(slide.hasNotes() && !slide.notesHtml().isBlank() ? slide.notesHtml()
                            : "<p class=\"deck-note-none\">No notes.</p>\n")
                    .append("</section>\n");
        }
        String facts = "<p class=\"page-facts\">" + Html.escape("Speaker notes · Milestone ")
                + DemoPages.time(deck.milestone()) + " · Slides " + SiteContext.link(notesPage, page, Sheets.code(page))
                + "</p>\n";
        StatusPanel status = new StatusPanel(Statuses.design(doc), Badge.NEUTRAL.html("Not applicable") + " "
                + Html.escape("Speaker notes specify no capability."), Badge.NEUTRAL.html("Not applicable") + " "
                + Html.escape("Notes are not evidence: the recorded output they quote was not rerun by this build."));
        return new SitePage(notesPage, doc.title() + " \u2014 notes", deck.name() + " · speaker notes",
                SiteSection.DEMOS, "The speaker notes of the " + deck.name().toLowerCase(Locale.ROOT) + ": each slide's "
                        + "notes, linked back to the slide, which never shows them.", status, List.of(), body.toString(),
                context.mentioned(doc.body(), notesPage), doc.path(), false, null, facts, deck.lang(),
                SitePage.Layout.SHEET);
    }

    /** The slides with their numbers: 01/10 to 10/10 for the main ones, then A1/4 to A4/4 for the appendix. */
    private static List<Numbered> numbered(List<RenderedSlide> slides) {
        int main = (int) slides.stream().filter(s -> !DeckRecord.appendix(s.title())).count();
        int appendix = slides.size() - main;
        List<Numbered> numbered = new ArrayList<>();
        int mains = 0;
        int appendices = 0;
        for (RenderedSlide slide : slides) {
            if (DeckRecord.appendix(slide.title())) {
                appendices++;
                numbered.add(new Numbered(slide, numbered.size() + 1, 0, "A" + appendices + "/" + appendix,
                        "appendix slide " + appendices + " of " + appendix));
            } else {
                mains++;
                numbered.add(new Numbered(slide, numbered.size() + 1, mains, number(mains, main),
                        "slide " + mains + " of " + main));
            }
        }
        return numbered;
    }

    private static String number(int slide, int of) {
        return String.format(Locale.ROOT, "%02d/%02d", slide, of);
    }
}
