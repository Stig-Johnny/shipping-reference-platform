package io.github.stigjohnny.docs.markdown;

/**
 * One slide of a deck as HTML ({@link MarkdownRenderer#renderDeck}), with its notes apart: the slide's page shows its
 * {@code html} and never its {@code notesHtml}.
 *
 * @param id the slide's anchor, as {@link SlideOutline#id}
 * @param title the title's plain text
 * @param titleHtml the title's inline HTML, for the slide's heading
 * @param html the slide's content
 * @param hasNotes whether the slide has the reserved notes heading
 * @param notesHtml the notes; empty for a slide without
 */
public record RenderedSlide(String id, String title, String titleHtml, String html, boolean hasNotes,
        String notesHtml) {
}
