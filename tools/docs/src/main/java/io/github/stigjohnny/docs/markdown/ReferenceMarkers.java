package io.github.stigjohnny.docs.markdown;

import java.util.regex.Pattern;

/**
 * How the site shows the reference markers of ADR 0023 in inline code: as labels in current prose, which the reference
 * check reads, and as written everywhere else, such as in records, which nothing checks. The check and the site read
 * the same grammar, from here.
 */
public enum ReferenceMarkers {
    /**
     * A planned test as its name and a dashed "planned" label, an example as its name and an "example" label, and a
     * capability or control id without its {@code cap:} prefix.
     */
    LABELLED,
    /** Every marker exactly as the Markdown writes it, as GitHub shows it. */
    AS_WRITTEN;

    /** A test class name in the prose: a capital letter, then letters and digits, ending in {@code Test}. */
    public static final Pattern TEST_NAME = Pattern.compile("[A-Z][A-Za-z0-9]*Test");

    /** What a schematic example holds: a placeholder in angle brackets, such as {@code <Module>}. */
    public static final Pattern PLACEHOLDER = Pattern.compile(".*<[^<>]+>.*", Pattern.DOTALL);
}
