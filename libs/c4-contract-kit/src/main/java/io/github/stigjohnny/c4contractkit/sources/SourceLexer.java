package io.github.stigjohnny.c4contractkit.sources;

import java.io.Serial;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Splits TypeScript (and, for {@code .tsx}, JSX) into the tokens the import grammar reads: identifiers, string
 * literals, punctuators and markers for templates, regular expressions, numbers and JSX elements. Strings, comments,
 * template text, regular expressions and JSX text and attributes never yield identifiers, so a word inside them can
 * never be read as code; the expressions inside templates and JSX braces are code and are tokenized.
 *
 * <p>The lexer supports partial lint, not a general fail-closed guarantee (ADR 0013). In particular, its spread
 * tokens can cause the scanner to miss a following loader. Recognized ambiguities stop it with a {@link Stop}:
 * an unterminated string, template, comment, regular expression or JSX element, a
 * backslash in code, a character that is neither ECMAScript white space, a line feed, an identifier nor a
 * punctuator, a carriage return that does not start a CRLF line end, U+2028 and U+2029, and JSX it does not cover.
 *
 * <p>A slash, and in {@code .tsx} a {@code <}, starts a regular expression or a JSX element where an expression
 * starts, and is an operator after a value. Which of the two it is follows from the token before it and, for a
 * closing parenthesis, from what the parenthesis opened:
 *
 * <ul>
 *   <li>after a name, a literal, {@code ]}, a property named like a keyword ({@code a.default}) or the {@code )} of a
 *       call or a group, it is an operator;
 *   <li>after another punctuator, after a keyword such as {@code return}, {@code typeof} or {@code else}, and after
 *       the {@code )} that ends the head of {@code if}, {@code while}, {@code for} or {@code with}, an expression
 *       starts;
 *   <li>after {@code !} it is an operator where the {@code !} is TypeScript's non-null assertion, that is right after
 *       a value on the same line ({@code n! / 2}), and an expression starts where it is the negation;
 *   <li>after {@code >} (except in {@code =>}), {@code }}, {@code ++}, {@code --}, an unmatched {@code )},
 *       a module specifier and the words {@code of},
 *       {@code await} and {@code yield}, either is possible: the lexer refuses the file there instead of guessing.
 * </ul>
 */
final class SourceLexer {

    enum TokenType { IDENTIFIER, STRING, TEMPLATE, NUMBER, REGEX, PUNCTUATOR, JSX }

    /**
     * One token.
     *
     * @param text an identifier's name, a punctuator, or a string's content between the quotes as written
     * @param special a string with a backslash escape, a template with a substitution, or an opening bracket
     *     that may continue a value (computed access rather than an array expression)
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

    static final String LINE_SEPARATOR = "the characters U+2028 and U+2029 are not supported";
    static final String LONE_CARRIAGE_RETURN = "a carriage return that is not followed by a line feed is not "
            + "supported: TypeScript ends a line, and a // comment, there";
    static final String TRIPLE_SLASH = "triple-slash directives (/// <reference ...>) are not supported: they add "
            + "dependencies in comments";
    static final String JSX_PRAGMA = "JSX pragmas (@jsx, @jsxImportSource, @jsxFrag, @jsxRuntime) are not supported: "
            + "they change which module JSX imports";
    static final String NUMBER = "a numeric literal or its continuation is outside the supported grammar";
    static final String REGEX_FLAGS = "regular expression flags must be distinct letters from dgimsuy; "
            + "Unicode sets (v) and escaped or unknown flags are not supported";

    /** What a slash, or in .tsx a {@code <}, right after a token is. */
    private enum Next {
        /** A division, or a comparison: a value has just ended. */
        OPERATOR,
        /** The start of a regular expression, or of a JSX element: an expression starts. */
        EXPRESSION,
        /** Either: the lexer refuses the file rather than guess. */
        AMBIGUOUS
    }

    /** Keywords after which an expression, and so a regular expression or JSX, starts. */
    private static final Set<String> EXPRESSION_KEYWORDS = Set.of("return", "typeof", "instanceof", "in", "new",
            "delete", "void", "throw", "case", "do", "else", "default", "extends", "break", "continue", "debugger");

    /** Words that are a keyword before an expression in one place and a name before an operator in another. */
    private static final Set<String> AMBIGUOUS_WORDS = Set.of("of", "await", "yield");

    /** Keywords whose parenthesis holds a statement's head: after its {@code )} a statement starts. */
    private static final Set<String> CONTROL_KEYWORDS = Set.of("if", "while", "for", "with");

    /** The punctuators of TypeScript; {@code ++}, {@code --} and {@code =>} are read as two-character ones. */
    private static final String PUNCTUATORS = "{}()[];,<>+-*/%&|^!~?:=.@#";

    private static final Pattern JSX_PRAGMA_NAME = Pattern.compile("@jsx",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Token OPEN_BRACE = new Token(TokenType.PUNCTUATOR, "{", 0, false);
    private static final Token CLOSE_BRACE = new Token(TokenType.PUNCTUATOR, "}", 0, false);

    private final String text;
    private final boolean jsx;
    private final List<Token> tokens = new ArrayList<>();
    private final List<Refusal> refusals = new ArrayList<>();
    private int position;
    private int line = 1;
    /** The token before the current position, a brace at the edge of nested code, or null at the start. */
    private @Nullable Token last;
    /** The index of {@link #last} in {@link #tokens}, or -1 when it is a brace at the edge of nested code. */
    private int lastIndex = -1;
    /** The line on which {@link #last} ended. */
    private int lastEndLine = 1;
    /** What a slash after {@link #last} is. */
    private Next next = Next.EXPRESSION;
    /** For {@link Next#AMBIGUOUS}: what the slash would follow, in words. */
    private String ambiguousAfter = "";
    /** One entry per open parenthesis: whether it holds the head of if, while, for, for await or with. */
    private Deque<Boolean> parentheses = new ArrayDeque<>();

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
            lexer.checkLineTerminators();
            lexer.checkTripleSlashLines();
            lexer.code(false);
        } finally {
            tokens.addAll(lexer.tokens);
            refusals.addAll(lexer.refusals);
        }
    }

    /**
     * Line terminators other than LF and CRLF are refused wherever they are, comments and strings included:
     * TypeScript ends a line, and a {@code //} comment, at a lone CR, at U+2028 and at U+2029, so a comment that this
     * lexer read up to the next LF could hide code.
     */
    private void checkLineTerminators() throws Stop {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ' || c == ' ') {
                throw new Stop(lineAt(text, i), LINE_SEPARATOR);
            }
            if (c == '\r' && (i + 1 >= text.length() || text.charAt(i + 1) != '\n')) {
                throw new Stop(lineAt(text, i), LONE_CARRIAGE_RETURN);
            }
        }
    }

    /**
     * A line whose first character other than white space (TypeScript's, which counts U+0085 and U+200B too) is the
     * start of {@code ///} is refused, wherever the lexer would place it: TypeScript reads triple-slash directives
     * from such comments.
     */
    private void checkTripleSlashLines() {
        int number = 1;
        int start = 0;
        while (start <= text.length()) {
            int end = text.indexOf('\n', start);
            int stop = end < 0 ? text.length() : end;
            int i = start;
            while (i < stop && (isWhitespace(text.charAt(i)) || isTypeScriptOnlyWhitespace(text.charAt(i)))) {
                i++;
            }
            if (text.startsWith("///", i) && i < stop) {
                refusals.add(new Refusal(number, TRIPLE_SLASH));
            }
            if (end < 0) {
                return;
            }
            number++;
            start = end + 1;
        }
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
            } else if (c == '\r' || isWhitespace(c)) {
                // A carriage return here precedes a line feed: checkLineTerminators refused every other one.
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
                if (decide("/") == Next.EXPRESSION) {
                    regex();
                } else {
                    punctuator(1);
                }
            } else if (c == '<' && jsx && decide("<") == Next.EXPRESSION) {
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
            } else if (isDigit(c) || (c == '.' && isDigit(peek(1)))) {
                number();
            } else if (c == '\\') {
                throw new Stop(line, "a backslash outside a string, template or regular expression is not supported");
            } else if (PUNCTUATORS.indexOf(c) >= 0) {
                punctuator(punctuatorLength(c));
            } else {
                throw new Stop(line, "the character U+" + String.format(Locale.ROOT, "%04X",
                        text.codePointAt(position)) + " is not supported outside strings, comments, templates and "
                        + "JSX text");
            }
        }
        return !nested;
    }

    /** What a slash or a {@code <} at the current position is; a stop where it could be either. */
    private Next decide(String symbol) throws Stop {
        if (next == Next.AMBIGUOUS) {
            String starts = symbol.equals("/") ? "a regular expression" : "a JSX element";
            throw new Stop(line, "a " + symbol + " after " + ambiguousAfter + " is not supported: it could start "
                    + starts + " or be an operator, and the reader does not guess; put the expression in parentheses");
        }
        return next;
    }

    private void lineComment() {
        int start = position + 2;
        int end = text.indexOf('\n', start);
        String comment = text.substring(start, end < 0 ? text.length() : end);
        if (comment.startsWith("/")) {
            refusals.add(new Refusal(line, TRIPLE_SLASH));
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

    /** Any comment that names {@code @jsx}, in any letter case, is refused: TypeScript reads pragmas from comments. */
    private void checkPragma(String comment, int at) {
        if (JSX_PRAGMA_NAME.matcher(comment).find()) {
            refusals.add(new Refusal(at, JSX_PRAGMA));
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
     * Skips a backslash and what it escapes; an escaped line end (LF or CRLF) continues the literal. Lines are
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
        add(TokenType.TEMPLATE, "`", start, false);
        int index = lastIndex;
        boolean substitution = false;
        position++;
        while (position < text.length()) {
            char c = text.charAt(position);
            if (c == '`') {
                position++;
                tokens.set(index, new Token(TokenType.TEMPLATE, "`", start, substitution));
                last = tokens.get(index);
                lastIndex = index;
                lastEndLine = line;
                next = Next.OPERATOR;
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

    /**
     * Code inside braces of a template or JSX, which starts like any expression, with parentheses of its own; false
     * at the end of the file.
     */
    private boolean nestedCode() throws Stop {
        Deque<Boolean> outer = parentheses;
        parentheses = new ArrayDeque<>();
        last = OPEN_BRACE;
        lastIndex = -1;
        next = Next.EXPRESSION;
        boolean closed = code(true);
        parentheses = outer;
        last = CLOSE_BRACE;
        lastIndex = -1;
        next = ambiguous("'}'");
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
                regexFlags();
                add(TokenType.REGEX, "/", start, false);
                return;
            }
            position++;
        }
        throw new Stop(start, "unterminated regular expression");
    }

    /** Never swallow an arbitrary identifier as flags. Unicode-set bodies need a different character-class reader. */
    private void regexFlags() throws Stop {
        int seen = 0;
        while (position < text.length()
                && (isIdentifierPart(text.codePointAt(position)) || peek(0) == '\\')) {
            int flag = "dgimsuy".indexOf(peek(0));
            if (flag < 0 || (seen & (1 << flag)) != 0) {
                throw new Stop(line, REGEX_FLAGS);
            }
            seen |= 1 << flag;
            position++;
        }
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

    /** White space and comments inside a tag. */
    private void skipTagSpace() throws Stop {
        while (position < text.length()) {
            char c = text.charAt(position);
            if (c == '\n') {
                line++;
                position++;
            } else if (c == '\r' || isWhitespace(c)) {
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

    /** Read only the literal; a following property dot and name must remain visible to the import scanner. */
    private void number() throws Stop {
        int radix = peek(0) == '0' ? switch (peek(1)) {
            case 'x', 'X' -> 16;
            case 'b', 'B' -> 2;
            case 'o', 'O' -> 8;
            default -> 10;
        } : 10;
        boolean integer = true;
        if (radix != 10) {
            position += 2;
            numericDigits(radix);
        } else {
            if (peek(0) != '.') {
                int from = position;
                numericDigits(10);
                if (text.charAt(from) == '0' && position > from + 1) {
                    throw new Stop(line, NUMBER); // Legacy leading-zero forms are deliberately unsupported.
                }
            }
            if (peek(0) == '.') {
                integer = false;
                position++;
                if (isDigit(peek(0))) {
                    numericDigits(10);
                }
            }
            if (peek(0) == 'e' || peek(0) == 'E') {
                integer = false;
                position++;
                if (peek(0) == '+' || peek(0) == '-') {
                    position++;
                }
                numericDigits(10);
            }
        }
        if (integer && peek(0) == 'n') {
            position++;
        }
        if (position < text.length() && (isIdentifierPart(text.codePointAt(position)) || peek(0) == '\\')) {
            throw new Stop(line, NUMBER);
        }
        add(TokenType.NUMBER, "0", line, false);
    }

    /** A separator is allowed only between two digits of the same radix, never at either end or doubled. */
    private void numericDigits(int radix) throws Stop {
        if (!numericDigit(peek(0), radix)) {
            throw new Stop(line, NUMBER);
        }
        do {
            position++;
            if (peek(0) == '_') {
                position++;
                if (!numericDigit(peek(0), radix)) {
                    throw new Stop(line, NUMBER);
                }
            }
        } while (numericDigit(peek(0), radix));
    }

    private static boolean numericDigit(char c, int radix) {
        int value = "0123456789abcdef".indexOf(Character.toLowerCase(c));
        return value >= 0 && value < radix;
    }

    /** Keep {@code =>} distinct from a closing type argument or comparison operator. */
    private int punctuatorLength(char c) {
        return ((c == '+' || c == '-') && peek(1) == c) || (c == '=' && peek(1) == '>') ? 2 : 1;
    }

    private void punctuator(int length) {
        String value = text.substring(position, position + length);
        boolean access = value.equals("[") && (next != Next.EXPRESSION || isMemberAccess(last));
        add(TokenType.PUNCTUATOR, value, line, access);
        position += length;
    }

    private void add(TokenType type, String value, int at, boolean special) {
        Token token = new Token(type, value, at, special);
        Next after = classify(token);
        tokens.add(token);
        last = token;
        lastIndex = tokens.size() - 1;
        lastEndLine = line;
        next = after;
    }

    /** What a slash after this token is, from the token, the one before it and the open parentheses. */
    private Next classify(Token token) {
        return switch (token.type()) {
            case STRING -> isKeyword(last, lastIndex, "import") || isKeyword(last, lastIndex, "from")
                    ? ambiguous("a module specifier") : Next.OPERATOR;
            case TEMPLATE, NUMBER, REGEX, JSX -> Next.OPERATOR;
            case IDENTIFIER -> {
                if (isMemberAccess(last)) {
                    yield Next.OPERATOR;
                }
                if (EXPRESSION_KEYWORDS.contains(token.text())) {
                    yield Next.EXPRESSION;
                }
                yield AMBIGUOUS_WORDS.contains(token.text()) ? ambiguous("'" + token.text() + "'") : Next.OPERATOR;
            }
            case PUNCTUATOR -> switch (token.text()) {
                case "(" -> {
                    parentheses.push(opensControlHead());
                    yield Next.EXPRESSION;
                }
                case ")" -> parentheses.isEmpty() ? ambiguous("an unmatched ')'")
                        : parentheses.pop() ? Next.EXPRESSION : Next.OPERATOR;
                case "]" -> Next.OPERATOR;
                // A type argument may end a value (value as N<number> / 2); a comparison starts an expression.
                case ">" -> ambiguous("'>'");
                case "}" -> ambiguous("'}'");
                case "++", "--" -> ambiguous("'" + token.text() + "'");
                case "!" -> bang(token);
                default -> Next.EXPRESSION;
            };
        };
    }

    /**
     * After {@code !}: TypeScript reads {@code !} right after a value on the same line as a non-null assertion, after
     * which the value goes on; on a later line, or after an operator or keyword, it is the negation, after which an
     * expression starts.
     */
    private Next bang(Token token) {
        if (last == null || token.line() != lastEndLine) {
            return Next.EXPRESSION;
        }
        return next;
    }

    /** Whether the {@code (} being added opens the head of if, while, for, for await or with. */
    private boolean opensControlHead() {
        if (last != null && CONTROL_KEYWORDS.contains(last.text()) && isKeyword(last, lastIndex, last.text())) {
            return true;
        }
        return isKeyword(last, lastIndex, "await") && lastIndex > 0
                && isKeyword(tokens.get(lastIndex - 1), lastIndex - 1, "for");
    }

    /** Whether the token at this index is this keyword: an identifier with its name that no {@code .} or # precedes. */
    private boolean isKeyword(@Nullable Token token, int index, String keyword) {
        return token != null && index >= 0 && token.type() == TokenType.IDENTIFIER && token.text().equals(keyword)
                && !(index > 0 && isMemberAccess(tokens.get(index - 1)));
    }

    private static boolean isMemberAccess(@Nullable Token token) {
        return token != null && token.type() == TokenType.PUNCTUATOR
                && (token.text().equals(".") || token.text().equals("#"));
    }

    private Next ambiguous(String after) {
        ambiguousAfter = after;
        return Next.AMBIGUOUS;
    }

    private char peek(int offset) {
        int index = position + offset;
        return index < text.length() ? text.charAt(index) : '\0';
    }

    /** ECMAScript's WhiteSpace: TAB, VT, FF, ZWNBSP and every Zs character, SP and NBSP among them. */
    static boolean isWhitespace(char c) {
        return c == '\t' || c == '\u000B' || c == '\f' || c == '﻿'
                || Character.getType(c) == Character.SPACE_SEPARATOR;
    }

    /** NEL and ZWSP, which TypeScript's scanner skips as white space although ECMAScript does not. */
    private static boolean isTypeScriptOnlyWhitespace(char c) {
        return c == '\u0085' || c == '​';
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
