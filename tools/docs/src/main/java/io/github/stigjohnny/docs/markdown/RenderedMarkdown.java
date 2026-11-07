package io.github.stigjohnny.docs.markdown;

import java.util.List;

/** The HTML of one document and its headings, in order. */
public record RenderedMarkdown(String html, List<Section> headings) {

    public RenderedMarkdown {
        headings = List.copyOf(headings);
    }
}
