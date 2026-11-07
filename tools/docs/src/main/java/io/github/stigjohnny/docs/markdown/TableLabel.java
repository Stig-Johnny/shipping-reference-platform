package io.github.stigjohnny.docs.markdown;

/**
 * The accessible name of the keyboard-scrollable region around a table, in the page's own language: from the table's
 * number on the page and the section it stands in.
 */
@FunctionalInterface
public interface TableLabel {

    String of(int number, String section);
}
