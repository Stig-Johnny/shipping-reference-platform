package io.github.stigjohnny.c4contractkit.report;

import java.util.List;

/**
 * A JSON value, written by hand because no JSON library is approved for the library's runtime class path: objects keep
 * their member order, the text uses two-space indentation and LF line ends, and strings are escaped as RFC 8259
 * requires (plus unpaired surrogates, so the text is always valid UTF-8).
 */
sealed interface Json {

    record JsonObject(List<Member> members) implements Json {
    }

    record Member(String name, Json value) {
    }

    record JsonArray(List<Json> items) implements Json {
    }

    record JsonString(String value) implements Json {
    }

    record JsonNumber(long value) implements Json {
    }

    record JsonBoolean(boolean value) implements Json {
    }

    static JsonObject object(Member... members) {
        return new JsonObject(List.of(members));
    }

    static Member member(String name, Json value) {
        return new Member(name, value);
    }

    static Member member(String name, String value) {
        return new Member(name, new JsonString(value));
    }

    static Member member(String name, long value) {
        return new Member(name, new JsonNumber(value));
    }

    static Member member(String name, boolean value) {
        return new Member(name, new JsonBoolean(value));
    }

    static JsonArray array(List<? extends Json> items) {
        return new JsonArray(List.copyOf(items));
    }

    static JsonArray strings(List<String> values) {
        return new JsonArray(values.stream().<Json>map(JsonString::new).toList());
    }

    /** The value as a JSON text that ends with a line feed. */
    default String print() {
        StringBuilder out = new StringBuilder();
        write(this, out, "");
        return out.append('\n').toString();
    }

    private static void write(Json value, StringBuilder out, String indent) {
        switch (value) {
            case JsonObject object -> {
                out.append('{');
                List<Member> members = object.members();
                for (int i = 0; i < members.size(); i++) {
                    out.append(i == 0 ? "\n" : ",\n").append(indent).append("  ");
                    quote(members.get(i).name(), out);
                    out.append(": ");
                    write(members.get(i).value(), out, indent + "  ");
                }
                out.append(members.isEmpty() ? "" : "\n" + indent).append('}');
            }
            case JsonArray array -> {
                out.append('[');
                List<Json> items = array.items();
                for (int i = 0; i < items.size(); i++) {
                    out.append(i == 0 ? "\n" : ",\n").append(indent).append("  ");
                    write(items.get(i), out, indent + "  ");
                }
                out.append(items.isEmpty() ? "" : "\n" + indent).append(']');
            }
            case JsonString string -> quote(string.value(), out);
            case JsonNumber number -> out.append(number.value());
            case JsonBoolean bool -> out.append(bool.value());
        }
    }

    private static void quote(String text, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> out.append(c < ' ' || unpairedSurrogate(text, i) ? "\\u%04x".formatted((int) c) : c);
            }
        }
        out.append('"');
    }

    private static boolean unpairedSurrogate(String text, int index) {
        char c = text.charAt(index);
        if (Character.isHighSurrogate(c)) {
            return index + 1 == text.length() || !Character.isLowSurrogate(text.charAt(index + 1));
        }
        return Character.isLowSurrogate(c) && (index == 0 || !Character.isHighSurrogate(text.charAt(index - 1)));
    }
}
