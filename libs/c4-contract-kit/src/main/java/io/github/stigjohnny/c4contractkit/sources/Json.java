package io.github.stigjohnny.c4contractkit.sources;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A strict JSON reader for the configuration files the check reads as data (RFC 8259): no comments, no trailing
 * commas, no duplicate member names, no other white space than space, tab, LF and CR, and at most 64 levels. Each value
 * knows the line it starts on, so that a finding can name it. A file it cannot read is refused, never guessed at.
 */
final class Json {

    private static final int MAX_DEPTH = 64;

    /** The file is not plain JSON: the line and what is wrong. */
    static final class Invalid extends Exception {

        @Serial
        private static final long serialVersionUID = 1L;

        private final int line;

        Invalid(int line, String message) {
            super(message, null, false, false);
            this.line = line;
        }

        int line() {
            return line;
        }
    }

    /** JSON's null. */
    enum Null { NULL }

    /**
     * A value and the line it starts on.
     *
     * @param value a {@code Map<String, Value>} in source order, a {@code List<Value>}, a {@code String}, a
     *     {@code BigDecimal}, a {@code Boolean} or {@link Null#NULL}
     */
    record Value(Object value, int line) {

        boolean isObject() {
            return value instanceof Map<?, ?>;
        }

        boolean isArray() {
            return value instanceof List<?>;
        }

        boolean isString() {
            return value instanceof String;
        }

        boolean isTrue() {
            return Boolean.TRUE.equals(value);
        }

        @SuppressWarnings("unchecked")
        Map<String, Value> object() {
            return (Map<String, Value>) value;
        }

        @SuppressWarnings("unchecked")
        List<Value> array() {
            return (List<Value>) value;
        }

        String string() {
            return (String) value;
        }
    }

    private final String text;
    private int position;
    private int line = 1;

    private Json(String text) {
        this.text = text;
    }

    /** Parses one JSON document. */
    static Value parse(String text) throws Invalid {
        Json json = new Json(text);
        json.space();
        Value value = json.value(0);
        json.space();
        if (json.position < text.length()) {
            throw new Invalid(json.line, "unexpected text after the JSON value");
        }
        return value;
    }

    private Value value(int depth) throws Invalid {
        if (depth > MAX_DEPTH) {
            throw new Invalid(line, "more than " + MAX_DEPTH + " levels of nesting");
        }
        if (position >= text.length()) {
            throw new Invalid(line, "a value is missing");
        }
        int start = line;
        char c = text.charAt(position);
        return switch (c) {
            case '{' -> new Value(object(depth), start);
            case '[' -> new Value(array(depth), start);
            case '"' -> new Value(string(), start);
            case 't' -> literal("true", true, start);
            case 'f' -> literal("false", false, start);
            case 'n' -> literal("null", Null.NULL, start);
            default -> {
                if (c == '-' || (c >= '0' && c <= '9')) {
                    yield new Value(number(), start);
                }
                throw new Invalid(line, "unexpected character '" + c + "' (comments are not JSON)");
            }
        };
    }

    private Map<String, Value> object(int depth) throws Invalid {
        position++;
        Map<String, Value> members = new LinkedHashMap<>();
        space();
        if (peek() == '}') {
            position++;
            return Collections.unmodifiableMap(members);
        }
        while (true) {
            space();
            if (peek() != '"') {
                throw new Invalid(line, "expected a member name in double quotes (comments and trailing commas are "
                        + "not JSON)");
            }
            int at = line;
            String name = string();
            space();
            expect(':');
            space();
            Value member = value(depth + 1);
            if (members.putIfAbsent(name, member) != null) {
                throw new Invalid(at, "the member name \"" + name + "\" appears twice");
            }
            space();
            char c = peek();
            position++;
            if (c == '}') {
                return Collections.unmodifiableMap(members);
            }
            if (c != ',') {
                throw new Invalid(line, "expected ',' or '}' in an object");
            }
        }
    }

    private List<Value> array(int depth) throws Invalid {
        position++;
        List<Value> elements = new ArrayList<>();
        space();
        if (peek() == ']') {
            position++;
            return List.copyOf(elements);
        }
        while (true) {
            space();
            elements.add(value(depth + 1));
            space();
            char c = peek();
            position++;
            if (c == ']') {
                return List.copyOf(elements);
            }
            if (c != ',') {
                throw new Invalid(line, "expected ',' or ']' in an array");
            }
        }
    }

    private String string() throws Invalid {
        position++;
        StringBuilder out = new StringBuilder();
        while (position < text.length()) {
            char c = text.charAt(position++);
            if (c == '"') {
                return out.toString();
            }
            if (c < 0x20) {
                throw new Invalid(line, "a control character inside a string");
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (position >= text.length()) {
                break;
            }
            char e = text.charAt(position++);
            switch (e) {
                case '"', '\\', '/' -> out.append(e);
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (position + 4 > text.length()) {
                        throw new Invalid(line, "an incomplete \\u escape");
                    }
                    try {
                        out.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw new Invalid(line, "an invalid \\u escape");
                    }
                    position += 4;
                }
                default -> throw new Invalid(line, "an invalid escape \\" + e);
            }
        }
        throw new Invalid(line, "an unterminated string");
    }

    private BigDecimal number() throws Invalid {
        int start = position;
        if (peek() == '-') {
            position++;
        }
        if (peek() == '0') {
            position++;
        } else if (isDigit(peek())) {
            digits();
        } else {
            throw new Invalid(line, "an invalid number");
        }
        if (peek() == '.') {
            position++;
            if (!isDigit(peek())) {
                throw new Invalid(line, "an invalid number");
            }
            digits();
        }
        if (peek() == 'e' || peek() == 'E') {
            position++;
            if (peek() == '+' || peek() == '-') {
                position++;
            }
            if (!isDigit(peek())) {
                throw new Invalid(line, "an invalid number");
            }
            digits();
        }
        return new BigDecimal(text.substring(start, position));
    }

    private void digits() {
        while (isDigit(peek())) {
            position++;
        }
    }

    private Value literal(String word, Object value, int start) throws Invalid {
        if (!text.startsWith(word, position)) {
            throw new Invalid(line, "unexpected text (only true, false and null are literals in JSON)");
        }
        position += word.length();
        return new Value(value, start);
    }

    private void expect(char c) throws Invalid {
        if (peek() != c) {
            throw new Invalid(line, "expected '" + c + "'");
        }
        position++;
    }

    private void space() {
        while (position < text.length()) {
            char c = text.charAt(position);
            if (c == '\n') {
                line++;
            } else if (c != ' ' && c != '\t' && c != '\r') {
                return;
            }
            position++;
        }
    }

    private char peek() {
        return position < text.length() ? text.charAt(position) : '\0';
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }
}
