package io.github.stigjohnny.docs.site;

import io.github.stigjohnny.docs.Html;
import io.github.stigjohnny.docs.yaml.YamlValue;
import io.github.stigjohnny.docs.yaml.YamlValue.YMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A schema of the contract in words, with links to the named schemas on the schemas page. */
final class SchemaText {

    static final String SCHEMAS = "api/schemas.html";
    private static final String COMPONENT_PREFIX = "#/components/schemas/";

    private SchemaText() {
    }

    /** The anchor of a named schema on the schemas page. */
    static String anchor(String name) {
        return "schema-" + name.toLowerCase(Locale.ROOT);
    }

    /** A schema as HTML: a link to a named schema, or its type with its constraints. */
    static String describe(YMap schema, String page) {
        if (schema.has("$ref")) {
            String ref = schema.string("$ref");
            if (!ref.startsWith(COMPONENT_PREFIX)) {
                return Html.escape(ref);
            }
            String name = ref.substring(COMPONENT_PREFIX.length());
            return SiteContext.link(page, SCHEMAS + "#" + anchor(name), name);
        }
        List<String> parts = new ArrayList<>();
        String type = schema.optionalString("type").orElse("");
        if ("array".equals(type) && schema.has("items")) {
            String items = describe(schema.map("items"), page);
            return Html.escape("array of ") + items + constraintsHtml(schema);
        }
        if (!type.isEmpty()) {
            parts.add(type);
        }
        schema.optionalString("format").ifPresent(parts::add);
        String head = Html.escape(parts.isEmpty() ? "any value" : String.join(", ", parts));
        return head + constraintsHtml(schema);
    }

    private static String constraintsHtml(YMap schema) {
        List<String> constraints = constraints(schema);
        return constraints.isEmpty() ? "" : " <span class=\"constraints\">(" + String.join("; ", constraints) + ")</span>";
    }

    /** The constraints of a schema in words, as escaped HTML fragments. */
    static List<String> constraints(YMap schema) {
        List<String> out = new ArrayList<>();
        if (schema.has("enum")) {
            List<String> values = schema.list("enum").stream().map(v -> code(text(v))).toList();
            out.add("one of " + String.join(", ", values));
        }
        schema.find("const").ifPresent(v -> out.add("always " + code(text(v))));
        range(schema, "minimum", "maximum", "").ifPresent(out::add);
        range(schema, "minLength", "maxLength", " characters").ifPresent(out::add);
        range(schema, "minItems", "maxItems", " items").ifPresent(out::add);
        schema.optionalString("pattern").ifPresent(p -> out.add("pattern " + code(p)));
        schema.optionalString("contentMediaType").ifPresent(t -> out.add("media type " + code(t)));
        schema.find("default").ifPresent(v -> out.add("default " + code(text(v))));
        if (schema.find("uniqueItems").map(v -> "true".equals(text(v))).orElse(false)) {
            out.add("unique items");
        }
        if (schema.find("additionalProperties").map(v -> "false".equals(text(v))).orElse(false)) {
            out.add("no other members");
        }
        return out;
    }

    private static java.util.Optional<String> range(YMap schema, String min, String max, String unit) {
        boolean hasMin = schema.has(min);
        boolean hasMax = schema.has(max);
        if (hasMin && hasMax) {
            return java.util.Optional.of(Html.escape(schema.string(min) + " to " + schema.string(max) + unit));
        }
        if (hasMin) {
            return java.util.Optional.of(Html.escape("at least " + schema.string(min) + unit));
        }
        if (hasMax) {
            return java.util.Optional.of(Html.escape("at most " + schema.string(max) + unit));
        }
        return java.util.Optional.empty();
    }

    private static String text(YamlValue value) {
        return value instanceof YamlValue.YNull ? "null" : value.asText("value");
    }

    private static String code(String text) {
        return "<code>" + Html.escape(text) + "</code>";
    }
}
