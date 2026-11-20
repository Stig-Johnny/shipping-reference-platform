package io.github.stigjohnny.docs.check;

import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The names a Java source declares for classes, records, interfaces and enums that read as test classes, from its code
 * alone, as Java reads it: eligible Unicode escapes are translated first; then comments, string and character literals
 * and text blocks, escapes included, are blanked out, so a name that only stands in one of them declares nothing; and
 * an identifier is read whole, so {@code GhostTest$Helper} is not {@code GhostTest}. It reads declarations, not a parse
 * tree: nested and local classes count, and a file that declares no such name, an empty one for example, declares none.
 */
final class JavaDeclarations {

    private static final Pattern DECLARATION = Pattern.compile("(?<!\\p{javaJavaIdentifierPart})"
            + "(?:class|record|interface|enum)\\s+(\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)");

    private JavaDeclarations() {
    }

    static Set<String> testClasses(String source) {
        Set<String> names = new TreeSet<>();
        Matcher declaration = DECLARATION.matcher(code(unicodeTranslated(source)));
        while (declaration.find()) {
            if (References.TEST.matcher(declaration.group(1)).matches()) {
                names.add(declaration.group(1));
            }
        }
        return names;
    }

    /**
     * The source with its Unicode escapes translated, as the Java compiler does before anything else: a backslash
     * starts an escape when an even number of backslashes precedes it, and one or more u and four hex digits follow.
     */
    static String unicodeTranslated(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        while (i < source.length()) {
            int digits = escapeDigits(source, i);
            if (digits > 0) {
                out.append((char) Integer.parseInt(source.substring(digits, digits + 4), 16));
                i = digits + 4;
            } else {
                out.append(source.charAt(i));
                i++;
            }
        }
        return out.toString();
    }

    /** Where the four hex digits of an eligible Unicode escape at {@code i} start, or 0 when none starts there. */
    private static int escapeDigits(String source, int i) {
        if (source.charAt(i) != '\\' || i + 1 >= source.length() || source.charAt(i + 1) != 'u') {
            return 0;
        }
        int before = 0;
        for (int k = i - 1; k >= 0 && source.charAt(k) == '\\'; k--) {
            before++;
        }
        if (before % 2 != 0) {
            return 0;
        }
        int j = i + 1;
        while (j < source.length() && source.charAt(j) == 'u') {
            j++;
        }
        if (j + 4 > source.length()) {
            return 0;
        }
        for (int k = j; k < j + 4; k++) {
            if (Character.digit(source.charAt(k), 16) < 0) {
                return 0;
            }
        }
        return j;
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
                i = textBlockEnd(source, i + 3);
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

    /** Where a text block whose content starts at {@code start} ends: after its closing delimiter, escapes skipped. */
    private static int textBlockEnd(String source, int start) {
        int i = start;
        while (i < source.length()) {
            if (source.charAt(i) == '\\') {
                i += 2;
            } else if (source.startsWith("\"\"\"", i)) {
                return i + 3;
            } else {
                i++;
            }
        }
        return source.length();
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
