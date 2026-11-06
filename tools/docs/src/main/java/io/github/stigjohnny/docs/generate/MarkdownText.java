package io.github.stigjohnny.docs.generate;

/** Helpers for writing Markdown text that the generator composes from data. */
public final class MarkdownText {

    private MarkdownText() {
    }

    /** Text for a table cell: one line, pipes escaped. */
    public static String cell(String text) {
        return text.replace("\r", "").replace("\n", " ").replace("|", "\\|").strip();
    }

    /** Inline code; text with a backtick uses a double-backtick fence. */
    public static String code(String text) {
        return text.contains("`") ? "`` " + text + " ``" : "`" + text + "`";
    }

    /** A double-quoted YAML scalar on one line. */
    public static String yaml(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").strip() + "\"";
    }

    /** A comma-separated list, or the fallback when empty. */
    public static String list(java.util.List<String> items, String empty) {
        return items.isEmpty() ? empty : String.join(", ", items);
    }
}
