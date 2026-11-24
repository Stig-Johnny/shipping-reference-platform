package io.github.stigjohnny.docs.markdown;

import java.util.List;

/**
 * A table of a Markdown text, at any depth, as the site's parser reads and shows it ({@link MarkdownRenderer#tables}):
 * the line it starts on, counted from 0, and its rows, the header first. A cell is its text as the site shows it, with
 * each code span in backticks, so that a reader can tell a code span from plain text.
 */
public record MarkdownTable(int line, List<Row> rows) {

    public MarkdownTable {
        rows = List.copyOf(rows);
    }

    /**
     * One row: the line it stands on, counted from 0, and its cells, as many as the header has, each also as the text
     * the site shows, with no code delimiters and with every other character, a literal backtick too.
     */
    public record Row(int line, List<String> cells, List<String> texts) {

        public Row {
            cells = List.copyOf(cells);
            texts = List.copyOf(texts);
        }
    }
}
