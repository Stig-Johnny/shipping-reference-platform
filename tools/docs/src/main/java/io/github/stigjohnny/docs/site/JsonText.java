package io.github.stigjohnny.docs.site;

import io.github.stigjohnny.docs.yaml.YamlValue;
import io.github.stigjohnny.docs.yaml.YamlValue.YBool;
import io.github.stigjohnny.docs.yaml.YamlValue.YList;
import io.github.stigjohnny.docs.yaml.YamlValue.YMap;
import io.github.stigjohnny.docs.yaml.YamlValue.YNull;
import io.github.stigjohnny.docs.yaml.YamlValue.YNumber;
import io.github.stigjohnny.docs.yaml.YamlValue.YString;
import java.util.List;
import java.util.Map;

/** Pretty JSON of a value from the contract, for the examples on the API pages, in source order. */
final class JsonText {

    private JsonText() {
    }

    static String pretty(YamlValue value) {
        StringBuilder out = new StringBuilder();
        write(value, 0, out);
        return out.toString();
    }

    private static void write(YamlValue value, int depth, StringBuilder out) {
        switch (value) {
            case YMap map -> {
                if (map.entries().isEmpty()) {
                    out.append("{}");
                    return;
                }
                out.append("{\n");
                int i = 0;
                for (Map.Entry<String, YamlValue> entry : map.entries().entrySet()) {
                    out.append("  ".repeat(depth + 1)).append(quote(entry.getKey())).append(": ");
                    write(entry.getValue(), depth + 1, out);
                    out.append(++i < map.entries().size() ? ",\n" : "\n");
                }
                out.append("  ".repeat(depth)).append('}');
            }
            case YList list -> {
                List<YamlValue> items = list.items();
                if (items.isEmpty()) {
                    out.append("[]");
                    return;
                }
                out.append("[\n");
                for (int i = 0; i < items.size(); i++) {
                    out.append("  ".repeat(depth + 1));
                    write(items.get(i), depth + 1, out);
                    out.append(i + 1 < items.size() ? ",\n" : "\n");
                }
                out.append("  ".repeat(depth)).append(']');
            }
            case YString string -> out.append(quote(string.value()));
            case YNumber number -> out.append(number.text());
            case YBool bool -> out.append(bool.value());
            case YNull ignored -> out.append("null");
        }
    }

    static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
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
                        out.append("\\u%04x".formatted((int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
