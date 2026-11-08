package io.github.stigjohnny.shipping.scenarios.support;

import java.util.List;
import java.util.Map;

/** Deterministic JSON: two-space indentation, LF line ends, members in insertion order, no timestamps. */
final class Json {

    private Json() {
    }

    static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(out, value, 0);
        return out.append('\n').toString();
    }

    private static void write(StringBuilder out, Object value, int depth) {
        switch (value) {
            case null -> out.append("null");
            case String text -> string(out, text);
            case Integer number -> out.append(number);
            case Long number -> out.append(number);
            case Boolean flag -> out.append(flag);
            case Enum<?> constant -> string(out, constant.name());
            case Map<?, ?> map when map.isEmpty() -> out.append("{}");
            case Map<?, ?> map -> {
                out.append("{\n");
                int i = 0;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    out.append("  ".repeat(depth + 1));
                    string(out, String.valueOf(entry.getKey()));
                    out.append(": ");
                    write(out, entry.getValue(), depth + 1);
                    out.append(++i < map.size() ? ",\n" : "\n");
                }
                out.append("  ".repeat(depth)).append('}');
            }
            case List<?> list when list.isEmpty() -> out.append("[]");
            case List<?> list -> {
                out.append("[\n");
                for (int i = 0; i < list.size(); i++) {
                    out.append("  ".repeat(depth + 1));
                    write(out, list.get(i), depth + 1);
                    out.append(i + 1 < list.size() ? ",\n" : "\n");
                }
                out.append("  ".repeat(depth)).append(']');
            }
            default -> throw new IllegalArgumentException("no JSON form for " + value.getClass().getName());
        }
    }

    private static void string(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
