package io.github.stigjohnny.docs.markdown;

/** One inline code span of a document: the line it starts on, counted from 1 (0 when unknown), and its text. */
public record InlineCode(int line, String text) {
}
