package io.github.stigjohnny.docs.markdown;

import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * How the site shows the reference markers of ADR 0023 in inline code: as labels in current prose, which the reference
 * check reads, and as written everywhere else, such as in records, which nothing checks. The check and the site read
 * the same grammar, from here.
 */
public enum ReferenceMarkers {
    /**
     * A planned test or path as its name and a dashed "planned" label, an example as its name and an "example" label,
     * an absent path as its path and an "absent" label, and a capability or control id without its {@code cap:} prefix.
     */
    LABELLED,
    /** Every marker exactly as the Markdown writes it, as GitHub shows it. */
    AS_WRITTEN;

    /** A test class name in the prose: a capital letter, then letters and digits, ending in {@code Test}. */
    public static final Pattern TEST_NAME = Pattern.compile("[A-Z][A-Za-z0-9]*Test");

    /** What a schematic example holds: a placeholder in angle brackets, such as {@code <Module>}. */
    public static final Pattern PLACEHOLDER = Pattern.compile(".*<[^<>]+>.*", Pattern.DOTALL);

    /**
     * One name of a path: letters, digits, '.', '_', '-' and '*' for a glob, and placeholders in angle brackets. A
     * placeholder holds at least one character that is no space, and no angle bracket or slash, so it is never empty,
     * nested or left open.
     */
    private static final Pattern NAME = Pattern.compile("(?:[A-Za-z0-9._*-]|<(?=[^<>/]*[^<>/\\s])[^<>/]+>)+");

    /** Where a path is read from: by the rule of ADR 0023 when it has no anchor, or as its anchor says. */
    public enum Anchor {
        /** No anchor: from the root when its first name is a top-level directory, else from the document's. */
        NONE,
        /** Written {@code /path}: from the repository's root. */
        ROOT,
        /** Written {@code ./path}: from the document's directory. */
        DOCUMENT
    }

    /**
     * A path as the prose writes it (ADR 0023): an optional anchor, then names joined by single slashes, and a trailing
     * slash for a directory.
     *
     * @param text the path as written, anchor included
     * @param names its names, without the anchor and the trailing slash
     * @param directory whether it ends in a slash, and so names a directory
     */
    public record PathReference(String text, Anchor anchor, List<String> names, boolean directory) {

        /** How many names come before the first one that holds a glob or a placeholder: all of them for a plain path. */
        public int fixed() {
            for (int i = 0; i < names.size(); i++) {
                if (names.get(i).indexOf('*') >= 0 || names.get(i).indexOf('<') >= 0) {
                    return i;
                }
            }
            return names.size();
        }

        /** Whether it is a pattern: a name holds a glob or a placeholder, and only its fixed directory can exist. */
        public boolean pattern() {
            return fixed() < names.size();
        }

        /** Whether a name holds a placeholder, as an example must. */
        public boolean placeholder() {
            return names.stream().anyMatch(name -> name.indexOf('<') >= 0);
        }

        /** Whether the prose could mean it as a path without a marker: it has an anchor or a slash. */
        public boolean shaped() {
            return anchor != Anchor.NONE || names.size() > 1 || directory;
        }
    }

    /**
     * The path of a path marker whose syntax holds, or null: {@code planned} and {@code absent} need a path whose first
     * name is fixed, so that something can be found absent, and {@code illustrative} needs a placeholder.
     */
    public static @Nullable PathReference markedPath(String state, String payload) {
        PathReference path = path(payload);
        if (path == null) {
            return null;
        }
        return switch (state) {
            case "planned", "absent" -> path.fixed() > 0 ? path : null;
            case "illustrative" -> path.placeholder() ? path : null;
            default -> null;
        };
    }

    /**
     * The path that {@code text} writes, or null when it is none by the grammar: an empty name, a repeated slash, a
     * "." or ".." name after the anchor, a character outside a name, or a placeholder that is empty, nested or open.
     */
    public static @Nullable PathReference path(String text) {
        Anchor anchor = Anchor.NONE;
        String rest = text;
        if (rest.startsWith("./")) {
            anchor = Anchor.DOCUMENT;
            rest = rest.substring(2);
        } else if (rest.startsWith("/")) {
            anchor = Anchor.ROOT;
            rest = rest.substring(1);
        }
        boolean directory = rest.endsWith("/");
        if (directory) {
            rest = rest.substring(0, rest.length() - 1);
        }
        if (rest.isEmpty()) {
            return null;
        }
        List<String> names = List.of(rest.split("/", -1));
        for (String name : names) {
            if (name.equals(".") || name.equals("..") || !NAME.matcher(name).matches()) {
                return null;
            }
        }
        return new PathReference(text, anchor, names, directory);
    }
}
