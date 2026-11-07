package io.github.stigjohnny.docs.markdown;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One slide of a deck as data ({@link MarkdownRenderer#deck}): a level-2 heading and the top-level blocks after it, up
 * to the next one, with its notes apart. Nothing in it is rendered.
 *
 * @param title the heading's plain text
 * @param id the slide's anchor: GitHub's slug of its title, without the suffix that a repeated slug would get
 * @param heading the level-2 heading's own block
 * @param content the blocks that a projected slide shows
 * @param notesHeading the reserved level-3 heading that starts the notes; null for a slide without notes
 * @param notes the blocks after the notes heading, to the slide's end; they are never shown on the slide
 */
public record SlideOutline(String title, String id, MarkdownBlock heading, List<MarkdownBlock> content,
        @Nullable MarkdownBlock notesHeading, List<MarkdownBlock> notes) {

    public SlideOutline {
        content = List.copyOf(content);
        notes = List.copyOf(notes);
    }

    /** Whether the slide has the reserved notes heading, whatever stands below it. */
    public boolean hasNotes() {
        return notesHeading != null;
    }
}
