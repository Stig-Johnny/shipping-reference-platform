package io.github.stigjohnny.docs.markdown;

import java.util.List;

/**
 * A deck as data ({@link MarkdownRenderer#deck}): its parsed top-level blocks grouped by slide.
 *
 * @param front what stands before the first slide: a deck's title, and nothing else in a valid one
 * @param slides the slides, in order
 */
public record DeckOutline(List<MarkdownBlock> front, List<SlideOutline> slides) {

    public DeckOutline {
        front = List.copyOf(front);
        slides = List.copyOf(slides);
    }
}
