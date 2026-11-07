package io.github.stigjohnny.docs.check;

import io.github.stigjohnny.docs.markdown.DeckOutline;
import io.github.stigjohnny.docs.markdown.MarkdownBlock;
import io.github.stigjohnny.docs.markdown.MarkdownRenderer;
import io.github.stigjohnny.docs.markdown.SlideOutline;
import io.github.stigjohnny.docs.repo.Capabilities;
import io.github.stigjohnny.docs.repo.Capability;
import io.github.stigjohnny.docs.repo.DeckRecord;
import io.github.stigjohnny.docs.repo.DemoRecord;
import io.github.stigjohnny.docs.repo.DocFile;
import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.yaml.YamlValue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The milestone decks under docs/demos/ (docs/ui/documentation-site.md, "Decks"), checked with the demonstrations
 * before anything is generated or published. A deck:
 *
 * <ul>
 *   <li>is {@code docs/demos/deck-<slug>.md}, and its notes page is no other deck's page;
 *   <li>has the documents' frontmatter and its own, nothing else: {@code kind} deck, {@code lang} en, an ISO
 *       {@code milestone}, and {@code mainSlides} and {@code appendixSlides}, the numbers of its slides;
 *   <li>has one title, its level-1 heading, which is the frontmatter's, and nothing else before its first slide;
 *   <li>has its slides as level-2 headings, each with a title, an anchor of its own and content; the appendix slides,
 *       whose titles start with "Appendix:", last; at least two main slides, because slide 02 carries the repository's
 *       ledger; and, below a slide's title, no heading but {@code ### Notes}, which starts its notes. Notes are never
 *       counted as slides;
 *   <li>is text only: no image, raw HTML, media link or code but recorded output in {@code ```text} fences, no
 *       placeholder (TODO, TBD, {@code <...>} or YYYY-MM-DD), and in-page links that name its slides;
 *   <li>shows the line "Recorded output — not rerun by this site build." wherever it shows recorded output, and the
 *       slide's notes say when it was recorded, on which revision, with which approved command and with which exit
 *       status;
 *   <li>counts capabilities, "N of M capabilities", only as docs/capabilities/capabilities.yaml does: N planned of M
 *       product capabilities.
 * </ul>
 *
 * <p>Every block is held to these checks: the title, the slides' and notes' headings and anything before the first
 * slide included. No command is ever run.
 */
final class DeckCheck {

    private static final String TEXT = "text";
    private static final String TEXT_ONLY = "; a deck is text only";
    private static final String NAMED = "a deck is named docs/demos/deck-<slug>.md: deck-, then a kebab-case slug";
    private static final String OWN_TITLE = "; each slide needs a title of its own";
    private static final Pattern PLACEHOLDER = Pattern.compile("<(?:\\.\\.\\.|…|[A-Za-z][^<>\\n]*)>");
    private static final Pattern PLACEHOLDER_LINE = Pattern.compile("<[A-Za-z][^<>]*\\s[^<>]*>");
    private static final Pattern PLACEHOLDER_WORD = Pattern.compile("(?i)\\b(?:TODO|TBD)\\b");
    private static final Pattern TEMPLATE_DATE = Pattern.compile("(?i)YYYY-MM-DD");
    private static final Pattern CAPABILITIES = Pattern.compile("(?<![0-9])([0-9]+) of ([0-9]+) capabilities\\b");
    private static final Pattern RUN_DATE = Pattern.compile("(?<![0-9])([0-9]{4}-[0-9]{2}-[0-9]{2})(?![0-9])");
    private static final Pattern REVISION = Pattern.compile("\\brevision [0-9a-f]{7,40}\\b");
    private static final Pattern EXIT = Pattern.compile("\\bexited [0-9]+\\b");

    /** Where a block stands, for a message: the title, a slide, or a slide's notes, which take a verb in the plural. */
    private record Where(String name, boolean plural) {

        String says(String singular, String pluralVerb) {
            return name + " " + (plural ? pluralVerb : singular);
        }
    }

    private DeckCheck() {
    }

    /** The problems of the given decks, read against the sources: the capabilities and the commands they name. */
    static List<String> problems(Sources sources, List<DocFile> decks, Set<String> checkerTests) {
        Set<String> problems = new TreeSet<>();
        List<DocFile> sorted = decks.stream().sorted(Comparator.comparing(DocFile::path)).toList();
        Map<String, String> pages = new TreeMap<>();
        for (DocFile doc : sorted) {
            DeckRecord.slugOf(doc.path()).ifPresent(slug -> pages.put(page(slug), doc.path()));
        }
        for (DocFile doc : sorted) {
            List<String> found = new ArrayList<>();
            metadata(doc, found);
            body(doc, sources.capabilities(), checkerTests, found);
            DeckRecord.slugOf(doc.path()).ifPresent(slug -> {
                String notes = DeckRecord.notesPage(page(slug));
                String other = pages.get(notes);
                if (other != null) {
                    found.add("its notes page, " + notes + ", is also the page of " + other);
                }
            });
            found.forEach(problem -> problems.add(problem.startsWith(doc.path() + ": ") ? problem
                    : doc.path() + ": " + problem));
        }
        return List.copyOf(problems);
    }

    private static String page(String slug) {
        return "demos/deck-" + slug + ".html";
    }

    private static void metadata(DocFile doc, List<String> found) {
        if (DeckRecord.slugOf(doc.path()).isEmpty()) {
            found.add(NAMED);
        }
        for (String key : doc.frontmatter().entries().keySet()) {
            if (!DeckRecord.KEYS.contains(key)) {
                found.add("frontmatter key '" + key + "' is not one of title, description, status, updated, kind, "
                        + "lang, milestone, mainSlides and appendixSlides; a deck names no pull request, and no "
                        + "revision is entered by hand");
            }
        }
        DemoCheck.read(() -> DeckRecord.kindOf(doc), found);
        DemoCheck.read(() -> DeckRecord.languageOf(doc), found);
        DemoCheck.read(() -> DeckRecord.milestoneOf(doc), found);
        for (String key : List.of("title", "description")) {
            doc.optional(key).ifPresent(value -> {
                placeholder(value).ifPresent(left -> found.add(key + " holds '" + left + "', an unfilled "
                        + "placeholder"));
                word(value).ifPresent(left -> found.add(key + " holds '" + left + "', a placeholder"));
            });
        }
        boolean dateLeft = TEMPLATE_DATE.matcher(doc.body()).find() || doc.frontmatter().entries().values().stream()
                .anyMatch(value -> value instanceof YamlValue.YString text
                        && TEMPLATE_DATE.matcher(text.value()).find());
        if (dateLeft) {
            found.add("YYYY-MM-DD is left from a template");
        }
    }

    private static void body(DocFile doc, Capabilities capabilities, Set<String> checkerTests, List<String> found) {
        DeckOutline deck = DeckRecord.outline(doc);
        Optional<String> title = front(doc, deck.front(), found);
        Map<String, String> anchors = new HashMap<>();
        title.ifPresent(text -> anchors.put(MarkdownRenderer.slug(text), "the deck's title has"));
        anchors.put(MarkdownRenderer.slug(DeckRecord.NOTES), "the notes headings have");
        Set<String> slides = new HashSet<>();
        for (SlideOutline slide : deck.slides()) {
            if (!slide.title().isEmpty()) {
                slides.add(slide.id());
            }
        }
        int number = 0;
        int main = 0;
        int appendix = 0;
        for (SlideOutline slide : deck.slides()) {
            number++;
            Where where = new Where("slide " + number + (slide.title().isEmpty() ? "" : " '" + slide.title() + "'"),
                    false);
            Where notes = new Where("the notes of " + where.name(), true);
            if (slide.title().isEmpty()) {
                found.add(where.name() + " has no title");
            } else {
                String owner = anchors.putIfAbsent(slide.id(), "slide " + number + " also has");
                if (owner != null) {
                    found.add(where.name() + " has the anchor #" + slide.id() + ", which " + owner + OWN_TITLE);
                }
            }
            if (DeckRecord.appendix(slide.title())) {
                appendix++;
            } else {
                main++;
                if (appendix > 0) {
                    found.add("the main slide '" + slide.title() + "' comes after an appendix slide; the appendix "
                            + "comes last");
                }
            }
            slide(slide, where, notes, slides, found);
            recorded(slide, where, notes, checkerTests, found);
            counts(slide, where, notes, capabilities, found);
        }
        int mains = main;
        int appendices = appendix;
        DemoCheck.read(() -> DeckRecord.mainSlidesOf(doc), found).filter(declared -> declared != mains)
                .ifPresent(declared -> found.add("mainSlides is " + declared + ", but the deck has " + mains
                        + (mains == 1 ? " main slide" : " main slides")));
        DemoCheck.read(() -> DeckRecord.appendixSlidesOf(doc), found).filter(declared -> declared != appendices)
                .ifPresent(declared -> found.add("appendixSlides is " + declared + ", but the deck has " + appendices
                        + (appendices == 1 ? " appendix slide" : " appendix slides") + " (a title that starts with '"
                        + DeckRecord.APPENDIX + "')"));
        if (main < 2) {
            found.add("a deck has at least two main slides; slide 02 carries the repository's ledger");
        }
    }

    /** What stands before the first slide: the title, the frontmatter's, and nothing else. Returns the title. */
    private static Optional<String> front(DocFile doc, List<MarkdownBlock> front, List<String> found) {
        Optional<String> title = Optional.empty();
        boolean before = false;
        for (MarkdownBlock block : front) {
            if (block.kind() == MarkdownBlock.Kind.HEADING && block.level() == 1) {
                if (title.isPresent()) {
                    found.add("a second level-1 heading, '" + block.text() + "'; a deck has one title");
                } else {
                    title = Optional.of(block.text());
                }
                block(new Where("the deck's title", false), block, found);
            } else {
                before = true;
                block(new Where("the text before the first slide", false), block, found);
            }
        }
        if (before) {
            found.add("text stands before the first slide; everything stands under a slide's level-2 heading");
        }
        if (title.isEmpty()) {
            found.add("the deck has no level-1 heading; it starts with # and its title");
        } else {
            String heading = title.get();
            DemoCheck.read(doc::title, found).filter(text -> !text.equals(heading)).ifPresent(text -> found.add(
                    "the level-1 heading '" + heading + "' is not the title '" + text + "'"));
        }
        return title;
    }

    /** A slide's blocks: its heading, its content and its notes, each held to the block checks, and their anchors. */
    private static void slide(SlideOutline slide, Where where, Where notes, Set<String> slides, List<String> found) {
        block(where, slide.heading(), found);
        links(where, slide.heading(), slides, found);
        if (slide.content().stream().noneMatch(MarkdownBlock::hasContent)) {
            found.add(where.says("is", "are") + " empty");
        }
        for (MarkdownBlock block : slide.content()) {
            if (block.kind() == MarkdownBlock.Kind.HEADING) {
                found.add(block.level() == 1 ? "a second level-1 heading, '" + block.text() + "'; a deck has one title"
                        : where.says("has", "have") + " the heading '" + block.text() + "'; below its title a slide "
                                + "has only ### " + DeckRecord.NOTES + ", which starts its notes");
            }
            block(where, block, found);
            links(where, block, slides, found);
        }
        MarkdownBlock heading = slide.notesHeading();
        if (heading == null) {
            return;
        }
        block(notes, heading, found);
        links(notes, heading, slides, found);
        if (slide.notes().stream().noneMatch(MarkdownBlock::hasContent)) {
            found.add(notes.says("is", "are") + " empty");
        }
        for (MarkdownBlock block : slide.notes()) {
            if (block.kind() == MarkdownBlock.Kind.HEADING) {
                found.add(block.level() == 1 ? "a second level-1 heading, '" + block.text() + "'; a deck has one title"
                        : notes.says("has", "have") + " the heading '" + block.text() + "'; notes have no headings");
            }
            block(notes, block, found);
            links(notes, block, slides, found);
        }
    }

    /** One block, wherever it stands: its fences, placeholders, media and raw HTML. */
    private static void block(Where where, MarkdownBlock block, List<String> found) {
        switch (block.kind()) {
            case FENCED_CODE -> {
                if (!block.info().equals(TEXT)) {
                    found.add(where.says("has", "have") + (block.info().isEmpty() ? " a fence without ```text"
                            : " a ```" + block.info() + " fence") + "; a deck shows recorded output in ```text "
                            + "fences and nothing else");
                } else if (block.text().isBlank()) {
                    found.add(where.says("has", "have") + " an empty output fence");
                } else {
                    block.text().lines().map(String::strip).filter(line -> PLACEHOLDER_LINE.matcher(line).matches())
                            .findFirst().ifPresent(left -> found.add(where.says("holds", "hold") + " '" + left
                                    + "', an unfilled placeholder"));
                }
            }
            case INDENTED_CODE -> found.add(where.says("has", "have") + " an indented code block; recorded output "
                    + "goes in a ```text fence");
            case HEADING, OTHER -> {
                if (!block.code().isEmpty()) {
                    found.add(where.says("has", "have") + " a code block inside a list or a quote; fences stand on "
                            + "their own");
                }
                for (String image : block.images()) {
                    found.add(where.says("shows", "show") + " the image '" + image + "'" + TEXT_ONLY);
                }
                for (String link : block.links()) {
                    Optional<String> path = DemoCheck.decodedPath(link);
                    if (path.isEmpty()) {
                        found.add(where.says("links", "link") + " '" + link + "', whose percent-encoding is malformed");
                    } else if (DemoCheck.MEDIA.matcher(path.get()).find()) {
                        found.add(where.says("links", "link") + " the media file '" + link + "'" + TEXT_ONLY);
                    }
                }
                if (!block.html().isEmpty()) {
                    found.add(where.says("holds", "hold") + " '" + block.html().getFirst() + "': raw HTML or an "
                            + "unfilled placeholder" + TEXT_ONLY);
                }
                placeholder(block.prose()).ifPresent(left -> found.add(where.says("holds", "hold") + " '" + left
                        + "', an unfilled placeholder"));
                word(block.prose()).ifPresent(left -> found.add(where.says("holds", "hold") + " '" + left
                        + "', a placeholder"));
            }
        }
    }

    /** A deck's pages have no anchor but its slides': an in-page link names a slide. */
    private static void links(Where where, MarkdownBlock block, Set<String> slides, List<String> found) {
        for (String link : block.links()) {
            if (link.startsWith("#") && !slides.contains(link.substring(1))) {
                found.add(where.says("links", "link") + " " + link + ", which is no slide of this deck");
            }
        }
    }

    /**
     * Recorded output carries its line wherever it is shown, and the slide's notes say when it was recorded, on which
     * revision, with which approved command and with which exit status.
     */
    private static void recorded(SlideOutline slide, Where where, Where notes, Set<String> checkerTests,
            List<String> found) {
        boolean onSlide = outputs(slide.content());
        boolean inNotes = outputs(slide.notes());
        if (onSlide && !labelled(slide.content())) {
            found.add(where.says("shows", "show") + " recorded output (```text) without the line '"
                    + DemoRecord.RECORDED_OUTPUT + "'");
        }
        if (inNotes && !labelled(slide.notes())) {
            found.add(notes.says("shows", "show") + " recorded output (```text) without the line '"
                    + DemoRecord.RECORDED_OUTPUT + "'");
        }
        if (!onSlide && !inNotes) {
            return;
        }
        if (!slide.hasNotes()) {
            found.add(where.name() + " shows recorded output, but has no notes that record its date, revision, "
                    + "command and exit status");
            return;
        }
        List<MarkdownBlock> prose = slide.notes().stream().filter(b -> b.kind() == MarkdownBlock.Kind.OTHER).toList();
        String text = String.join("\n", prose.stream().map(MarkdownBlock::text).toList());
        if (RUN_DATE.matcher(text).results().noneMatch(date -> DemoRecord.isoDate(date.group(1)))) {
            found.add(notes.says("does", "do") + " not name the date its output was recorded (YYYY-MM-DD)");
        }
        if (!REVISION.matcher(text).find()) {
            found.add(notes.says("does", "do") + " not name the revision it was recorded on (revision and seven to "
                    + "forty hexadecimal digits)");
        }
        boolean command = prose.stream().flatMap(b -> b.spans().stream())
                .anyMatch(span -> DemoCheck.approved(span.strip(), checkerTests));
        if (!command) {
            found.add(notes.says("does", "do") + " not name the command that recorded it, an approved command of "
                    + "docs/demos/how-to.md in a code span");
        }
        if (!EXIT.matcher(text).find()) {
            found.add(notes.says("does", "do") + " not state its exit status (exited and a number)");
        }
    }

    /** Whether blocks show recorded output: a ```text fence of their own. */
    private static boolean outputs(List<MarkdownBlock> blocks) {
        return blocks.stream().anyMatch(b -> b.kind() == MarkdownBlock.Kind.FENCED_CODE && b.info().equals(TEXT));
    }

    /** Whether a line of the blocks' text, a paragraph's or a list item's, starts with the recorded output's line. */
    private static boolean labelled(List<MarkdownBlock> blocks) {
        return blocks.stream().filter(b -> b.kind() == MarkdownBlock.Kind.OTHER).flatMap(b -> b.text().lines())
                .map(String::strip).anyMatch(line -> line.startsWith(DemoRecord.RECORDED_OUTPUT));
    }

    /** A count of capabilities, "N of M capabilities", on a slide or in its notes, is the sources' own. */
    private static void counts(SlideOutline slide, Where where, Where notes, Capabilities capabilities,
            List<String> found) {
        List<Capability> product = capabilities.capabilities();
        long planned = product.stream().filter(c -> c.status() == Capability.Status.PLANNED).count();
        String slideText = String.join("\n", Stream.concat(Stream.of(slide.heading()), slide.content().stream())
                .filter(b -> b.kind() != MarkdownBlock.Kind.FENCED_CODE && b.kind() != MarkdownBlock.Kind.INDENTED_CODE)
                .map(MarkdownBlock::text).toList());
        String notesText = String.join("\n", slide.notes().stream()
                .filter(b -> b.kind() != MarkdownBlock.Kind.FENCED_CODE && b.kind() != MarkdownBlock.Kind.INDENTED_CODE)
                .map(MarkdownBlock::text).toList());
        for (Map.Entry<Where, String> part : List.of(Map.entry(where, slideText), Map.entry(notes, notesText))) {
            Matcher count = CAPABILITIES.matcher(part.getValue().replaceAll("\\s+", " "));
            while (count.find()) {
                if (Long.parseLong(count.group(1)) != planned || Long.parseLong(count.group(2)) != product.size()) {
                    found.add(part.getKey().says("says", "say") + " '" + count.group() + "', but "
                            + Capabilities.PATH + " has " + planned + " of " + product.size() + " product "
                            + "capabilities planned");
                }
            }
        }
    }

    /** The first unfilled placeholder of a text, such as {@code <insert the numbers>} or {@code <...>}. */
    private static Optional<String> placeholder(String text) {
        Matcher left = PLACEHOLDER.matcher(text);
        return left.find() ? Optional.of(left.group()) : Optional.empty();
    }

    /** The first placeholder word of a text: TODO or TBD, in any case. */
    private static Optional<String> word(String text) {
        Matcher left = PLACEHOLDER_WORD.matcher(text);
        return left.find() ? Optional.of(left.group()) : Optional.empty();
    }
}
