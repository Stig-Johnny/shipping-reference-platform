package io.github.stigjohnny.docs.yaml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An immutable YAML or JSON value. Maps keep the order of the source, so everything built from them is deterministic.
 * Numbers keep their source text, which is what canonical JSON needs.
 */
public sealed interface YamlValue {

    /** A mapping with string keys, in source order. */
    record YMap(Map<String, YamlValue> entries) implements YamlValue {

        public YMap {
            entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        }

        public boolean has(String key) {
            return entries.containsKey(key);
        }

        public YamlValue get(String key) {
            YamlValue value = entries.get(key);
            if (value == null) {
                throw new YamlInputException("missing key '" + key + "'");
            }
            return value;
        }

        public Optional<YamlValue> find(String key) {
            return Optional.ofNullable(entries.get(key));
        }

        public String string(String key) {
            return get(key).asText(key);
        }

        public Optional<String> optionalString(String key) {
            return find(key).filter(value -> !(value instanceof YNull)).map(value -> value.asText(key));
        }

        public YMap map(String key) {
            return get(key).asMap(key);
        }

        public List<YamlValue> list(String key) {
            return get(key).asList(key);
        }

        /** A list of scalars as text; a single scalar counts as a list of one. */
        public List<String> strings(String key) {
            YamlValue value = get(key);
            if (value instanceof YList list) {
                List<String> out = new ArrayList<>();
                for (YamlValue item : list.items()) {
                    out.add(item.asText(key));
                }
                return List.copyOf(out);
            }
            return List.of(value.asText(key));
        }
    }

    /** A sequence, in source order. */
    record YList(List<YamlValue> items) implements YamlValue {

        public YList {
            items = List.copyOf(items);
        }
    }

    /** A string scalar. */
    record YString(String value) implements YamlValue {
    }

    /** A number, as written in the source. */
    record YNumber(String text) implements YamlValue {
    }

    /** A boolean scalar. */
    record YBool(boolean value) implements YamlValue {
    }

    /** An explicit null. */
    record YNull() implements YamlValue {
    }

    /** The value as text: strings as they are, numbers and booleans as written. Maps, lists and null are errors. */
    default String asText(String what) {
        return switch (this) {
            case YString string -> string.value();
            case YNumber number -> number.text();
            case YBool bool -> Boolean.toString(bool.value());
            case YNull ignored -> throw new YamlInputException("'" + what + "' is null, expected text");
            case YMap ignored -> throw new YamlInputException("'" + what + "' is a mapping, expected text");
            case YList ignored -> throw new YamlInputException("'" + what + "' is a list, expected text");
        };
    }

    default YMap asMap(String what) {
        if (this instanceof YMap map) {
            return map;
        }
        throw new YamlInputException("'" + what + "' is not a mapping");
    }

    default List<YamlValue> asList(String what) {
        if (this instanceof YList list) {
            return list.items();
        }
        throw new YamlInputException("'" + what + "' is not a list");
    }
}
