package io.github.stigjohnny.c4contractkit.model;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The check of the model file before parsing. The restricted DSL has no constants, no variables, no
 * <code>${...}</code> substitution and no line continuations, and a file that holds any of them is not parsed.
 *
 * <p>With ENVIRONMENT off, StructurizrDslParser 6.2.4 still substitutes the constants and variables that a file
 * defines, and following what it substitutes takes a parser of its own. So the check reads every physical line as
 * text, comments and text blocks too, and rejects what substitution needs, wherever it stands:
 * <ul>
 *   <li><code>${</code>, with or without a name and a closing brace;</li>
 *   <li>{@code !const} and {@code !var}, also inside a word, in any letter case that {@link String#equalsIgnoreCase}
 *       accepts, since the parser compares its keywords that way: <code>!con&#383;t</code>, with a long s, defines
 *       a constant too;</li>
 *   <li>a backslash as the last character of a line, which the parser joins to the next line without a separator:
 *       a line {@code !con\} and a line {@code st N x} define N.</li>
 * </ul>
 * It neither normalises Unicode nor decodes escapes, since the parser does neither: a fullwidth dollar sign or brace,
 * or the six characters <code>&#92;u0024</code>, are no syntax to it. Text blocks need none of these: the parser
 * keeps each in a constant of its own and puts it back itself. The cost is deliberate: a harmless comment or text
 * that holds one of them is rejected too.
 */
final class SourceLines {

    /** What opens a substitution in StructurizrDslParser 6.2.4. */
    static final String SUBSTITUTION = "${";

    private static final Pattern DEFINITION =
            Pattern.compile("!const|!var", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final String CONTINUATION = "\\";

    private static final String ANYWHERE =
            " anywhere in the file, comments and text blocks included: the restricted DSL has no ";

    private SourceLines() {
    }

    /**
     * Adds problems located as {@code <displayPath>:<line>}: one for a line that holds <code>${</code>, one for each
     * way a line spells {@code !const} or {@code !var}, and one for a line that ends in a backslash.
     *
     * @param lines the physical lines of the file, as {@link java.nio.file.Files#readAllLines} reads them for the
     *     parser
     * @return whether it added a problem
     */
    static boolean reject(List<String> lines, String displayPath, Problems problems) {
        boolean rejected = false;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            String place = displayPath + ":" + (index + 1);
            Matcher definition = DEFINITION.matcher(line);
            while (definition.find()) {
                problems.add(place, definition.group() + " is not supported" + ANYWHERE + "constants or variables");
                rejected = true;
            }
            if (line.contains(SUBSTITUTION)) {
                problems.add(place, SUBSTITUTION + " is not supported" + ANYWHERE + "${...} substitution");
                rejected = true;
            }
            if (line.endsWith(CONTINUATION)) {
                problems.add(place, "a backslash at the end of a line is not supported" + ANYWHERE
                        + "line continuations");
                rejected = true;
            }
        }
        return rejected;
    }
}
