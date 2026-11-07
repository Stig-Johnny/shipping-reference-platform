package io.github.stigjohnny.docs.markdown;

import java.util.List;

/** A deck's slides as HTML, in order ({@link MarkdownRenderer#renderDeck}); its title is the page's to show. */
public record RenderedDeck(List<RenderedSlide> slides) {

    public RenderedDeck {
        slides = List.copyOf(slides);
    }
}
