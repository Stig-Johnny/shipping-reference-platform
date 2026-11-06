package io.github.stigjohnny.c4contractkit.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Finds the {@code ${NAME}} references that the parser leaves as text. With ENVIRONMENT off, the parser replaces a
 * reference only with a constant or variable defined before it ({@code !const}, {@code !var}) and otherwise keeps
 * it, silently. Such a reference wherever it stands (the workspace, an element, a group, a view, a style) means the
 * model expected an environment variable, and is a model problem.
 *
 * <p>The scan reads the file the way StructurizrDslParser 6.2.4 does. First its line preprocessing: a line that ends
 * in a backslash, and is not a line comment, continues on the next line, whose leading whitespace is dropped; a line
 * that ends with {@code """} opens a text block, which ends at the next line that ends with {@code """} (the parser
 * keeps nothing else of that line). A text block is text: the lines in it are never comments or definitions, and its
 * references are resolved where the line that holds it is read. Then line by line: a byte order mark is dropped,
 * line comments are skipped, block comments nest, an identifier assignment ({@code x =}) is dropped from the tokens,
 * and a {@code !const} or {@code !var} line defines its name for the lines after it, where the parser reads it as that
 * directive.
 *
 * <p>The parser does not, inside a block whose parser reads every line as data. In {@code properties} each line is a
 * property, and in {@code perspectives} each line is a perspective, except a <code>perspective &lt;name&gt; {</code>
 * line, which opens a block of directives again. The other such blocks in StructurizrDslParser 6.2.4 (a dynamic view
 * and its parallel sequences, an animation, the users of the configuration) read a line as a step or a user, and a
 * {@code !const} or {@code !var} line there does not parse, so the scan need not follow them. So the scan keeps only
 * those two kinds of block, and the perspective blocks inside the second: a line that starts with {@code properties}
 * or {@code perspectives} opens one, whatever follows, and a line that starts with <code>}</code> closes the innermost
 * block. Neither kind holds any other block, so the other blocks of the file need no record: the brace that closes one
 * of them finds no block of those kinds open. {@link ParsedStrings} checks the parsed workspace afterwards as well.
 *
 * <p>The scan does not follow scopes, and errs only towards rejecting. Inside the model, StructurizrDslParser reads
 * {@code properties -> app} as a relationship from an element named {@code properties}; at workspace scope, or with
 * an archetype that does not exist, it opens the properties block. The scan always opens it, so after such a
 * relationship a {@code !const} defines nothing for the scan until the block closes, and a reference to it is rejected
 * although the parser substitutes it (docs/gaps.md, section 5). So that such a block also closes no earlier than the
 * parser's, a line inside a block of data that ends with a brace counts as a block nested in it: a closing brace
 * then ends the innermost of those first, and the parser's own blocks can never leave the scan outside a block of
 * data while the parser is still in one.
 */
final class UnresolvedReferences {

    /** StructurizrDslParser.STRING_SUBSTITUTION_PATTERN: only these names are ever substituted. */
    private static final Pattern REFERENCE = Pattern.compile("\\$\\{([a-zA-Z0-9-_.]+?)}");

    /** NameValueParser: the names a constant or variable may have. */
    private static final Pattern CONSTANT_NAME = Pattern.compile("[a-zA-Z0-9-_.]+");

    /** StructurizrDslParser.COMMENT_PATTERN. */
    private static final Pattern COMMENT = Pattern.compile("^\\s*?(//|#).*$");

    private static final String TEXT_BLOCK = "\"\"\"";
    private static final String CONTINUATION = "\\";
    private static final String BYTE_ORDER_MARK = "\uFEFF";
    private static final String BLOCK_COMMENT_START = "/*";
    private static final String BLOCK_COMMENT_END = "*/";
    private static final String BLOCK_START = "{";
    private static final String BLOCK_END = "}";

    /** A block of the two kinds that read each line as data, or a perspective block, whose lines are directives. */
    private enum Block { PROPERTIES, PERSPECTIVES, OTHER }

    private UnresolvedReferences() {
    }

    /**
     * Adds one problem per unresolved reference, located as {@code <displayPath>:<line>}.
     *
     * @return the names of the references it reported
     */
    static Set<String> check(List<String> lines, String displayPath, Problems problems) {
        Set<String> defined = new HashSet<>();
        Set<String> reported = new HashSet<>();
        Deque<Block> blocks = new ArrayDeque<>();
        int openComments = 0;
        for (LogicalLine line : logicalLines(lines)) {
            Located code = line.code().text().startsWith(BYTE_ORDER_MARK)
                    ? line.code().slice(1, line.code().text().length()) : line.code();
            String text = code.text();
            if (COMMENT.matcher(text).matches()) {
                continue;
            }
            List<String> tokens = withoutIdentifierAssignment(tokens(text));
            String first = tokens.isEmpty() ? "" : tokens.get(0);
            String trimmed = text.trim();
            if (trimmed.startsWith(BLOCK_COMMENT_START) && trimmed.endsWith(BLOCK_COMMENT_END)) {
                continue;
            }
            if (first.startsWith(BLOCK_COMMENT_START)) {
                openComments++;
                continue;
            }
            if (openComments > 0) {
                if (trimmed.endsWith(BLOCK_COMMENT_END)) {
                    openComments--;
                }
                continue;
            }
            report(code, defined, displayPath, problems, reported);
            line.textBlock().ifPresent(block -> report(block, defined, displayPath, problems, reported));
            Block current = blocks.isEmpty() ? Block.OTHER : blocks.peek();
            if (BLOCK_END.equals(first)) {
                blocks.poll();
            } else if (current == Block.OTHER) {
                if (first.equalsIgnoreCase("!const") || first.equalsIgnoreCase("!var")) {
                    tokens.stream().skip(1).findFirst().ifPresent(defined::add);
                } else if (first.equalsIgnoreCase("properties")) {
                    blocks.push(Block.PROPERTIES);
                } else if (first.equalsIgnoreCase("perspectives")) {
                    blocks.push(Block.PERSPECTIVES);
                }
            } else if (opensBlock(tokens)) {
                // In a block that reads lines as data, StructurizrDslParser opens only a perspective block, for
                // "perspective <name> {". Any other line that ends with a brace counts as a nested block of the same
                // kind, so that a closing brace never ends the data block early (see the class comment).
                boolean perspective = current == Block.PERSPECTIVES && first.equalsIgnoreCase("perspective")
                        && tokens.size() == 3;
                blocks.push(perspective ? Block.OTHER : current);
            }
        }
        return reported;
    }

    /** StructurizrDslParser.shouldStartContext: the last token is the opening brace. */
    private static boolean opensBlock(List<String> tokens) {
        return !tokens.isEmpty() && tokens.get(tokens.size() - 1).equalsIgnoreCase(BLOCK_START);
    }

    /**
     * Every name that a line of the file could declare, with how it is declared. A token after {@code !const} or
     * {@code !var} on any line, physical or joined, in a comment, a text block or a block of data as well, maps to
     * that keyword in lower case. Any other token that is a valid constant name maps to {@code !const or !var}: a
     * constant can produce the keyword ({@code ${K} NAME value}) or the name ({@code !const ${N} value}) of a
     * declaration. The parser defines few of these names; asking it for their values costs nothing, and a line that
     * only substitution makes a block keyword, a comment opener or an identifier assignment hides no declaration.
     */
    static Map<String, String> declaredNames(List<String> lines) {
        Map<String, String> declared = new TreeMap<>();
        List<List<String>> lineTokens = Stream.concat(lines.stream(),
                        logicalLines(lines).stream().map(line -> line.code().text()))
                .map(UnresolvedReferences::tokens)
                .toList();
        for (List<String> tokens : lineTokens) {
            for (int index = 0; index + 1 < tokens.size(); index++) {
                String keyword = tokens.get(index).toLowerCase(Locale.ROOT);
                if (keyword.equals("!const") || keyword.equals("!var")) {
                    declared.putIfAbsent(tokens.get(index + 1), keyword);
                }
            }
        }
        lineTokens.stream().flatMap(List::stream).filter(token -> CONSTANT_NAME.matcher(token).matches())
                .forEach(token -> declared.putIfAbsent(token, "!const or !var"));
        return declared;
    }

    private static void report(Located text, Set<String> defined, String displayPath, Problems problems,
            Set<String> reported) {
        Matcher reference = REFERENCE.matcher(text.text());
        while (reference.find()) {
            String name = reference.group(1);
            if (!defined.contains(name)) {
                reported.add(name);
                problems.add(displayPath + ":" + text.lineAt(reference.start()), reference.group()
                        + " is not substituted: environment variables are not read, and no !const or !var defines "
                        + name + " before this line");
            }
        }
    }

    /**
     * StructurizrDslParser.preProcessLines: physical lines joined at a trailing backslash, and a text block taken out
     * of the line that holds it. An incomplete line at the end of the file is dropped, as the parser drops it.
     */
    private static List<LogicalLine> logicalLines(List<String> physical) {
        List<LogicalLine> logical = new ArrayList<>();
        Located.Builder buffer = new Located.Builder();
        boolean complete = true;
        boolean inTextBlock = false;
        for (int index = 0; index < physical.size(); index++) {
            String line = physical.get(index);
            int number = index + 1;
            if (inTextBlock) {
                if (line.endsWith(TEXT_BLOCK)) {
                    buffer.append(TEXT_BLOCK, number);
                    inTextBlock = false;
                    complete = true;
                } else {
                    // The parser also drops the block's indentation, which holds no reference.
                    buffer.append(line, number);
                    buffer.append("\n", number);
                }
            } else if (!COMMENT.matcher(line).matches() && line.endsWith(CONTINUATION)) {
                buffer.append(line.substring(0, line.length() - CONTINUATION.length()), number);
                complete = false;
            } else if (!COMMENT.matcher(line).matches() && line.endsWith(TEXT_BLOCK)) {
                buffer.append(line, number);
                complete = false;
                inTextBlock = true;
            } else if (complete) {
                buffer.append(line, number);
            } else {
                buffer.append(line.stripLeading(), number);
                complete = true;
            }
            if (complete) {
                logical.add(LogicalLine.of(buffer.build()));
                buffer = new Located.Builder();
            }
        }
        return logical;
    }

    /**
     * StructurizrDslParser's Tokenizer: whitespace separates tokens, double quotes enclose one, and a quote after a
     * backslash belongs to the token (the backslash too).
     */
    private static List<String> tokens(String line) {
        List<String> tokens = new ArrayList<>();
        String text = line.trim();
        boolean inToken = false;
        boolean quoted = false;
        StringBuilder token = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            if (!inToken) {
                if (!Character.isWhitespace(c)) {
                    inToken = true;
                    quoted = c == '"';
                    token = new StringBuilder();
                    if (!quoted) {
                        token.append(c);
                    }
                }
            } else if (c == '"' && text.charAt(index - 1) == '\\') {
                token.append(c);
            } else if (quoted ? c == '"' : Character.isWhitespace(c)) {
                tokens.add(token.toString());
                inToken = false;
            } else {
                token.append(c);
            }
        }
        if (inToken) {
            tokens.add(token.toString());
        }
        return tokens;
    }

    /** The parser reads {@code x = <rest>} as an identifier for whatever {@code <rest>} creates. */
    private static List<String> withoutIdentifierAssignment(List<String> tokens) {
        return tokens.size() > 1 && "=".equals(tokens.get(1)) ? tokens.subList(2, tokens.size()) : tokens;
    }

    /** One line as the parser reads it, and the text block it held, if any. */
    private record LogicalLine(Located code, Optional<Located> textBlock) {

        /**
         * The parser splits a line that ends with {@code """} at its markers: the text before the first marker is the
         * line, and the text up to the next marker, without its last character, is the block. That character is the
         * line end before the closing marker, unless the opening line held a second marker of its own.
         */
        static LogicalLine of(Located line) {
            String text = line.text();
            if (!text.endsWith(TEXT_BLOCK)) {
                return new LogicalLine(line, Optional.empty());
            }
            int open = text.indexOf(TEXT_BLOCK);
            int start = open + TEXT_BLOCK.length();
            int close = text.indexOf(TEXT_BLOCK, start);
            if (close == -1) {
                return new LogicalLine(line, Optional.empty());
            }
            return new LogicalLine(line.slice(0, open), Optional.of(line.slice(start, Math.max(start, close - 1))));
        }
    }

    /** Text that knows, for each of its characters, the physical line it came from. */
    private record Located(String text, List<Integer> lines) {

        Located slice(int from, int to) {
            return new Located(text.substring(from, to), lines.subList(from, to));
        }

        int lineAt(int offset) {
            return lines.get(offset);
        }

        static final class Builder {

            private final StringBuilder text = new StringBuilder();
            private final List<Integer> lines = new ArrayList<>();

            void append(String part, int line) {
                text.append(part);
                for (int index = 0; index < part.length(); index++) {
                    lines.add(line);
                }
            }

            Located build() {
                return new Located(text.toString(), List.copyOf(lines));
            }
        }
    }
}
