package io.github.stigjohnny.c4contractkit.sources;

import io.github.stigjohnny.c4contractkit.sources.SourceLexer.Token;
import io.github.stigjohnny.c4contractkit.sources.SourceLexer.TokenType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Partial static lint over a restricted TypeScript/TSX import grammar. The forms below describe recognized syntax,
 * not complete coverage: loaders after spread syntax can yield neither imports nor refusals (ADR 0014).
 *
 * <p>Read, each with the line of its specifier: import declarations ({@code import a from 'x'}, {@code import { a }
 * from 'x'}, {@code import * as a from 'x'}, {@code import 'x'}), type-only imports, re-exports ({@code export * from},
 * {@code export { a } from}, their type-only forms) and {@code import('x')} with one plain string literal,
 * types included. Refused: {@code import()} with anything else, {@code require}, {@code import.meta} other than
 * {@code import.meta.env}, import-equals declarations, import attributes, a module declaration with a module name
 * ({@code module 'x'}, with or without {@code declare}), references to {@code eval}, {@code Function} and a
 * {@code constructor} property, workers ({@code Worker}, {@code SharedWorker}, {@code importScripts},
 * {@code serviceWorker}), {@code setTimeout} and {@code setInterval} unless directly passed a function literal,
 * triple-slash directives and JSX pragmas, a backslash escape in a specifier or property key, and whatever the lexer
 * cannot read with certainty. Loader names are checked equally as identifiers and quoted keys. Computed keys other
 * than a plain string or number are refused, including templates and keys held in constant bindings.
 * Words inside strings, comments, templates, regular expressions and JSX text are never read as code, and neither are
 * property names ({@code a.import}, <code>{ require: 1 }</code>).
 */
final class ImportScanner {

    static final String DYNAMIC_IMPORT = "import() is supported only with one plain string literal: the module it "
            + "loads must be known without running the code";
    static final String REQUIRE = "require is not supported: modules are loaded with import declarations only";
    static final String IMPORT_META = "import.meta is supported only as import.meta.env: import.meta.glob, "
            + "import.meta.url and import.meta.resolve locate modules or files at run time";
    static final String IMPORT_EQUALS = "import ... = (an import-equals declaration) is not supported";
    static final String ATTRIBUTES = "import attributes (with or assert) are not supported";
    static final String DECLARE_MODULE = "a module declaration with a module name (module 'x') is not supported: it "
            + "declares a module that no file of the web root is";
    static final String EVAL = "eval is not supported: the code it runs could load modules that the check never sees";
    static final String FUNCTION = "the Function constructor is not supported: the code it builds could load modules "
            + "that the check never sees";
    static final String WORKER = "a worker (Worker, SharedWorker, importScripts, serviceWorker) is not supported: it "
            + "loads a script from a URL at run time";
    static final String CONSTRUCTOR_CALL = "a call of a constructor property (x.constructor(...)) is not supported: it "
            + "can reach the Function constructor";
    static final String STRING_TIMER = "setTimeout and setInterval with a string are not supported: the string is "
            + "code the check never reads";
    static final String TIMER_REFERENCE = "a timer reference is not supported unless directly called with a "
            + "function literal";
    static final String CONSTRUCTOR_REFERENCE = "a constructor property reference is not supported: it can reach "
            + "Function";
    static final String PROPERTY_ESCAPE = "a property key with a backslash escape is not supported";
    static final String COMPUTED_PROPERTY = "a computed property key must be one plain string or number literal: "
            + "the reader does not evaluate keys or follow constant bindings";

    /** Names that load a script from a URL at run time, refused wherever they appear. */
    private static final Set<String> WORKERS = Set.of("Worker", "SharedWorker", "importScripts",
            "serviceWorker");
    static final String IMPORT_CLAUSE =
            "unsupported import declaration: expected from and a string after the import clause";
    static final String EXPORT_STAR = "unsupported export declaration: expected from and a string after export *";
    static final String EXPORT_FROM = "unsupported export declaration: expected a string after from";
    static final String ESCAPE = "a module specifier with a backslash escape is not supported";
    static final String CSS_IMPORT = "CSS @import is not supported: link each style sheet from index.html";
    static final String CSS_ESCAPED_AT_RULE = "a CSS at-rule whose name holds a backslash escape is not supported: "
            + "the reader does not decode escapes";

    /** {@code @import} in any ASCII letter case, wherever it is: in comments and strings too. */
    private static final Pattern CSS_IMPORT_RULE = Pattern.compile("@import", Pattern.CASE_INSENSITIVE);
    /** An at-keyword with a backslash in its name, such as {@code @\69mport}: an escape could spell import. */
    private static final Pattern CSS_ESCAPED_NAME = Pattern.compile("@[A-Za-z0-9_-]*\\\\");

    /** What one file imports and what it refuses, each in source order; a refusal found twice is listed once. */
    record Scan(List<SourceImport> imports, List<Refusal> refusals) {

        Scan {
            imports = List.copyOf(imports);
            refusals = refusals.stream().distinct().sorted(Comparator.comparingInt(Refusal::line)).toList();
        }
    }

    /** What import.meta may name in a test: the location of the file, which no module is. */
    private static final Set<String> TEST_IMPORT_META = Set.of("url", "dirname", "filename");

    private final List<Token> tokens;
    private final boolean test;
    private final List<SourceImport> imports = new ArrayList<>();
    private final List<Refusal> refusals = new ArrayList<>();

    private ImportScanner(List<Token> tokens, boolean test) {
        this.tokens = tokens;
        this.test = test;
    }

    /**
     * Scans one TypeScript file.
     *
     * @param jsx whether the file is {@code .tsx}, where {@code <} can start a JSX element
     */
    static Scan scan(String text, boolean jsx) {
        return scan(text, jsx, false);
    }

    /**
     * Scans one TypeScript file.
     *
     * @param jsx whether the file is {@code .tsx}, where {@code <} can start a JSX element
     * @param test whether the file is a test, which runs in Node.js and may locate files with {@code import.meta.url},
     *     {@code import.meta.dirname} and {@code import.meta.filename}
     */
    static Scan scan(String text, boolean jsx, boolean test) {
        List<Token> tokens = new ArrayList<>();
        List<Refusal> lexical = new ArrayList<>();
        int stoppedAt = Integer.MAX_VALUE;
        try {
            SourceLexer.tokenize(text, jsx, tokens, lexical);
        } catch (SourceLexer.Stop stop) {
            lexical.add(new Refusal(stop.line(), stop.reason()));
            stoppedAt = stop.line();
        }
        ImportScanner scanner = new ImportScanner(tokens, test);
        scanner.read();
        List<Refusal> refusals = new ArrayList<>(lexical);
        // After the lexer stopped, the last statement it read may be cut off: refuse it only for the lexer's reason.
        int cutOff = stoppedAt;
        scanner.refusals.stream().filter(refusal -> refusal.line() < cutOff).forEach(refusals::add);
        return new Scan(scanner.imports, refusals);
    }

    /**
     * Scans one style sheet, in which only {@code @import} could make it depend on another file. The raw text is
     * searched, so that no comment or string can hide the rule: a style sheet that names {@code @import} anywhere,
     * or holds an at-rule whose name holds an escape, is refused.
     */
    static List<Refusal> scanStyle(String text) {
        List<Refusal> refusals = new ArrayList<>();
        Matcher rule = CSS_IMPORT_RULE.matcher(text);
        while (rule.find()) {
            refusals.add(new Refusal(lineOf(text, rule.start()), CSS_IMPORT));
        }
        Matcher escaped = CSS_ESCAPED_NAME.matcher(text);
        while (escaped.find()) {
            refusals.add(new Refusal(lineOf(text, escaped.start()), CSS_ESCAPED_AT_RULE));
        }
        return refusals.stream().distinct().sorted(Comparator.comparingInt(Refusal::line)).toList();
    }

    private static int lineOf(String text, int index) {
        return 1 + (int) text.substring(0, index).chars().filter(c -> c == '\n').count();
    }

    private void read() {
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (punctuator(i, "[")) {
                computedProperty(i);
            }
            // Quoted bindings, object keys and bracket keys share exactly the same capability checks as names.
            boolean property = token.type() == TokenType.STRING && (punctuator(i + 1, ":")
                    || punctuator(i + 1, "(") || (punctuator(i - 1, "[") && punctuator(i + 1, "]")));
            if (token.type() != TokenType.IDENTIFIER && !property) {
                continue;
            }
            if (property && token.special()) {
                refuse(token, PROPERTY_ESCAPE);
                continue;
            }
            // Refused even as properties (globalThis.eval, window.Function(...)): what they run is never read.
            if (token.text().equals("eval")) {
                refuse(token, EVAL);
                continue;
            }
            // No call-shape test: aliases, .call/.apply/.bind and even type uses are refused conservatively.
            if (token.text().equals("Function")) {
                refuse(token, FUNCTION);
                continue;
            }
            if (WORKERS.contains(token.text())) {
                refuse(token, WORKER);
                continue;
            }
            if (token.text().equals("constructor") && (property || !methodDeclaration(i))) {
                refuse(token, punctuator(i - 1, ".") && punctuator(i + 1, "(")
                        ? CONSTRUCTOR_CALL : CONSTRUCTOR_REFERENCE);
                continue;
            }
            if (token.text().equals("setTimeout") || token.text().equals("setInterval")) {
                if (punctuator(i + 1, "(") && (type(i + 2, TokenType.STRING) || type(i + 2, TokenType.TEMPLATE))) {
                    refuse(token, STRING_TIMER);
                } else if (property || !punctuator(i + 1, "(") || !functionLiteral(i + 2)) {
                    refuse(token, TIMER_REFERENCE);
                }
                continue;
            }
            if (property || isPropertyName(i)) {
                continue;
            }
            switch (token.text()) {
                case "import" -> importAt(i);
                case "export" -> exportAt(i);
                case "require" -> refuse(token, REQUIRE);
                case "module" -> {
                    if (type(i + 1, TokenType.STRING)) {
                        refuse(token, DECLARE_MODULE);
                    }
                }
                default -> {
                    // Any other identifier names no module.
                }
            }
        }
    }

    /** Refuses recognized non-literal computed access/binding keys, including const aliases and template literals. */
    private void computedProperty(int open) {
        int close = closing(open, "[", "]");
        boolean binding = punctuator(close + 1, ":") || punctuator(close + 1, "(");
        if (!tokens.get(open).special() && !binding) {
            return; // An array expression: its elements still get scanned as ordinary code.
        }
        if (close == open + 1) {
            return; // TypeScript's T[]; an empty access expression is not valid code.
        }
        if (close != open + 2 || !(type(open + 1, TokenType.STRING) || type(open + 1, TokenType.NUMBER))) {
            refuse(tokens.get(open), COMPUTED_PROPERTY);
        }
    }

    private boolean methodDeclaration(int name) {
        return !punctuator(name - 1, ".") && punctuator(name + 1, "(")
                && punctuator(closing(name + 1, "(", ")") + 1, "{");
    }

    /** A visible function expression cannot be a timer's string argument; aliases are deliberately unsupported. */
    private boolean functionLiteral(int start) {
        int first = identifier(start, "async") ? start + 1 : start;
        if (identifier(first, "function")) {
            int parameters = punctuator(first + 1, "*") ? first + 2 : first + 1;
            if (type(parameters, TokenType.IDENTIFIER)) {
                parameters++;
            }
            if (!punctuator(parameters, "(")) {
                return false;
            }
            int body = closing(parameters, "(", ")") + 1;
            if (!punctuator(body, "{")) {
                return false;
            }
            int after = closing(body, "{", "}") + 1;
            return punctuator(after, ",") || punctuator(after, ")");
        }
        return (type(first, TokenType.IDENTIFIER) && punctuator(first + 1, "=>"))
                || (punctuator(first, "(") && punctuator(closing(first, "(", ")") + 1, "=>"));
    }

    /** Matching delimiters only; the contents are never evaluated. */
    private int closing(int open, String left, String right) {
        int depth = 0;
        for (int i = open; i < tokens.size(); i++) {
            if (punctuator(i, left)) {
                depth++;
            } else if (punctuator(i, right) && --depth == 0) {
                return i;
            }
        }
        return -2;
    }

    /** {@code a.import}, {@code a?.require} and <code>{ import: 1 }</code> name a property, not a module. */
    private boolean isPropertyName(int index) {
        return punctuator(index - 1, ".") || punctuator(index + 1, ":");
    }

    private void importAt(int i) {
        Token keyword = tokens.get(i);
        if (punctuator(i + 1, "(")) {
            if (type(i + 2, TokenType.STRING) && punctuator(i + 3, ")")) {
                record(tokens.get(i + 2), ImportKind.DYNAMIC);
            } else {
                refuse(keyword, DYNAMIC_IMPORT);
            }
            return;
        }
        if (punctuator(i + 1, ".")) {
            boolean env = identifier(i + 2, "meta") && punctuator(i + 3, ".") && identifier(i + 4, "env");
            boolean location = test && identifier(i + 2, "meta") && punctuator(i + 3, ".")
                    && type(i + 4, TokenType.IDENTIFIER) && TEST_IMPORT_META.contains(tokens.get(i + 4).text());
            if (!env && !location) {
                refuse(keyword, IMPORT_META);
            }
            return;
        }
        if (type(i + 1, TokenType.STRING)) {
            record(tokens.get(i + 1), ImportKind.SIDE_EFFECT);
            attributes(i + 2);
            return;
        }
        int j = i + 1;
        boolean typeOnly = identifier(j, "type") && !identifier(j + 1, "from") && !punctuator(j + 1, ",")
                && !punctuator(j + 1, "=");
        if (typeOnly) {
            j++;
        }
        if (type(j, TokenType.IDENTIFIER)) {
            if (punctuator(j + 1, "=")) {
                refuse(keyword, IMPORT_EQUALS);
                return;
            }
            j++;
            if (punctuator(j, ",")) {
                j++;
            }
        }
        if (punctuator(j, "{")) {
            j = afterBraces(j);
        } else if (punctuator(j, "*")) {
            j = identifier(j + 1, "as") && type(j + 2, TokenType.IDENTIFIER) ? j + 3 : -1;
        }
        if (j >= 0 && identifier(j, "from") && type(j + 1, TokenType.STRING)) {
            record(tokens.get(j + 1), typeOnly ? ImportKind.TYPE_IMPORT : ImportKind.IMPORT);
            attributes(j + 2);
        } else {
            refuse(keyword, IMPORT_CLAUSE);
        }
    }

    private void exportAt(int i) {
        Token keyword = tokens.get(i);
        int j = i + 1;
        boolean typeOnly = identifier(j, "type") && (punctuator(j + 1, "{") || punctuator(j + 1, "*"));
        if (typeOnly) {
            j++;
        }
        ImportKind kind = typeOnly ? ImportKind.TYPE_RE_EXPORT : ImportKind.RE_EXPORT;
        if (punctuator(j, "*")) {
            j++;
            if (identifier(j, "as")) {
                j = type(j + 1, TokenType.IDENTIFIER) || type(j + 1, TokenType.STRING) ? j + 2 : -1;
            }
            if (j >= 0 && identifier(j, "from") && type(j + 1, TokenType.STRING)) {
                record(tokens.get(j + 1), kind);
                attributes(j + 2);
            } else {
                refuse(keyword, EXPORT_STAR);
            }
        } else if (punctuator(j, "{")) {
            int k = afterBraces(j);
            if (k >= 0 && identifier(k, "from")) {
                if (type(k + 1, TokenType.STRING)) {
                    record(tokens.get(k + 1), kind);
                    attributes(k + 2);
                } else {
                    refuse(keyword, EXPORT_FROM);
                }
            }
        }
        // Any other export declares something local and names no module.
    }

    /** The index after the {@code }} that closes the braces at {@code open}, or -1 if they do not close plainly. */
    private int afterBraces(int open) {
        for (int k = open + 1; k < tokens.size(); k++) {
            Token token = tokens.get(k);
            if (token.type() == TokenType.PUNCTUATOR) {
                switch (token.text()) {
                    case "}" -> {
                        return k + 1;
                    }
                    case "{", "(", ";" -> {
                        return -1;
                    }
                    default -> {
                        // Commas and other separators of the binding list.
                    }
                }
            }
        }
        return -1;
    }

    private void attributes(int index) {
        if ((identifier(index, "with") || identifier(index, "assert")) && punctuator(index + 1, "{")) {
            refuse(tokens.get(index), ATTRIBUTES);
        }
    }

    private void record(Token specifier, ImportKind kind) {
        if (specifier.special()) {
            refuse(specifier, ESCAPE);
        } else {
            imports.add(new SourceImport(specifier.line(), specifier.text(), kind));
        }
    }

    private void refuse(Token at, String reason) {
        refusals.add(new Refusal(at.line(), reason));
    }

    private @Nullable Token at(int index) {
        return index >= 0 && index < tokens.size() ? tokens.get(index) : null;
    }

    private boolean type(int index, TokenType type) {
        Token token = at(index);
        return token != null && token.type() == type;
    }

    private boolean identifier(int index, String name) {
        Token token = at(index);
        return token != null && token.type() == TokenType.IDENTIFIER && token.text().equals(name);
    }

    private boolean punctuator(int index, String text) {
        Token token = at(index);
        return token != null && token.type() == TokenType.PUNCTUATOR && token.text().equals(text);
    }
}
