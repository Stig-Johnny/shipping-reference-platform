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
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
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
 *       {@code _} and {@code -}; tables are wrapped in labelled, keyboard-scrollable regions; Mermaid fences are shown
 *       as source, because only the generator's validated diagrams are ever rendered.
 *   <li>CommonMark 0.29.0 has no nesting limits, so the tree is measured before rendering and anything deeper than
 *       {@link #MAX_DEPTH} fails; any exception or error, {@link StackOverflowError} included, fails the build.
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

    /** What a hand-written diagram is, from its first line, for the label of its closed source. */
    private static String sourceLabel(FencedCodeBlock code) {
        String first = code.getLiteral().strip().lines().findFirst().orElse("").strip();
        if (first.startsWith("sequenceDiagram")) {
            return "Sequence source";
        }
        if (first.startsWith("stateDiagram")) {
            return "State diagram source";
        }
        return first.startsWith("flowchart") || first.startsWith("graph") ? "Workflow source" : "Diagram source";
    }

    private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create());
    private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder().extensions(EXTENSIONS).escapeHtml(true)
            .sanitizeUrls(true).urlSanitizer(new SiteUrlSanitizer()).build();
    private static final SiteUrlSanitizer SANITIZER = new SiteUrlSanitizer();
    private static final String RESERVED_PREFIX = "site-";

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

    /** Renders a document; {@code dropTitle} leaves out its first level-1 heading, which the page template shows. */
    public static RenderedMarkdown render(String markdown, String source, LinkResolver links, boolean dropTitle) {
        try {
            Node document = parse(markdown, source);
            Map<Node, Section> headings = headings(children(document));
            Node title = dropTitle ? title(children(document)) : null;
            if (title != null) {
                title.unlink();
            }
            List<Node> blocks = children(document);
            Set<String> anchors = Set.copyOf(headings.values().stream()
                    .map(Section::id).toList());
            rewriteLinks(document, source, links, anchors);
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
            Tables slideTables = new Tables();
            Tables noteTables = new Tables();
            List<RenderedSlide> slides = new ArrayList<>();
            for (int i = 0; i < deck.slides().size(); i++) {
                SlideNodes slide = deck.slides().get(i);
                String title = text(slide.heading()).strip();
                StringBuilder titleHtml = new StringBuilder();
                for (Node child : children(slide.heading())) {
                    titleHtml.append(RENDERER.render(child));
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
     * One top-level block as HTML: a heading with its ID, a table in a region named {@code tableLabel}, a Mermaid fence
     * as its closed source, anything else as the renderer writes it.
     */
    private static String block(Node block, Map<Node, Section> headings, String tableLabel, String source) {
        StringBuilder html = new StringBuilder();
        switch (block) {
            case Heading heading -> {
                Section entry = id(heading, headings, source);
                html.append("<h").append(entry.level()).append(" id=\"").append(Html.escape(entry.id())).append("\">");
                for (Node child : children(heading)) {
                    html.append(RENDERER.render(child));
                }
                html.append("</h").append(entry.level()).append(">\n");
            }
            case TableBlock table -> html.append("<div class=\"table-scroll\" role=\"region\" tabindex=\"0\" "
                    + "aria-label=\"").append(Html.escape(tableLabel)).append("\">\n").append(RENDERER.render(table))
                    .append("</div>\n");
            case FencedCodeBlock code when "mermaid".equals(code.getInfo().strip()) -> html
                    .append("<details class=\"diagram-source-block\">\n<summary>").append(sourceLabel(code))
                    .append(" \u2014 not rendered</summary>\n<p class=\"diagram-note\">GitHub draws this "
                            + "diagram from its source; the text on this page says the same.</p>\n")
                    .append(RENDERER.render(code)).append("</details>\n");
            default -> html.append(RENDERER.render(block));
        }
        return html.toString();
    }

    private static Section id(Heading heading, Map<Node, Section> headings, String source) {
        Section entry = headings.get(heading);
        if (entry == null) {
            throw new DocsException(source + ": a heading has no ID");
        }
        return entry;
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
        Map<Node, Section> out = new LinkedHashMap<>();
        Map<String, Integer> seen = new HashMap<>();
        for (Node block : blocks) {
            if (block instanceof Heading heading) {
                String text = text(heading).strip();
                String slug = slug(text);
                int count = seen.merge(slug, 1, Integer::sum);
                String id = count == 1 ? slug : slug + "-" + (count - 1);
                out.put(heading, new Section(heading.getLevel(), text, id));
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
        StringBuilder out = new StringBuilder();
        Deque<Node> pending = new ArrayDeque<>();
        pending.push(node);
        while (!pending.isEmpty()) {
            Node current = pending.pop();
            switch (current) {
                case Text text -> out.append(text.getLiteral());
                case Code code -> out.append(code.getLiteral());
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
