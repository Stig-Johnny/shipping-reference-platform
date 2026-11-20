package io.github.stigjohnny.docs.check;

import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The names a Java source declares for classes, records, interfaces and enums that end in Test, read from its code
 * alone: comments, string and character literals and text blocks are blanked out first, so a name that only stands in
 * one of them declares nothing. It reads declarations, not a parse tree; a file that declares no such name, an empty
 * one for example, declares no test.
 */
final class JavaDeclarations {

    private static final Pattern DECLARATION = Pattern.compile(
            "\\b(?:class|record|interface|enum)\\s+([A-Z][A-Za-z0-9]*Test)\\b");

    private JavaDeclarations() {
    }

    static Set<String> testClasses(String source) {
        Set<String> names = new TreeSet<>();
        Matcher declaration = DECLARATION.matcher(code(source));
        while (declaration.find()) {
            names.add(declaration.group(1));
        }
        return names;
    }

    /** The source with comments, literals and text blocks replaced by a space each, so that only code remains. */
    static String code(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int length = source.length();
        while (i < length) {
            if (source.startsWith("//", i)) {
                int end = source.indexOf('\n', i);
                i = end < 0 ? length : end;
            } else if (source.startsWith("/*", i)) {
                int end = source.indexOf("*/", i + 2);
                i = end < 0 ? length : end + 2;
            } else if (source.startsWith("\"\"\"", i)) {
                int end = source.indexOf("\"\"\"", i + 3);
                i = end < 0 ? length : end + 3;
            } else if (source.charAt(i) == '"' || source.charAt(i) == '\'') {
                i = literalEnd(source, i);
            } else {
                out.append(source.charAt(i));
                i++;
                continue;
            }
            out.append(' ');
        }
        return out.toString();
    }

    /** Where a string or character literal that starts at {@code start} ends: after its closing quote, or its line. */
    private static int literalEnd(String source, int start) {
        char quote = source.charAt(start);
        int i = start + 1;
        while (i < source.length() && source.charAt(i) != quote && source.charAt(i) != '\n') {
            i += source.charAt(i) == '\\' ? 2 : 1;
        }
        return Math.min(i + 1, source.length());
    }
}
