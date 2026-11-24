package io.github.stigjohnny.docs.markdown;

import java.util.List;

/**
 * A table of a Markdown text, at any depth, as the site's parser reads it ({@link MarkdownRenderer#tables}): the line
 * it starts on, counted from 0, and the text of its header's cells, which the site shows as its column names.
 */
public record TableHeader(int line, List<String> cells) {

    public TableHeader {
        cells = List.copyOf(cells);
    }
}
