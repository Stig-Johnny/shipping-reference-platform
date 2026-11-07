package io.github.stigjohnny.docs;

/** Escaping for text and attribute values in the HTML this tool writes itself. */
public final class Html {

    private Html() {
    }

    /** Escapes {@code & < > " '} so that the text is safe in element content and in quoted attribute values. */
    public static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
