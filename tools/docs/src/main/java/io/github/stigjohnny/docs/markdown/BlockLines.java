package io.github.stigjohnny.docs.markdown;

/**
 * One top-level block of a Markdown text and the lines it spans, counted from 0 and inclusive, as the site's parser
 * reads it ({@link MarkdownRenderer#blockLines}): a heading of either form with its level and text, a table, or another
 * block. A reader that needs a section's bounds or a table's rows takes them from here, so that it agrees with what the
 * site shows.
 *
 * @param level a heading's level; 0 for every other block
 * @param text a heading's text; empty for every other block
 */
public record BlockLines(Kind kind, int level, String text, int firstLine, int lastLine) {

    /** What a top-level block is. */
    public enum Kind {
        HEADING,
        TABLE,
        CODE,
        OTHER
    }
}
