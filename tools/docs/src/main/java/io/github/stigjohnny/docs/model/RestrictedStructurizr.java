package io.github.stigjohnny.docs.model;

import com.structurizr.dsl.Features;
import com.structurizr.dsl.StructurizrDslParser;
import io.github.stigjohnny.docs.DocsException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The docs tool's one Structurizr parser factory (security/reviews/maven--structurizr-dsl--6.2.4.md and
 * maven--docs-tool-scope-extension.md).
 *
 * <p>The contract kit has a factory with the same restrictions, but it is internal to the kit, so this tool has its
 * own. Every parser it creates has all ten Structurizr features switched off before it parses: no environment
 * variables, no file system access beyond the model file, no HTTP or HTTPS, no {@code !include}, no plugins, no
 * scripts, no component finder, no documentation and no decision import. It never calls
 * {@code getHttpClient().allow(...)} and never uses the deprecated restricted mode. A test enumerates every
 * {@link Features} constant by reflection and asserts each one is off, so a feature added by a later version fails the
 * build instead of slipping in switched on.
 *
 * <p>Before parsing, the DSL text outside comments is rejected when it contains a directive that loads code or files
 * or a {@code ${NAME}} reference, which the parser would otherwise keep as literal text.
 */
final class RestrictedStructurizr {

    static final List<String> DISABLED_FEATURES = List.of(Features.ENVIRONMENT, Features.FILE_SYSTEM, Features.HTTP,
            Features.HTTPS, Features.INCLUDE, Features.PLUGINS, Features.SCRIPTS, Features.COMPONENT_FINDER,
            Features.DOCUMENTATION, Features.DECISIONS);

    private static final Pattern DIRECTIVE =
            Pattern.compile("(?i)!(include|script|plugin|docs|adrs|components)\\b|^workspace\\s+extends\\b");
    private static final Pattern REFERENCE = Pattern.compile("\\$\\{[^}]*}");

    private RestrictedStructurizr() {
    }

    static StructurizrDslParser newParser() {
        StructurizrDslParser parser = new StructurizrDslParser();
        for (String feature : DISABLED_FEATURES) {
            parser.getFeatures().disable(feature);
        }
        return parser;
    }

    /** Rejects directives and environment references in the DSL text, ignoring comments. */
    static void guard(String dsl, String displayPath) {
        boolean inBlockComment = false;
        int lineNumber = 0;
        for (String line : dsl.split("\n", -1)) {
            lineNumber++;
            String text = line.strip();
            if (inBlockComment) {
                inBlockComment = !text.contains("*/");
                continue;
            }
            if (text.startsWith("/*")) {
                inBlockComment = !text.contains("*/");
                continue;
            }
            if (text.startsWith("//") || text.startsWith("#")) {
                continue;
            }
            Matcher directive = DIRECTIVE.matcher(text);
            if (directive.find()) {
                throw new DocsException("%s:%d: '%s' is not allowed: the docs tool parses models with every Structurizr feature off"
                        .formatted(displayPath, lineNumber, directive.group()));
            }
            Matcher reference = REFERENCE.matcher(text);
            if (reference.find()) {
                throw new DocsException("%s:%d: %s is not allowed: environment variables are never read"
                        .formatted(displayPath, lineNumber, reference.group()));
            }
        }
    }
}
