package io.github.stigjohnny.docs.markdown;

import java.util.List;

/**
 * One top-level block of a Markdown document as data, for checks of its structure such as a demonstration's sections
 * ({@link MarkdownRenderer#outline}). Nothing in it is rendered.
 *
 * @param level a heading's level; 0 for every other block
 * @param info a fenced code block's info string, stripped; empty for every other block
 * @param text a code block's literal text; for any other block its text with code spans, one line per line break and
 *     per nested block
 * @param prose the text without code spans and without code blocks: where an unfilled placeholder shows as text;
 *     empty for a code block
 * @param links the destinations of the block's links, in order
 * @param images the destinations of the block's images, in order
 * @param html the block's raw HTML, inline or as a block, in order
 * @param code the literal text of every code block nested in the block, such as a fence inside a list item
 */
public record MarkdownBlock(Kind kind, int level, String info, String text, String prose, List<String> links,
        List<String> images, List<String> html, List<String> code) {

    /** What a top-level block is. */
    public enum Kind {
        HEADING,
        FENCED_CODE,
        INDENTED_CODE,
        /** A paragraph, list, quote, table, thematic break or HTML block. */
        OTHER
    }

    public MarkdownBlock {
        links = List.copyOf(links);
        images = List.copyOf(images);
        html = List.copyOf(html);
        code = List.copyOf(code);
    }

    /** Whether the block holds anything to read: a heading never does, a code block its text, any other block text. */
    public boolean hasContent() {
        return kind != Kind.HEADING && !text.isBlank();
    }
}
