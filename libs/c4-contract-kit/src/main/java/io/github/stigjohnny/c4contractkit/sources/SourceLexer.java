package io.github.stigjohnny.c4contractkit.sources;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Splits TypeScript (and, for {@code .tsx}, JSX) into the tokens the import grammar reads: identifiers, string
 * literals, punctuators and markers for templates, regular expressions, numbers and JSX elements. Strings, comments,
 * template text, regular expressions and JSX text and attributes never yield identifiers, so a word inside them can
 * never be read as code; the expressions inside templates and JSX braces are code and are tokenized.
 *
 * <p>The lexer is restricted on purpose. Whatever it cannot read with certainty stops it with a {@link Stop}, so that a
 * file is refused rather than misread: an unterminated string, template, comment, regular expression or JSX element,
 * a backslash in code, the line separators U+2028 and U+2029, and JSX it does not cover. A slash or, in {@code .tsx},
 * a {@code <} starts a regular expression or a JSX element where an expression can start, as decided from the token
 * before it; a generic arrow function or generic function type that starts with {@code <} in a {@code .tsx} file is
 * therefore read as JSX and refused. A keyword right after {@code .} or {@code #} is a property name
 * ({@code options.default}, {@code this.#in}), after which a value has ended.
 */
final class SourceLexer {

    enum TokenType { IDENTIFIER, STRING, TEMPLATE, NUMBER, REGEX, PUNCTUATOR, JSX }

    /**
     * One token.
     *
     * @param text an identifier's name, a punctuator, or a string's content between the quotes as written
     * @param special a string with a backslash escape, or a template with a substitution
     */
    record Token(TokenType type, String text, int line, boolean special) {
    }

    /** The lexer cannot go on: the file is refused from this line. */
    static final class Stop extends Exception {

        @Serial
        private static final long serialVersionUID = 1L;

        private final int line;
        private final String reason;

        Stop(int line, String reason) {
            super(reason, null, false, false);
            this.line = line;
            this.reason = reason;
        }

        int line() {
            return line;
        }

        String reason() {
            return reason;
        }
    }

    /** Keywords after which an expression, and so a regular expression or JSX, can start. */
    private static final Set<String> EXPRESSION_KEYWORDS = Set.of("return", "typeof", "instanceof", "in", "of", "new",
            "delete", "void", "throw", "case", "do", "else", "yield", "await", "default", "extends");

    /** Punctuators after which an expression cannot start: a value has just ended. */
    private static final Set<String> VALUE_ENDS = Set.of(")", "]", "}", "++", "--");

    private static final Pattern JSX_PRAGMA = Pattern.compile("@jsx(?:ImportSource|Frag|Runtime)?(?![\\w$])");
    private static final Pattern TRIPLE_SLASH = Pattern.compile("^/\\s*<");

    private final String text;
    private final boolean jsx;
    private final List<Token> tokens = new ArrayList<>();
    private final List<Refusal> refusals = new ArrayList<>();
    private int position;
    private int line = 1;
    private @Nullable Token last;

    private SourceLexer(String text, boolean jsx) {
        this.text = text;
        this.jsx = jsx;
    }

    /**
     * Tokenizes a whole file.
     *
     * @param tokens receives the tokens, in source order; when the lexer stops, those before the place it stopped
     * @param refusals receives what the lexer refuses without stopping (directives and pragmas in comments)
     * @throws Stop when the rest of the file cannot be read with certainty
     */
    static void tokenize(String text, boolean jsx, List<Token> tokens, List<Refusal> refusals) throws Stop {
        SourceLexer lexer = new SourceLexer(text, jsx);
        try {
            int separator = indexOfLineSeparator(text);
            if (separator >= 0) {
                throw new Stop(lineAt(text, separator), "the characters U+2028 and U+2029 are not supported");
            }
            lexer.code(false);
        } finally {
            tokens.addAll(lexer.tokens);
            refusals.addAll(lexer.refusals);
        }
    }

    private static int indexOfLineSeparator(String text) {
        int a = text.indexOf(' ');
        int b = text.indexOf(' ');
        return a < 0 ? b : b < 0 ? a : Math.min(a, b);
    }

    private static int lineAt(String text, int index) {
        int count = 1;
        for (int i = 0; i < index; i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    /**
     * Code until the end of the file or, when {@code nested}, until the brace that closes the template substitution or
     * JSX expression it is in.
     *
     * @return whether the closing brace was found; always true for the whole file
     */
    private boolean code(boolean nested) throws Stop {
        int depth = 0;
        while (position < text.length()) {
            char c = text.charAt(position);
            if (c == '\n') {
                line++;
                position++;
            } else if (isSpace(c)) {
                position++;
            } else if (c == '/' && peek(1) == '/') {
                lineComment();
            } else if (c == '/' && peek(1) == '*') {
                blockComment();
            } else if (c == '\'' || c == '"') {
                string(c);
            } else if (c == '`') {
                template();
            } else if (c == '/') {
                if (expressionCanStart()) {
                    regex();
                } else {
                    punctuator(1);
                }
            } else if (c == '<' && jsx && expressionCanStart()) {
                int start = line;
                element();
                add(TokenType.JSX, "<>", start, false);
            } else if (c == '{') {
                depth++;
                punctuator(1);
            } else if (c == '}') {
                if (nested && depth == 0) {
                    position++;
                    return true;
                }
                depth--;
                punctuator(1);
            } else if (isIdentifierStart(text.codePointAt(position))) {
                identifier();
            } else if (isDigit(c)) {
                number();
            } else if (c == '\\') {
                throw new Stop(line, "a backslash outside a string, template or regular expression is not supported");
            } else {
                punctuator(punctuatorLength(c));
            }
        }
        return !nested;
    }

    private boolean expressionCanStart() {
        Token previous = last;
        if (previous == null) {
            return true;
        }
        return switch (previous.type()) {
            case IDENTIFIER -> EXPRESSION_KEYWORDS.contains(previous.text()) && !afterMemberAccess();
            case PUNCTUATOR -> !VALUE_ENDS.contains(previous.text());
            case STRING, TEMPLATE, NUMBER, REGEX, JSX -> false;
        };
    }

    /**
     * Whether the identifier just read follows {@code .} or {@code #}, so that it names a property, not a keyword. An
     * identifier is always the last token added: only punctuators and templates replace {@code last} otherwise.
     */
    private boolean afterMemberAccess() {
        int index = tokens.size() - 2;
        if (index < 0) {
            return false;
        }
        Token before = tokens.get(index);
        return before.type() == TokenType.PUNCTUATOR && (before.text().equals(".") || before.text().equals("#"));
    }

    private void lineComment() {
        int start = position + 2;
        int end = text.indexOf('\n', start);
        String comment = text.substring(start, end < 0 ? text.length() : end);
        if (TRIPLE_SLASH.matcher(comment).find()) {
            refusals.add(new Refusal(line, "triple-slash directives (/// <reference ...>) are not supported: they add "
                    + "dependencies in comments"));
        }
        checkPragma(comment, line);
        position = end < 0 ? text.length() : end;
    }

    private void blockComment() throws Stop {
        int start = line;
        int end = text.indexOf("*/", position + 2);
        if (end < 0) {
            throw new Stop(start, "unterminated comment");
        }
        String comment = text.substring(position + 2, end);
        checkPragma(comment, start);
        line += (int) comment.chars().filter(ch -> ch == '\n').count();
        position = end + 2;
    }

    private void checkPragma(String comment, int at) {
        if (JSX_PRAGMA.matcher(comment).find()) {
            refusals.add(new Refusal(at, "JSX pragmas (@jsx, @jsxImportSource, @jsxFrag, @jsxRuntime) are not "
                    + "supported: they change which module JSX imports"));
        }
    }

    private void string(char quote) throws Stop {
        int start = line;
        int from = position + 1;
        boolean escaped = false;
        position++;
        while (position < text.length()) {
            char c = text.charAt(position);
            if (c == quote) {
                add(TokenType.STRING, text.substring(from, position), start, escaped);
                position++;
                return;
            }
            if (c == '\\') {
                escaped = true;
                skipEscaped();
            } else if (c == '\n' || c == '\r') {
                break;
            } else {
                position++;
            }
        }
        throw new Stop(start, "unterminated string literal");
    }

    /**
     * Skips a backslash and what it escapes; an escaped line end (LF, CR or CRLF) continues the literal. Lines are
     * counted by LF everywhere, as editors number them in files with LF or CRLF line ends.
     */
    private void skipEscaped() {
        position++;
        if (position >= text.length()) {
            return;
        }
        char escaped = text.charAt(position);
        position++;
        if (escaped == '\r' && peek(0) == '\n') {
            position++;
            line++;
        } else if (escaped == '\n') {
            line++;
        }
    }

    private void template() throws Stop {
        int start = line;
        int index = tokens.size();
        add(TokenType.TEMPLATE, "`", start, false);
        boolean substitution = false;
        position++;
        while (position < text.length()) {
            char c = text.charAt(position);
            if (c == '`') {
                position++;
                tokens.set(index, new Token(TokenType.TEMPLATE, "`", start, substitution));
                last = tokens.get(index);
                return;
            }
            if (c == '\\') {
                skipEscaped();
            } else if (c == '$' && peek(1) == '{') {
                substitution = true;
                position += 2;
                if (!nestedCode()) {
                    break;
                }
            } else {
                if (c == '\n') {
                    line++;
                }
                position++;
            }
        }
        throw new Stop(start, "unterminated template literal");
    }

    /** Code inside braces of a template or JSX, which starts like any expression; false at the end of the file. */
    private boolean nestedCode() throws Stop {
        last = new Token(TokenType.PUNCTUATOR, "{", line, false);
        boolean closed = code(true);
        last = new Token(TokenType.PUNCTUATOR, "}", line, false);
        return closed;
    }

    private void regex() throws Stop {
        int start = line;
        boolean inClass = false;
        position++;
        while (position < text.length()) {
            char c = text.charAt(position);
            if (c == '\n' || c == '\r') {
                break;
            }
            if (c == '\\') {
                position++;
                if (position < text.length() && (text.charAt(position) == '\n' || text.charAt(position) == '\r')) {
                    break;
                }
            } else if (c == '[') {
                inClass = true;
            } else if (c == ']') {
                inClass = false;
            } else if (c == '/' && !inClass) {
                position++;
                while (position < text.length() && isIdentifierPart(text.codePointAt(position))) {
                    position += Character.charCount(text.codePointAt(position));
                }
                add(TokenType.REGEX, "/", start, false);
                return;
            }
            position++;
        }
        throw new Stop(start, "unterminated regular expression");
    }

    /** A JSX element or fragment, from its {@code <} to the end of its closing tag. */
    private void element() throws Stop {
        int start = line;
        position++;
        skipTagSpace();
        if (peek(0) == '>') {
            position++;
            children("", start);
            return;
        }
        String name = jsxName();
        if (name.isEmpty()) {
            throw new Stop(line, unexpectedInTag());
        }
        while (true) {
            skipTagSpace();
            if (position >= text.length()) {
                throw new Stop(start, "JSX: unterminated tag <" + name + ">");
            }
            char c = text.charAt(position);
            if (c == '/' && peek(1) == '>') {
                position += 2;
                return;
            } else if (c == '>') {
                position++;
                children(name, start);
                return;
            } else if (c == '{') {
                position++;
                if (!nestedCode()) {
                    throw new Stop(start, "JSX: unterminated expression in the tag <" + name + ">");
                }
            } else if (isJsxNameStart(c)) {
                jsxName();
                skipTagSpace();
                if (peek(0) == '=') {
                    position++;
                    skipTagSpace();
                    attributeValue(name);
                }
            } else {
                throw new Stop(line, unexpectedInTag());
            }
        }
    }

    private String unexpectedInTag() {
        String found = position < text.length() ? String.valueOf(text.charAt(position)) : "the end of the file";
        return "JSX: unexpected '" + found + "' in a tag (a generic arrow function or generic function type starting "
                + "with '<' is not supported in .tsx files)";
    }

    private void attributeValue(String tag) throws Stop {
        char c = peek(0);
        if (c == '"' || c == '\'') {
            int start = line;
            int end = text.indexOf(c, position + 1);
            if (end < 0) {
                throw new Stop(start, "JSX: unterminated attribute string in the tag <" + tag + ">");
            }
            line += (int) text.substring(position, end).chars().filter(ch -> ch == '\n').count();
            position = end + 1;
        } else if (c == '{') {
            position++;
            if (!nestedCode()) {
                throw new Stop(line, "JSX: unterminated attribute expression in the tag <" + tag + ">");
            }
        } else {
            throw new Stop(line, "JSX: an attribute value must be a string or an expression in braces");
        }
    }

    /** The children of an element up to and including its closing tag, which must name the same element. */
    private void children(String name, int start) throws Stop {
        while (position < text.length()) {
            char c = text.charAt(position);
            if (c == '\n') {
                line++;
                position++;
            } else if (c == '{') {
                position++;
                if (!nestedCode()) {
                    throw new Stop(start, "JSX: unterminated expression in <" + name + ">");
                }
            } else if (c == '<' && peek(1) == '/') {
                position += 2;
                skipTagSpace();
                String closing = jsxName();
                skipTagSpace();
                if (peek(0) != '>') {
                    throw new Stop(line, "JSX: expected '>' to end the closing tag </" + closing);
                }
                position++;
                if (!closing.equals(name)) {
                    throw new Stop(line, "JSX: closing tag </" + closing + "> does not match <" + name + ">");
                }
                return;
            } else if (c == '<') {
                element();
            } else if (c == '}' || c == '>') {
                throw new Stop(line, "JSX: '" + c + "' in JSX text is not supported; write {'" + c + "'}");
            } else {
                position++;
            }
        }
        throw new Stop(start, "JSX: unterminated element <" + name + ">");
    }

    /** Whitespace and comments inside a tag. */
    private void skipTagSpace() throws Stop {
        while (position < text.length()) {
            char c = text.charAt(position);
            if (c == '\n') {
                line++;
                position++;
            } else if (isSpace(c)) {
                position++;
            } else if (c == '/' && peek(1) == '/') {
                lineComment();
            } else if (c == '/' && peek(1) == '*') {
                blockComment();
            } else {
                return;
            }
        }
    }

    private String jsxName() {
        int from = position;
        while (position < text.length()) {
            char c = text.charAt(position);
            if (isJsxNameStart(c) || isDigit(c) || c == '.' || c == ':' || c == '-') {
                position++;
            } else {
                break;
            }
        }
        return text.substring(from, position);
    }

    private static boolean isJsxNameStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    private void identifier() {
        int from = position;
        while (position < text.length() && isIdentifierPart(text.codePointAt(position))) {
            position += Character.charCount(text.codePointAt(position));
        }
        add(TokenType.IDENTIFIER, text.substring(from, position), line, false);
    }

    /** A numeric literal; a dot belongs to it, so a decimal or a member access after it is no expression start. */
    private void number() {
        while (position < text.length()) {
            char c = text.charAt(position);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '.') {
                position++;
            } else {
                break;
            }
        }
        add(TokenType.NUMBER, "0", line, false);
    }

    /**
     * Punctuators are single characters, except {@code ++} and {@code --}: after them a value has ended, so a slash
     * divides. After any other punctuator, {@code =>} included, an expression can start.
     */
    private int punctuatorLength(char c) {
        return (c == '+' || c == '-') && peek(1) == c ? 2 : 1;
    }

    private void punctuator(int length) {
        add(TokenType.PUNCTUATOR, text.substring(position, position + length), line, false);
        position += length;
    }

    private void add(TokenType type, String value, int at, boolean special) {
        Token token = new Token(type, value, at, special);
        tokens.add(token);
        last = token;
    }

    private char peek(int offset) {
        int index = position + offset;
        return index < text.length() ? text.charAt(index) : '\0';
    }

    private static boolean isSpace(char c) {
        return c == ' ' || c == '\t' || c == '\r' || c == '\f' || c == '\u000B' || c == ' ' || c == '﻿';
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isIdentifierStart(int codePoint) {
        return codePoint == '$' || codePoint == '_' || Character.isUnicodeIdentifierStart(codePoint);
    }

    private static boolean isIdentifierPart(int codePoint) {
        return codePoint == '$' || codePoint == '‌' || codePoint == '‍'
                || (Character.isUnicodeIdentifierPart(codePoint) && !Character.isIdentifierIgnorable(codePoint));
    }
}
