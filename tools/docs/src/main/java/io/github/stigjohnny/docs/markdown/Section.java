package io.github.stigjohnny.docs.markdown;

/** A heading of a rendered document, with the anchor ID the site gives it (GitHub's algorithm). */
public record Section(int level, String text, String id) {
}
