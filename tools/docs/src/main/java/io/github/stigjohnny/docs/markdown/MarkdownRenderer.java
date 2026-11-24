package io.github.stigjohnny.docs.markdown;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.Html;
import io.github.stigjohnny.docs.markdown.LinkDecision.Keep;
import io.github.stigjohnny.docs.markdown.LinkDecision.Reject;
import io.github.stigjohnny.docs.markdown.LinkDecision.Rewrite;
import io.github.stigjohnny.docs.markdown.LinkDecision.Unlink;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Block;
import org.commonmark.node.Code;
import org.commonmark.node.CustomNode;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.SourceSpan;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jspecify.annotations.Nullable;

/**
 * Markdown to HTML for the public site, under the CommonMark conditions of
 * security/reviews/maven--docs-tool-scope-extension.md. It is the only class that uses CommonMark
 * (DocsReviewConditionsTest).
 *
 * <ul>
 *   <li>One parser and one renderer, built once: the GFM tables extension and nothing else; the renderer escapes raw
 *       HTML ({@code escapeHtml(true)}) and sanitises URLs ({@code sanitizeUrls(true)}) with {@link SiteUrlSanitizer},
 *       which allows only http, https, mailto, relative and fragment URLs. No node renderer calls {@code raw()} and no
 *       attribute provider exists.
 *   <li>Links are rewritten on the parsed {@link Link} nodes, before rendering: a {@link LinkResolver} turns links to
 *       published sources into site links and links the public cannot open into plain text, and rejects broken ones.
 *       In-page anchors must exist. A destination the sanitizer would empty fails the build instead of rendering
 *       {@code href=""}, and images fail (the site publishes none).
 *   <li>Headings get GitHub's anchor IDs, written by this class's own template from a slug of letters, digits,
 *       {@code _} and {@code -}; tables are wrapped in labelled, keyboard-scrollable regions; a Mermaid fence, at any
 *       depth, is shown as its closed source, because only the generator's validated diagrams are ever rendered. The
 *       fence's text gets the class's own placeholders on the parsed tree, and the closed source's markup is constant;
 *       a sequence or state diagram that the label right before it disclaims shows the unchecked-design label in
 *       current prose (ADR 0024). {@link #diagrams} reads the same fences and labels as data, for the check.
 *   <li>CommonMark 0.29.0 has no nesting limits, so the tree is measured before rendering and anything deeper than
 *       {@link #MAX_DEPTH} fails; any exception or error, {@link StackOverflowError} included, fails the build.
 *   <li>Reference markers (ADR 0023) become labels when the caller asks, for current prose: on the parsed tree a
 *       marker's code keeps its name or path, and a placeholder that the parser never leaves in a document, a NUL,
 *       follows it; each fragment the renderer writes then gets the class's own constant label in place of each placeholder,
 *       before any template wraps it, and a NUL that is left fails the build. No document text enters a label.
 *   <li>{@link #outline} reads the same parsed tree as data, for checks of a document's structure such as a
 *       demonstration's sections; it renders nothing.
 *   <li>A deck is the same Markdown grouped by slide on the parsed tree, never by splitting its text: each level-2
 *       heading starts a slide, and the reserved level-3 notes heading starts the slide's notes, which a slide never
 *       shows. {@link #deck} reads the slides as data, for the deck's checks; {@link #renderDeck} renders each slide
 *       and its notes apart, under the same conditions, with the slides' anchors as the only in-page anchors.
 * </ul>
 */
public final class MarkdownRenderer {

    /** Deepest nesting of Markdown nodes that is rendered (real documents stay under 12). */
    public static final int MAX_DEPTH = 64;

    /**
     * A Mermaid fence's closed source (ADR 0024): the renderer's own constant markup in place of a placeholder that starts
     * the fence's text, chosen from its first line and from whether the unchecked-design label shows, and the end of the
     * disclosure in place of {@link #DIAGRAM_END}, which ends it. A placeholder is in the text of the code block, so a
     * fence in a list or a quote is wrapped as well; no document text enters the markup.
     */
    private enum DiagramSource {
        SEQUENCE("Sequence source", false),
        SEQUENCE_UNCHECKED("Sequence source", true),
        STATE("State diagram source", false),
        STATE_UNCHECKED("State diagram source", true),
        WORKFLOW("Workflow source", false),
        OTHER("Diagram source", false);

        private final String placeholder;
        private final String html;

        DiagramSource(String source, boolean unchecked) {
            this.placeholder = "\0diagram-" + name().toLowerCase(Locale.ROOT) + "\0";
            this.html = "<details class=\"diagram-source-block\">\n<summary>" + source + " \u2014 not rendered"
                    + (unchecked ? " <span class=\"badge badge-none diagram-unchecked\">Unchecked design</span>" : "")
                    + "</summary>\n<p class=\"diagram-note\">GitHub draws this diagram from its source; the text on this "
                    + "page says the same.</p>\n" + CODE_OPEN;
        }

        /** What a fence is, from its first line, as the closed source names it; unchecked only when the label shows. */
        static DiagramSource of(String firstLine, boolean unchecked) {
            if (firstLine.startsWith("sequenceDiagram")) {
                return unchecked ? SEQUENCE_UNCHECKED : SEQUENCE;
            }
            if (firstLine.startsWith("stateDiagram")) {
                return unchecked ? STATE_UNCHECKED : STATE;
            }
            return firstLine.startsWith("flowchart") || firstLine.startsWith("graph") ? WORKFLOW : OTHER;
        }
    }

    /** How the renderer opens a Mermaid fence's code block. */
    private static final String CODE_OPEN = "<pre><code class=\"language-" + MermaidFence.INFO + "\">";
    /** Ends a Mermaid fence's text; with the code block's end after it, it becomes the end of the disclosure. */
    private static final String DIAGRAM_END = "\0diagram-end\0";
    private static final String CODE_CLOSE = "</code></pre>";

    private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create());
    private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS)
            .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES).build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder().extensions(EXTENSIONS).escapeHtml(true)
            .sanitizeUrls(true).urlSanitizer(new SiteUrlSanitizer()).build();
    private static final SiteUrlSanitizer SANITIZER = new SiteUrlSanitizer();
    private static final String RESERVED_PREFIX = "site-";

    /** The hint at the start of a wide table's region; the stylesheet hides it once the table fits. */
    public static final String TABLE_HINT = "<p class=\"table-hint\">Scroll horizontally for all columns.</p>\n";

    /** A label's placeholder starts and ends with it; the parser never leaves one in a document. */
    private static final char NUL = '\0';
    private static final Pattern PLANNED_TEST = Pattern.compile("planned:test:("
            + ReferenceMarkers.TEST_NAME.pattern() + ")");
    private static final Pattern EXAMPLE_TEST = Pattern.compile("illustrative:test:("
            + ReferenceMarkers.PLACEHOLDER.pattern() + ")", Pattern.DOTALL);
    /** A capability or control id: lower-case words joined by hyphens, in two or more parts joined by dots. */
    private static final Pattern CAPABILITY = Pattern.compile(
            "cap:([a-z0-9]+(?:-[a-z0-9]+)*(?:\\.[a-z0-9]+(?:-[a-z0-9]+)*)+)");
    /** A path marker: its state, the word path, and the path, which {@link ReferenceMarkers#markedPath} reads. */
    private static final Pattern PATH_MARKER = Pattern.compile("(planned|absent|illustrative):path:(.*)",
            Pattern.DOTALL);

    /**
     * A label after a reference's name: the renderer's own constant markup, which no document text enters. A no-break
     * space holds it to the name, and it is an inline box, which that space holds where an inline-block would not; the
     * stylesheet's {@code reference-label} makes it so.
     */
    private enum Label {
        PLANNED("planned", "badge-planned"),
        EXAMPLE("example", "badge-neutral"),
        ABSENT("absent", "badge-none");

        private final String word;
        private final String placeholder;
        private final String html;

        Label(String word, String badge) {
            this.word = word;
            this.placeholder = "\0" + word + "\0";
            this.html = "&nbsp;<span class=\"badge " + badge + " reference-label\">" + word + "</span>";
        }
    }

    /** A reference marker read from inline code: the name the site shows, and the label after it, if any. */
    private record Marker(String name, @Nullable Label label) {
    }

    /** A deck's parsed top-level blocks: what stands before its first slide, and its slides. */
    private record DeckNodes(List<Node> front, List<SlideNodes> slides) {
    }

    /** One slide's parsed top-level blocks: its heading, its content and, apart, its notes heading and notes. */
    private record SlideNodes(Heading heading, List<Node> content, @Nullable Heading notesHeading, List<Node> notes) {
    }

    /** Counts a page's tables, so that each region is named by its number. */
    private static final class Tables {

        private int count;

        int next() {
            return ++count;
        }
    }

    private MarkdownRenderer() {
    }

    /** Renders a document with its reference markers as written; see the overload that takes {@link ReferenceMarkers}. */
    public static RenderedMarkdown render(String markdown, String source, LinkResolver links, boolean dropTitle) {
        return render(markdown, source, links, dropTitle, ReferenceMarkers.AS_WRITTEN);
    }

    /**
     * Renders a document; {@code dropTitle} leaves out its first level-1 heading, which the page template shows, and
     * {@code markers} says whether its reference markers are shown as labels (ADR 0023). Headings keep the anchors that
     * GitHub gives their source text; a section's title and a table region's name give a label in words.
     */
    public static RenderedMarkdown render(String markdown, String source, LinkResolver links, boolean dropTitle,
            ReferenceMarkers markers) {
        try {
            Node document = parse(markdown, source);
            Map<Node, Section> headings = headings(children(document), markers);
            Node title = dropTitle ? title(children(document)) : null;
            if (title != null) {
                title.unlink();
            }
            List<Node> blocks = children(document);
            Set<String> anchors = Set.copyOf(headings.values().stream()
                    .map(Section::id).toList());
            rewriteLinks(document, source, links, anchors);
            if (markers == ReferenceMarkers.LABELLED) {
                label(document);
            }
            closeDiagramSources(document, markers);
            StringBuilder html = new StringBuilder();
            List<Section> shown = new ArrayList<>();
            String section = "";
            Tables tables = new Tables();
            for (Node block : blocks) {
                if (block instanceof Heading heading) {
                    Section entry = id(heading, headings, source);
                    shown.add(entry);
                    section = entry.text();
                }
                String label = block instanceof TableBlock ? tableLabel(tables.next(), section) : "";
                html.append(block(block, headings, label, source));
            }
            return new RenderedMarkdown(html.toString(), shown);
        } catch (DocsException e) {
            throw e;
        } catch (StackOverflowError e) {
            throw new DocsException(source + ": the document is nested too deeply to render", e);
        } catch (RuntimeException e) {
            throw new DocsException(source + ": the document cannot be rendered: " + e, e);
        }
    }

    /**
     * A deck read as data, without rendering it: its parsed top-level blocks grouped by slide. Each level-2 heading
     * starts a slide, which runs to the next one; within a slide, the first level-3 heading whose text is
     * {@code notesHeading} starts its notes, which run to the slide's end. What stands before the first slide is the
     * deck's front. A {@code ##} line inside a fence is text, never a slide, and a notes heading at another level, or
     * with other words, stays in the slide's content, where the deck's check finds it.
     */
    public static DeckOutline deck(String markdown, String source, String notesHeading) {
        try {
            DeckNodes deck = group(children(parse(markdown, source)), notesHeading);
            List<SlideOutline> slides = new ArrayList<>();
            for (SlideNodes slide : deck.slides()) {
                String title = text(slide.heading()).strip();
                Heading notes = slide.notesHeading();
                slides.add(new SlideOutline(title, slug(title), block(slide.heading()), blocks(slide.content()),
                        notes == null ? null : block(notes), blocks(slide.notes())));
            }
            return new DeckOutline(blocks(deck.front()), slides);
        } catch (StackOverflowError e) {
            throw new DocsException(source + ": the document is nested too deeply to read", e);
        }
    }

    /**
     * Renders a deck's slides and, apart, their notes, under the conditions of {@link #render}: raw HTML escaped, URLs
     * sanitised, links resolved on the parsed nodes, images failing the build. The deck's title and anything else before
     * its first slide are left out, and so are the notes headings. A deck's pages have no anchor but its slides', so an
     * in-page link must name a slide. Tables are numbered on each page, the slides' and the notes', and named as a
     * document's are, with the slide's title as their section.
     *
     * <p>Each slide's anchor is taken from its source heading before any link is rewritten, the anchor {@link #deck}
     * gives, which the deck's check and the site map read: rewriting can change a heading's text, such as the
     * repository's address shown as words, and so its slug. What the slide shows, its title as text and as HTML, comes
     * from the rewritten heading.
     */
    public static RenderedDeck renderDeck(String markdown, String source, LinkResolver links, String notesHeading) {
        try {
            Node document = parse(markdown, source);
            List<Node> blocks = children(document);
            Map<Node, Section> headings = headings(blocks);
            DeckNodes deck = group(blocks, notesHeading);
            List<String> ids = new ArrayList<>();
            for (SlideNodes slide : deck.slides()) {
                ids.add(slug(text(slide.heading()).strip()));
            }
            rewriteLinks(document, source, links, Set.copyOf(ids));
            closeDiagramSources(document, ReferenceMarkers.AS_WRITTEN);
            Tables slideTables = new Tables();
            Tables noteTables = new Tables();
            List<RenderedSlide> slides = new ArrayList<>();
            for (int i = 0; i < deck.slides().size(); i++) {
                SlideNodes slide = deck.slides().get(i);
                String title = text(slide.heading()).strip();
                StringBuilder titleHtml = new StringBuilder();
                for (Node child : children(slide.heading())) {
                    titleHtml.append(rendered(child, source));
                }
                slides.add(new RenderedSlide(ids.get(i), title, titleHtml.toString(),
                        html(slide.content(), headings, title, slideTables, source), slide.notesHeading() != null,
                        html(slide.notes(), headings, title, noteTables, source)));
            }
            return new RenderedDeck(slides);
        } catch (DocsException e) {
            throw e;
        } catch (StackOverflowError e) {
            throw new DocsException(source + ": the deck is nested too deeply to render", e);
        } catch (RuntimeException e) {
            throw new DocsException(source + ": the deck cannot be rendered: " + e, e);
        }
    }

    /** A deck's top-level blocks, grouped by slide; the parser has already told headings from text in fences. */
    private static DeckNodes group(List<Node> blocks, String notesHeading) {
        List<Node> front = new ArrayList<>();
        List<SlideNodes> slides = new ArrayList<>();
        Heading heading = null;
        List<Node> content = new ArrayList<>();
        Heading notes = null;
        List<Node> noteBlocks = new ArrayList<>();
        for (Node block : blocks) {
            if (block instanceof Heading next && next.getLevel() == 2) {
                if (heading != null) {
                    slides.add(new SlideNodes(heading, content, notes, noteBlocks));
                }
                heading = next;
                content = new ArrayList<>();
                notes = null;
                noteBlocks = new ArrayList<>();
            } else if (heading == null) {
                front.add(block);
            } else if (notes == null && block instanceof Heading start && start.getLevel() == 3
                    && text(start).strip().equals(notesHeading)) {
                notes = start;
            } else if (notes != null) {
                noteBlocks.add(block);
            } else {
                content.add(block);
            }
        }
        if (heading != null) {
            slides.add(new SlideNodes(heading, content, notes, noteBlocks));
        }
        return new DeckNodes(front, slides);
    }

    /** One slide's or its notes' blocks as HTML; each table is named by its number on the page and the slide. */
    private static String html(List<Node> blocks, Map<Node, Section> headings, String slide, Tables tables,
            String source) {
        StringBuilder html = new StringBuilder();
        for (Node block : blocks) {
            html.append(block(block, headings, block instanceof TableBlock ? tableLabel(tables.next(), slide) : "",
                    source));
        }
        return html.toString();
    }

    /** A table's region in words: "Table 2 in Steps, scrolls sideways". */
    private static String tableLabel(int number, String section) {
        return "Table " + number + (section.isEmpty() ? "" : " in " + section) + ", scrolls sideways";
    }

    /**
     * One top-level block as HTML: a heading with its ID, a table in a region named {@code tableLabel}, anything else as
     * the renderer writes it, a Mermaid fence as its closed source ({@link #closeDiagramSources}).
     */
    private static String block(Node block, Map<Node, Section> headings, String tableLabel, String source) {
        StringBuilder html = new StringBuilder();
        switch (block) {
            case Heading heading -> {
                Section entry = id(heading, headings, source);
                html.append("<h").append(entry.level()).append(" id=\"").append(Html.escape(entry.id())).append("\">");
                for (Node child : children(heading)) {
                    html.append(rendered(child, source));
                }
                html.append("</h").append(entry.level()).append(">\n");
            }
            case TableBlock table -> {
                // A label never breaks from its name, so a table that holds one keeps a readable width and scrolls.
                boolean labelled = holdsLabel(table);
                html.append("<div class=\"").append(labelled ? "table-scroll table-wide-references" : "table-scroll")
                        .append("\" role=\"region\" tabindex=\"0\" aria-label=\"").append(Html.escape(tableLabel))
                        .append("\">\n").append(labelled ? TABLE_HINT : "").append(rendered(table, source))
                        .append("</div>\n");
            }
            default -> html.append(rendered(block, source));
        }
        return html.toString();
    }

    /** A node as the HTML renderer writes it, with the labels in place; every fragment of this class passes here. */
    private static String rendered(Node node, String source) {
        return placeLabels(RENDERER.render(node), source);
    }

    /**
     * Shows each reference marker of a document as the site does (ADR 0023): the code keeps the name alone, and a
     * label's placeholder follows it, which {@link #placeLabels} turns into the label once the renderer has escaped
     * everything else. Inline code that is no marker of the grammar keeps its text.
     */
    private static void label(Node document) {
        List<Code> spans = new ArrayList<>();
        Deque<Node> pending = new ArrayDeque<>();
        pending.push(document);
        while (!pending.isEmpty()) {
            Node node = pending.pop();
            if (node instanceof Code code) {
                spans.add(code);
            }
            for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
                pending.push(child);
            }
        }
        for (Code code : spans) {
            Marker marker = marker(code.getLiteral());
            if (marker != null) {
                code.setLiteral(marker.name());
                Label label = marker.label();
                if (label != null) {
                    code.insertAfter(new Text(label.placeholder));
                }
            }
        }
    }

    /** Whether a block holds a label's placeholder; only {@link #label} writes one. */
    private static boolean holdsLabel(Node block) {
        Deque<Node> pending = new ArrayDeque<>();
        pending.push(block);
        while (!pending.isEmpty()) {
            Node node = pending.pop();
            if (node instanceof Text text && text.getLiteral().indexOf(NUL) >= 0) {
                return true;
            }
            for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
                pending.push(child);
            }
        }
        return false;
    }

    /**
     * Puts each label's constant markup where its placeholder stands in HTML that the renderer wrote, and each Mermaid
     * fence's closed source around its code block. Only {@link #label} and {@link #closeDiagramSources} write a NUL:
     * CommonMark replaces every NUL of the source, character references included, with U+FFFD. A NUL that is left is
     * therefore no placeholder of this class, and the build fails.
     */
    static String placeLabels(String html, String source) {
        if (html.indexOf(NUL) < 0) {
            return html;
        }
        String placed = html;
        for (Label label : Label.values()) {
            placed = placed.replace(label.placeholder, label.html);
        }
        for (DiagramSource diagram : DiagramSource.values()) {
            placed = placed.replace(CODE_OPEN + diagram.placeholder, diagram.html);
        }
        placed = placed.replace(DIAGRAM_END + CODE_CLOSE, CODE_CLOSE + "\n</details>");
        if (placed.indexOf(NUL) >= 0) {
            throw new DocsException(source + ": the rendered HTML holds a placeholder that is no label");
        }
        return placed;
    }

    /** A reference marker of inline code, or null when the code is none: the name the site shows, and its label. */
    private static @Nullable Marker marker(String literal) {
        Matcher planned = PLANNED_TEST.matcher(literal);
        if (planned.matches()) {
            return new Marker(planned.group(1), Label.PLANNED);
        }
        Matcher example = EXAMPLE_TEST.matcher(literal);
        if (example.matches()) {
            return new Marker(example.group(1), Label.EXAMPLE);
        }
        Matcher capability = CAPABILITY.matcher(literal);
        if (capability.matches()) {
            return new Marker(capability.group(1), null);
        }
        Matcher path = PATH_MARKER.matcher(literal);
        if (path.matches() && ReferenceMarkers.markedPath(path.group(1), path.group(2)) != null) {
            Label label = switch (path.group(1)) {
                case "planned" -> Label.PLANNED;
                case "absent" -> Label.ABSENT;
                default -> Label.EXAMPLE;
            };
            return new Marker(path.group(2), label);
        }
        return null;
    }

    /** Inline code in plain words, for a section's title or a region's name: a marker as its name and its label. */
    private static String words(String literal) {
        Marker marker = marker(literal);
        if (marker == null) {
            return literal;
        }
        Label label = marker.label();
        return label == null ? marker.name() : marker.name() + " (" + label.word + ")";
    }

    private static Section id(Heading heading, Map<Node, Section> headings, String source) {
        Section entry = headings.get(heading);
        if (entry == null) {
            throw new DocsException(source + ": a heading has no ID");
        }
        return entry;
    }

    /**
     * Every inline code span of a document, in document order, with the line it starts on. Code blocks, fenced or
     * indented, hold no inline code, so nothing in them is returned.
     */
    public static List<InlineCode> inlineCode(String markdown, String source) {
        try {
            List<InlineCode> spans = new ArrayList<>();
            Deque<Node> pending = new ArrayDeque<>();
            pending.push(parse(markdown, source));
            while (!pending.isEmpty()) {
                Node node = pending.pop();
                if (node instanceof Code code) {
                    spans.add(new InlineCode(line(code), code.getLiteral()));
                    continue;
                }
                List<Node> children = children(node);
                for (int i = children.size() - 1; i >= 0; i--) {
                    pending.push(children.get(i));
                }
            }
            return List.copyOf(spans);
        } catch (StackOverflowError e) {
            throw new DocsException(source + ": the document is nested too deeply to read", e);
        }
    }

    /**
     * The Mermaid fences of a document at any depth, in document order, and the lines of the unchecked-design labels
     * that no sequence or state diagram follows (ADR 0024), read without rendering. The renderer shows the label by the
     * same rule ({@link #closeDiagramSources}).
     */
    public static DiagramOutline diagrams(String markdown, String source) {
        try {
            List<MermaidFence> fences = new ArrayList<>();
            List<Integer> strayLabels = new ArrayList<>();
            Deque<Node> pending = new ArrayDeque<>();
            pending.push(parse(markdown, source));
            while (!pending.isEmpty()) {
                Node node = pending.pop();
                if (node instanceof FencedCodeBlock code && MermaidFence.mermaid(code.getInfo())) {
                    fences.add(new MermaidFence(line(code), code.getInfo().strip(),
                            MermaidFence.firstLineOf(code.getLiteral()), uncheckedLabel(code.getPrevious())));
                } else if (uncheckedLabel(node) && !behaviouralFence(node.getNext())) {
                    strayLabels.add(line(node));
                }
                List<Node> children = children(node);
                for (int i = children.size() - 1; i >= 0; i--) {
                    pending.push(children.get(i));
                }
            }
            return new DiagramOutline(fences, strayLabels);
        } catch (StackOverflowError e) {
            throw new DocsException(source + ": the document is nested too deeply to read", e);
        }
    }

    /**
     * Whether a block is the unchecked-design label (ADR 0024): a paragraph whose first inline is strong emphasis that
     * holds exactly {@link MermaidFence#LABEL} as plain text, which GitHub shows in bold.
     */
    private static boolean uncheckedLabel(@Nullable Node block) {
        return block instanceof Paragraph paragraph && paragraph.getFirstChild() instanceof StrongEmphasis strong
                && strong.getFirstChild() instanceof Text text && text.getNext() == null
                && text.getLiteral().equals(MermaidFence.LABEL);
    }

    /** Whether a block is a Mermaid fence, in any letter case, whose first line names a sequence or state diagram. */
    private static boolean behaviouralFence(@Nullable Node block) {
        return block instanceof FencedCodeBlock code && MermaidFence.mermaid(code.getInfo())
                && MermaidFence.Type.of(MermaidFence.firstLineOf(code.getLiteral()))
                        .map(MermaidFence.Type::behavioural).orElse(false);
    }

    /**
     * Puts the closed source's placeholders into the text of every fence whose info string is {@code mermaid}, at any
     * depth (ADR 0024). A sequence or state diagram that the label right before it disclaims gets the placeholder with
     * the unchecked-design label when the document's labels show, which the site asks for current prose only; the
     * documentation check holds every such diagram of current prose to the label, so a label is one the check proved.
     */
    private static void closeDiagramSources(Node document, ReferenceMarkers markers) {
        Deque<Node> pending = new ArrayDeque<>();
        pending.push(document);
        while (!pending.isEmpty()) {
            Node node = pending.pop();
            if (node instanceof FencedCodeBlock code && MermaidFence.INFO.equals(code.getInfo().strip())) {
                boolean unchecked = markers == ReferenceMarkers.LABELLED && behaviouralFence(code)
                        && uncheckedLabel(code.getPrevious());
                DiagramSource diagram = DiagramSource.of(MermaidFence.firstLineOf(code.getLiteral()), unchecked);
                code.setLiteral(diagram.placeholder + code.getLiteral() + DIAGRAM_END);
            }
            for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
                pending.push(child);
            }
        }
    }

    /** The line a node starts on, counted from 1: its own source span, else its nearest ancestor's; 0 when none. */
    private static int line(Node node) {
        for (Node at = node; at != null; at = at.getParent()) {
            List<SourceSpan> spans = at.getSourceSpans();
            if (!spans.isEmpty()) {
                return spans.get(0).getLineIndex() + 1;
            }
        }
        return 0;
    }

    /** The headings of a document with their anchor IDs, title included, without rendering it. */
    public static List<Section> headings(String markdown, String source) {
        try {
            return List.copyOf(headings(children(parse(markdown, source))).values());
        } catch (StackOverflowError e) {
            throw new DocsException(source + ": the document is nested too deeply to read", e);
        }
    }

    /**
     * Every table of a text, at any depth, as the site's parser reads and shows it: the line it starts on and its rows,
     * the header first, each with the line it stands on and its cells. A table without outer pipes is a table too, an
     * escaped pipe is text in a cell, and a code block's text is no table.
     */
    public static List<MarkdownTable> tables(String markdown, String source) {
        try {
            List<MarkdownTable> tables = new ArrayList<>();
            Deque<Node> pending = new ArrayDeque<>();
            pending.push(parse(markdown, source));
            while (!pending.isEmpty()) {
                Node node = pending.pop();
                if (node instanceof TableBlock table) {
                    List<MarkdownTable.Row> rows = new ArrayList<>();
                    for (Node part = table.getFirstChild(); part != null; part = part.getNext()) {
                        for (Node row = part.getFirstChild(); row != null; row = row.getNext()) {
                            List<String> cells = new ArrayList<>();
                            for (Node cell = row.getFirstChild(); cell != null; cell = cell.getNext()) {
                                cells.add(inline(cell).strip());
                            }
                            rows.add(new MarkdownTable.Row(line(row) - 1, cells));
                        }
                    }
                    tables.add(new MarkdownTable(line(table) - 1, rows));
                }
                pushChildren(pending, node);
            }
            return List.copyOf(tables);
        } catch (StackOverflowError e) {
            throw new DocsException(source + ": the document is nested too deeply to read", e);
        }
    }

    /**
     * A cell's text as the site shows it, each code span in backticks; raw HTML, which the site escapes, as its text,
     * and line breaks as spaces.
     */
    private static String inline(Node node) {
        StringBuilder out = new StringBuilder();
        Deque<Node> pending = new ArrayDeque<>();
        pending.push(node);
        while (!pending.isEmpty()) {
            Node current = pending.pop();
            switch (current) {
                case Text text -> out.append(text.getLiteral());
                case Code code -> out.append('`').append(code.getLiteral()).append('`');
                case HtmlInline html -> out.append(html.getLiteral());
                case SoftLineBreak ignored -> out.append(' ');
                case HardLineBreak ignored -> out.append(' ');
                default -> {
                    List<Node> children = children(current);
                    for (int i = children.size() - 1; i >= 0; i--) {
                        pending.push(children.get(i));
                    }
                }
            }
        }
        return out.toString();
    }

    /**
     * The text of a block that the site shows as text, at any depth: raw HTML, which the site escapes, included, and
     * without code spans, code blocks and tables.
     */
    private static String prose(Node node) {
        StringBuilder out = new StringBuilder();
        Deque<Node> pending = new ArrayDeque<>();
        pending.push(node);
        while (!pending.isEmpty()) {
            Node current = pending.pop();
            switch (current) {
                case Text text -> out.append(text.getLiteral());
                case HtmlInline html -> out.append(html.getLiteral());
                case HtmlBlock html -> out.append(html.getLiteral());
                case Code ignored -> out.append(' ');
                case FencedCodeBlock ignored -> out.append('\n');
                case IndentedCodeBlock ignored -> out.append('\n');
                case TableBlock ignored -> out.append('\n');
                case SoftLineBreak ignored -> out.append('\n');
                case HardLineBreak ignored -> out.append('\n');
                default -> {
                    List<Node> children = children(current);
                    for (int i = children.size() - 1; i >= 0; i--) {
                        pending.push(children.get(i));
                    }
                }
            }
        }
        return out.toString();
    }

    /**
     * The text of every heading inside a quote, a list item or another container, which {@link #blockLines} does not
     * show because it is no top-level block.
     */
    public static List<String> nestedHeadings(String markdown, String source) {
        try {
            List<String> texts = new ArrayList<>();
            Deque<Node> pending = new ArrayDeque<>();
            for (Node top : children(parse(markdown, source))) {
                pushChildren(pending, top);
            }
            while (!pending.isEmpty()) {
                Node node = pending.pop();
                if (node instanceof Heading heading) {
                    texts.add(text(heading).strip());
                }
                pushChildren(pending, node);
            }
            return List.copyOf(texts);
        } catch (StackOverflowError e) {
            throw new DocsException(source + ": the document is nested too deeply to read", e);
        }
    }

    /**
     * The top-level blocks of a text with the lines each spans, without rendering it: a heading, written with hashes or
     * underlined, with its level and text, a table, or another block.
     */
    public static List<BlockLines> blockLines(String markdown, String source) {
        try {
            List<BlockLines> blocks = new ArrayList<>();
            for (Node node : children(parse(markdown, source))) {
                List<SourceSpan> spans = node.getSourceSpans();
                int first = spans.isEmpty() ? -1 : spans.getFirst().getLineIndex();
                int last = spans.isEmpty() ? -1 : spans.getLast().getLineIndex();
                String prose = prose(node);
                blocks.add(switch (node) {
                    case Heading heading -> new BlockLines(BlockLines.Kind.HEADING, heading.getLevel(),
                            text(heading).strip(), first, last, prose);
                    case TableBlock ignored -> new BlockLines(BlockLines.Kind.TABLE, 0, "", first, last, prose);
                    case FencedCodeBlock ignored -> new BlockLines(BlockLines.Kind.CODE, 0, "", first, last, prose);
                    case IndentedCodeBlock ignored -> new BlockLines(BlockLines.Kind.CODE, 0, "", first, last, prose);
                    default -> new BlockLines(BlockLines.Kind.OTHER, 0, "", first, last, prose);
                });
            }
            return List.copyOf(blocks);
        } catch (StackOverflowError e) {
            throw new DocsException(source + ": the document is nested too deeply to read", e);
        }
    }

    /**
     * The top-level blocks of a document as data, without rendering it: what a heading is only when the parser reads
     * one (a {@code ##} line inside a fence is text), each code block's info string and text, and every other block's
     * text, links, images, raw HTML and nested code blocks.
     */
    public static List<MarkdownBlock> outline(String markdown, String source) {
        try {
            return blocks(children(parse(markdown, source)));
        } catch (StackOverflowError e) {
            throw new DocsException(source + ": the document is nested too deeply to read", e);
        }
    }

    private static List<MarkdownBlock> blocks(List<Node> nodes) {
        List<MarkdownBlock> blocks = new ArrayList<>();
        for (Node node : nodes) {
            blocks.add(block(node));
        }
        return List.copyOf(blocks);
    }

    /** One top-level block as data. */
    private static MarkdownBlock block(Node block) {
        return switch (block) {
            case FencedCodeBlock code -> new MarkdownBlock(MarkdownBlock.Kind.FENCED_CODE, 0, code.getInfo().strip(),
                    code.getLiteral(), "", List.of(), List.of(), List.of(), List.of(), List.of());
            case IndentedCodeBlock code -> new MarkdownBlock(MarkdownBlock.Kind.INDENTED_CODE, 0, "",
                    code.getLiteral(), "", List.of(), List.of(), List.of(), List.of(), List.of());
            default -> walk(block);
        };
    }

    /** A heading or another block, read in document order without recursion. */
    private static MarkdownBlock walk(Node block) {
        StringBuilder text = new StringBuilder();
        StringBuilder prose = new StringBuilder();
        List<String> links = new ArrayList<>();
        List<String> images = new ArrayList<>();
        List<String> html = new ArrayList<>();
        List<String> code = new ArrayList<>();
        List<String> spans = new ArrayList<>();
        if (block instanceof HtmlBlock raw) {
            html.add(raw.getLiteral().strip());
        }
        Deque<Node> pending = new ArrayDeque<>();
        pushChildren(pending, block);
        while (!pending.isEmpty()) {
            Node node = pending.pop();
            switch (node) {
                case Text literal -> {
                    text.append(literal.getLiteral());
                    prose.append(literal.getLiteral());
                }
                case Code span -> {
                    text.append(span.getLiteral());
                    spans.add(span.getLiteral());
                }
                case SoftLineBreak ignored -> newLine(text, prose);
                case HardLineBreak ignored -> newLine(text, prose);
                case HtmlInline inline -> html.add(inline.getLiteral());
                case HtmlBlock raw -> html.add(raw.getLiteral().strip());
                case FencedCodeBlock nested -> code.add(nested.getLiteral());
                case IndentedCodeBlock nested -> code.add(nested.getLiteral());
                case Link link -> links.add(link.getDestination());
                case Image image -> images.add(image.getDestination());
                case Block ignored -> newLine(text, prose);
                case CustomNode ignored -> newLine(text, prose);
                default -> {
                    // Inline containers such as emphasis carry their children only.
                }
            }
            pushChildren(pending, node);
        }
        return block instanceof Heading heading
                ? new MarkdownBlock(MarkdownBlock.Kind.HEADING, heading.getLevel(), "", text.toString().strip(),
                        prose.toString().strip(), links, images, html, code, spans)
                : new MarkdownBlock(MarkdownBlock.Kind.OTHER, 0, "", text.toString().strip(), prose.toString().strip(),
                        links, images, html, code, spans);
    }

    /** Pushes a node's children so that they are taken in document order. */
    private static void pushChildren(Deque<Node> pending, Node node) {
        List<Node> children = children(node);
        for (int i = children.size() - 1; i >= 0; i--) {
            pending.push(children.get(i));
        }
    }

    private static void newLine(StringBuilder text, StringBuilder prose) {
        text.append('\n');
        prose.append('\n');
    }

    private static Node parse(String markdown, String source) {
        Node document = PARSER.parse(markdown);
        Deque<Node> pending = new ArrayDeque<>();
        Deque<Integer> depths = new ArrayDeque<>();
        pending.push(document);
        depths.push(0);
        while (!pending.isEmpty()) {
            Node node = pending.pop();
            int depth = depths.pop();
            if (depth > MAX_DEPTH) {
                throw new DocsException(source + ": Markdown nested deeper than " + MAX_DEPTH + " levels");
            }
            for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
                pending.push(child);
                depths.push(depth + 1);
            }
        }
        return document;
    }

    /** Top-level headings in order, with GitHub's IDs: unique, lower case, punctuation dropped, spaces as hyphens. */
    private static Map<Node, Section> headings(List<Node> blocks) {
        return headings(blocks, ReferenceMarkers.AS_WRITTEN);
    }

    /**
     * Top-level headings in order, with GitHub's IDs from their source text; with labels, the text a section's title
     * shows gives each marker as its name and its label in words.
     */
    private static Map<Node, Section> headings(List<Node> blocks, ReferenceMarkers markers) {
        Map<Node, Section> out = new LinkedHashMap<>();
        Map<String, Integer> seen = new HashMap<>();
        for (Node block : blocks) {
            if (block instanceof Heading heading) {
                String text = text(heading).strip();
                String slug = slug(text);
                int count = seen.merge(slug, 1, Integer::sum);
                String id = count == 1 ? slug : slug + "-" + (count - 1);
                String shown = markers == ReferenceMarkers.LABELLED ? text(heading, true).strip() : text;
                out.put(heading, new Section(heading.getLevel(), shown, id));
            }
        }
        return out;
    }

    /**
     * GitHub's anchor of a heading's text, before the suffix a repeated one gets: lower case, punctuation dropped,
     * spaces as hyphens; a slug that would start with the site's reserved {@code site-} starts with {@code doc-site-}.
     */
    public static String slug(String text) {
        String slug = text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{M}\\p{N}\\p{Pc}\\- ]", "")
                .replace(' ', '-');
        if (slug.isEmpty()) {
            return "section";
        }
        return slug.startsWith(RESERVED_PREFIX) ? "doc-" + slug : slug;
    }

    private static @Nullable Node title(List<Node> blocks) {
        for (Node block : blocks) {
            if (block instanceof Heading heading) {
                return heading.getLevel() == 1 ? heading : null;
            }
        }
        return null;
    }

    /** Applies the resolver to every link, checks in-page anchors, and fails on images and unsafe destinations. */
    private static void rewriteLinks(Node document, String source, LinkResolver links, Set<String> anchors) {
        List<Node> found = new ArrayList<>();
        Deque<Node> pending = new ArrayDeque<>();
        pending.push(document);
        while (!pending.isEmpty()) {
            Node node = pending.pop();
            if (node instanceof Link || node instanceof Image) {
                found.add(node);
            }
            for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
                pending.push(child);
            }
        }
        for (Node node : found) {
            if (node instanceof Image image) {
                throw new DocsException(source + ": image '" + image.getDestination() + "': the site publishes no "
                        + "images, and a remote one would be a request to another origin");
            }
            Link link = (Link) node;
            String destination = link.getDestination();
            if (destination.startsWith("#")) {
                if (!anchors.contains(destination.substring(1))) {
                    throw new DocsException(source + ": link to '" + destination + "': no heading has that anchor");
                }
                continue;
            }
            switch (links.resolve(destination)) {
                case Keep ignored -> {
                    // External or already site-relative.
                }
                case Rewrite rewrite -> link.setDestination(rewrite.href());
                case Unlink unlink -> {
                    unlink(link, unlink.text());
                    continue;
                }
                case Reject reject -> throw new DocsException(source + ": link to '" + destination + "': "
                        + reject.reason());
            }
            if (SANITIZER.sanitizeLinkUrl(link.getDestination()).isEmpty()) {
                throw new DocsException(source + ": link to '" + destination + "' is not an http, https, mailto, "
                        + "relative or fragment URL");
            }
        }
    }

    /** Replaces a link with its text; a link whose text is its own URL gets {@code replacement} instead. */
    private static void unlink(Link link, String replacement) {
        boolean urlAsText = text(link).strip().equals(link.getDestination())
                || ("mailto:" + text(link).strip()).equals(link.getDestination());
        if (urlAsText && !replacement.isEmpty()) {
            link.insertBefore(new Text(replacement));
        } else {
            for (Node child : children(link)) {
                link.insertBefore(child);
            }
        }
        link.unlink();
    }

    private static List<Node> children(Node parent) {
        List<Node> children = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            children.add(child);
        }
        return children;
    }

    /** The plain text of a node: text and code literals, with line breaks as spaces. */
    private static String text(Node node) {
        return text(node, false);
    }

    /** The plain text of a node; with {@code labelled}, a reference marker in code reads as its name and label. */
    private static String text(Node node, boolean labelled) {
        StringBuilder out = new StringBuilder();
        Deque<Node> pending = new ArrayDeque<>();
        pending.push(node);
        while (!pending.isEmpty()) {
            Node current = pending.pop();
            switch (current) {
                case Text text -> out.append(text.getLiteral());
                case Code code -> out.append(labelled ? words(code.getLiteral()) : code.getLiteral());
                case SoftLineBreak ignored -> out.append(' ');
                case HardLineBreak ignored -> out.append(' ');
                default -> {
                    List<Node> children = children(current);
                    for (int i = children.size() - 1; i >= 0; i--) {
                        pending.push(children.get(i));
                    }
                }
            }
        }
        return out.toString();
    }
}
