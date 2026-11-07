package io.github.stigjohnny.docs.repo;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.markdown.DeckOutline;
import io.github.stigjohnny.docs.markdown.MarkdownRenderer;
import io.github.stigjohnny.docs.yaml.YamlValue;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A milestone deck: the repository's state presented as slides for a meeting (docs/ui/documentation-site.md, "Decks").
 * It is not a demonstration: no pull request is its own. Its file is {@code docs/demos/deck-<slug>.md}, in Norwegian
 * Bokmål; each level-2 heading is a slide, those whose titles start with {@value #APPENDIX} form the appendix, and a
 * slide's notes stand under the reserved level-3 heading {@value #NOTES}. The check of its form is DemoCheck's
 * (DeckCheck); this record only reads it.
 *
 * @param slug the file name after {@code deck-}, which names the deck's pages
 * @param milestone the ISO date of the meeting the deck is made for
 * @param mainSlides the declared number of main slides
 * @param appendixSlides the declared number of appendix slides
 */
public record DeckRecord(DocFile doc, String slug, String milestone, int mainSlides, int appendixSlides) {

    /** Every file under docs/demos/ whose name starts with this is a deck, and is checked as one. */
    public static final String PREFIX = DemoRecord.DIRECTORY + "deck-";
    /** A deck's frontmatter: the four every document has, then its own. A pull request or a revision is not one. */
    public static final List<String> KEYS = List.of("title", "description", "status", "updated", "kind", "lang",
            "milestone", "mainSlides", "appendixSlides");
    public static final String KIND = "deck";
    /** The language of every deck: its slide controls are written in Norwegian Bokmål. */
    public static final String LANGUAGE = "nb";
    /** The reserved level-3 heading under which a slide's notes stand, never shown on the slide. */
    public static final String NOTES = "Notater";
    /** How an appendix slide's title starts. */
    public static final String APPENDIX = "Vedlegg:";

    private static final Pattern FILE = Pattern.compile("docs/demos/deck-([a-z0-9]+(?:-[a-z0-9]+)*)\\.md");
    private static final Pattern COUNT = Pattern.compile("0|[1-9][0-9]{0,3}");
    private static final String HTML = ".html";

    /** Whether a document under docs/ is a deck: its name under docs/demos/ starts with {@code deck-}. */
    public static boolean isDeck(String path) {
        return path.startsWith(PREFIX);
    }

    /** The slug of a deck's file name, when the name is {@code docs/demos/deck-<slug>.md} with a kebab-case slug. */
    public static Optional<String> slugOf(String path) {
        Matcher file = FILE.matcher(path);
        return file.matches() ? Optional.of(file.group(1)) : Optional.empty();
    }

    /** The page of a deck's notes, beside the deck's own: {@code demos/deck-x.html} has {@code demos/deck-x-notes.html}. */
    public static String notesPage(String deckPage) {
        if (!deckPage.endsWith(HTML)) {
            throw new DocsException(deckPage + " is not a page");
        }
        return deckPage.substring(0, deckPage.length() - HTML.length()) + "-notes" + HTML;
    }

    /** Whether a slide's title makes it an appendix slide. */
    public static boolean appendix(String title) {
        return title.startsWith(APPENDIX);
    }

    /** Reads a deck whose form DemoCheck has passed; any field it cannot read fails. */
    public static DeckRecord of(DocFile doc) {
        String slug = slugOf(doc.path()).orElseThrow(() -> new DocsException(doc.path() + ": a deck is named "
                + "docs/demos/deck-<slug>.md"));
        kindOf(doc);
        languageOf(doc);
        return new DeckRecord(doc, slug, milestoneOf(doc), mainSlidesOf(doc), appendixSlidesOf(doc));
    }

    /** Every deck of the sources, in the order of their files. */
    public static List<DeckRecord> all(Sources sources) {
        return sources.docsUnder(DemoRecord.DIRECTORY).stream().filter(d -> isDeck(d.path()))
                .sorted(Comparator.comparing(DocFile::path)).map(DeckRecord::of).toList();
    }

    /** {@code kind}: deck. */
    public static String kindOf(DocFile doc) {
        String value = doc.text("kind");
        if (!KIND.equals(value)) {
            throw new DocsException(doc.path() + ": kind '" + value + "' is not deck; a file named "
                    + "docs/demos/deck-<slug>.md is a deck");
        }
        return value;
    }

    /** {@code lang}: nb, the language of the slide controls. */
    public static String languageOf(DocFile doc) {
        String value = doc.text("lang");
        if (!LANGUAGE.equals(value)) {
            throw new DocsException(doc.path() + ": lang '" + value + "' is not nb; a deck is written in Norwegian "
                    + "Bokmål (nb), the language of its slide controls");
        }
        return value;
    }

    /** {@code milestone}: an ISO date, YYYY-MM-DD. */
    public static String milestoneOf(DocFile doc) {
        String value = doc.text("milestone");
        if (!DemoRecord.isoDate(value)) {
            throw new DocsException(doc.path() + ": milestone '" + value + "' is not an ISO date (YYYY-MM-DD)");
        }
        return value;
    }

    /** {@code mainSlides}: a whole number, written as a number. */
    public static int mainSlidesOf(DocFile doc) {
        return count(doc, "mainSlides", "main slides");
    }

    /** {@code appendixSlides}: a whole number, written as a number. */
    public static int appendixSlidesOf(DocFile doc) {
        return count(doc, "appendixSlides", "appendix slides");
    }

    /** The deck's slides as data, grouped on its parsed blocks, with their notes apart. */
    public static DeckOutline outline(DocFile doc) {
        return MarkdownRenderer.deck(doc.body(), doc.path(), NOTES);
    }

    /** This deck's slides as data. */
    public DeckOutline outline() {
        return outline(doc);
    }

    /** The deck's name in the English register: its slug in words, then "deck", such as "Milestone deck". */
    public String name() {
        String words = slug.replace('-', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1) + " deck";
    }

    /** The deck's language in English words. */
    public String language() {
        return "Norwegian";
    }

    private static int count(DocFile doc, String key, String what) {
        YamlValue value = doc.frontmatter().find(key)
                .orElseThrow(() -> new DocsException(doc.path() + ": frontmatter missing key '" + key + "'"));
        if (value instanceof YamlValue.YNumber number && COUNT.matcher(number.text()).matches()) {
            return Integer.parseInt(number.text());
        }
        throw new DocsException(doc.path() + ": " + key + " must be the number of " + what + ", a whole number "
                + "without quotes");
    }
}
