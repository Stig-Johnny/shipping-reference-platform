package io.github.stigjohnny.c4contractkit.model;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the {@code ${NAME}} references that the parser leaves as text. With ENVIRONMENT off, the parser replaces a
 * reference only with a constant or variable defined before it ({@code !const}, {@code !constant}, {@code !var}) and
 * otherwise keeps it, silently. Such a reference wherever it stands (the workspace, an element, a group, a view, a
 * style) means the model expected an environment variable, and is a model problem.
 *
 * <p>The scan reads the source the way Structurizr DSL 6.2.4 does: the same reference and comment patterns, block
 * comments from a line starting with {@code /*} to a line ending with {@code *}{@code /}, definitions in source order,
 * and text blocks between {@code """} markers, whose lines are never comments.
 */
final class UnresolvedReferences {

    /** StructurizrDslParser.STRING_SUBSTITUTION_PATTERN: only these names are ever substituted. */
    private static final Pattern REFERENCE = Pattern.compile("\\$\\{([a-zA-Z0-9-_.]+?)}");

    /** StructurizrDslParser.COMMENT_PATTERN. */
    private static final Pattern COMMENT = Pattern.compile("^\\s*?(//|#).*$");

    /** A {@code !const}, {@code !constant} or {@code !var} line, and the name it defines. */
    private static final Pattern DEFINITION = Pattern.compile("^(?i:!const|!constant|!var)\\s+\"?([^\\s\"]+)");
    private static final String TEXT_BLOCK = "\"\"\"";

    private UnresolvedReferences() {
    }

    /** Adds one problem per unresolved reference, located as {@code <displayPath>:<line>}. */
    static void check(List<String> lines, String displayPath, Problems problems) {
        Set<String> defined = new HashSet<>();
        boolean inBlockComment = false;
        boolean inTextBlock = false;
        for (int index = 0; index < lines.size(); index++) {
            String line = index == 0 ? lines.get(index).replace("﻿", "") : lines.get(index);
            String trimmed = line.strip();
            if (!inTextBlock) {
                if (inBlockComment) {
                    inBlockComment = !trimmed.endsWith("*/");
                    continue;
                }
                if (COMMENT.matcher(line).matches()) {
                    continue;
                }
                if (trimmed.startsWith("/*")) {
                    inBlockComment = !trimmed.endsWith("*/");
                    continue;
                }
            }
            Matcher reference = REFERENCE.matcher(line);
            while (reference.find()) {
                if (!defined.contains(reference.group(1))) {
                    problems.add(displayPath + ":" + (index + 1), reference.group() + " is not substituted: "
                            + "environment variables are not read, and no !const or !var defines "
                            + reference.group(1) + " before this line");
                }
            }
            Matcher definition = DEFINITION.matcher(trimmed);
            if (definition.find()) {
                defined.add(definition.group(1));
            }
            if (markers(line) % 2 == 1) {
                inTextBlock = !inTextBlock;
            }
        }
    }

    /** How many text block markers a line holds. */
    private static int markers(String line) {
        return line.split(TEXT_BLOCK, -1).length - 1;
    }

}
