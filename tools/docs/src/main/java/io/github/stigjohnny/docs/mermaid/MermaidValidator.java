package io.github.stigjohnny.docs.mermaid;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The build-time validator that every Mermaid definition passes before it reaches a page, a committed Markdown file or
 * a {@code .mmd} file (security/reviews/vendored-js--mermaid--11.17.2.md, condition 4).
 *
 * <p>It rejects <code>%%&#123;</code> directives, {@code click}, {@code href} and {@code call}, <code>@&#123;</code>
 * node metadata (image nodes fetch their URL even in strict mode), {@code $$} math, the characters {@code <} and
 * {@code >}, and
 * {@code style}, {@code classDef} and {@code linkStyle} values other than hex colours, and more than
 * {@value #MAX_CHARACTERS} characters or {@value #MAX_EDGES} edges. A flowchart cannot draw an arrowhead without
 * {@code >}, so the one place {@code >} may appear is as the head of the three arrow tokens {@code -->},
 * {@code ==>} and {@code -.->}; every label is also checked for {@code <} and {@code >} on its own.
 *
 * <p>On top of those bans it accepts only an allowlist of statements, the ones the adapter writes: the flowchart
 * header, {@code accTitle} and {@code accDescr}, subgraphs, nodes with quoted plain-text labels in four shapes, edges
 * with the three arrow tokens and an optional quoted label, and colour statements. Anything else fails, so a construct
 * that later Mermaid versions add cannot slip through.
 */
public final class MermaidValidator {

    public static final int MAX_CHARACTERS = 50_000;
    public static final int MAX_EDGES = 500;

    private static final List<String> ARROWS = List.of("-.->", "-->", "==>");
    private static final Set<String> RESERVED = Set.of("end", "subgraph", "style", "classdef", "linkstyle", "class",
            "click", "href", "call", "flowchart", "graph", "direction", "acctitle", "accdescr", "default");
    private static final Set<String> COLOUR_KEYS = Set.of("fill", "stroke", "color");

    private static final String ID = "([A-Za-z0-9_]+)";
    private static final String LABEL = "\"([^\"]*)\"";
    private static final Pattern HEADER = Pattern.compile("flowchart (LR|RL|TB|TD|BT)");
    private static final Pattern ACCESSIBILITY = Pattern.compile("(accTitle|accDescr): (.+)");
    private static final Pattern SUBGRAPH = Pattern.compile("subgraph " + ID + " \\[" + LABEL + "]");
    private static final Pattern NODE = Pattern.compile(ID + "(\\[" + LABEL + "]|\\[\\(" + LABEL + "\\)]|\\("
            + LABEL + "\\)|\\(\\[" + LABEL + "]\\))");
    private static final Pattern EDGE = Pattern.compile(ID + " (-\\.->|-->|==>)(?:\\|" + LABEL + "\\|)? " + ID);
    private static final Pattern COLOURS = Pattern.compile("(style|classDef|linkStyle) (\\S+) (.+)");
    private static final Pattern HEX = Pattern.compile("#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})");
    private static final Pattern ENTITY = Pattern.compile("#[A-Za-z0-9]+;");

    private MermaidValidator() {
    }

    /** Returns the definition unchanged when it is acceptable; throws {@link UnsafeDiagramException} otherwise. */
    public static String validate(String definition, String where) {
        if (definition.length() > MAX_CHARACTERS) {
            throw fail(where, "it has " + definition.length() + " characters; the limit is " + MAX_CHARACTERS);
        }
        for (String banned : List.of("%%{", "@{", "$$", "<")) {
            if (definition.contains(banned)) {
                throw fail(where, "it contains '" + banned + "'");
            }
        }
        String withoutArrows = definition;
        for (String arrow : ARROWS) {
            withoutArrows = withoutArrows.replace(arrow, "");
        }
        if (withoutArrows.contains(">")) {
            throw fail(where, "it contains '>' outside an arrow");
        }
        int edges = 0;
        boolean header = false;
        int lineNumber = 0;
        for (String raw : definition.split("\n", -1)) {
            lineNumber++;
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            String at = "line " + lineNumber;
            if (!header) {
                if (!HEADER.matcher(line).matches()) {
                    throw fail(where, at + ": a definition starts with 'flowchart <direction>'");
                }
                header = true;
                continue;
            }
            if (statement(line, where, at)) {
                edges++;
                if (edges > MAX_EDGES) {
                    throw fail(where, "it has more than " + MAX_EDGES + " edges");
                }
            }
        }
        if (!header) {
            throw fail(where, "it is empty");
        }
        return definition;
    }

    /** Checks one statement; returns whether it is an edge. */
    private static boolean statement(String line, String where, String at) {
        if ("end".equals(line)) {
            return false;
        }
        Matcher matcher = ACCESSIBILITY.matcher(line);
        if (matcher.matches()) {
            text(matcher.group(2), where, at);
            return false;
        }
        matcher = SUBGRAPH.matcher(line);
        if (matcher.matches()) {
            id(matcher.group(1), where, at);
            text(matcher.group(2), where, at);
            return false;
        }
        matcher = NODE.matcher(line);
        if (matcher.matches()) {
            id(matcher.group(1), where, at);
            for (int group = 3; group <= 6; group++) {
                String label = matcher.group(group);
                if (label != null) {
                    text(label, where, at);
                }
            }
            return false;
        }
        matcher = EDGE.matcher(line);
        if (matcher.matches()) {
            id(matcher.group(1), where, at);
            id(matcher.group(4), where, at);
            String label = matcher.group(3);
            if (label != null) {
                text(label, where, at);
            }
            return true;
        }
        matcher = COLOURS.matcher(line);
        if (matcher.matches()) {
            colours(matcher.group(3), where, at);
            return false;
        }
        throw fail(where, at + ": '" + line + "' is not a statement the docs site publishes");
    }

    private static void id(String id, String where, String at) {
        if (RESERVED.contains(id.toLowerCase(Locale.ROOT))) {
            throw fail(where, at + ": '" + id + "' is a reserved word and cannot be an ID");
        }
    }

    /** A label or accessible text: plain text only. */
    private static void text(String text, String where, String at) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isISOControl(c) || "\"<>`|\\".indexOf(c) >= 0) {
                throw fail(where, at + ": a label contains the character U+%04X".formatted((int) c));
            }
        }
        for (String banned : List.of("%%", "$$", "@{")) {
            if (text.contains(banned)) {
                throw fail(where, at + ": a label contains '" + banned + "'");
            }
        }
        if (ENTITY.matcher(text).find()) {
            throw fail(where, at + ": a label contains an entity code");
        }
    }

    /** {@code key:value} pairs, comma-separated, where every key is a colour property and every value a hex colour. */
    private static void colours(String pairs, String where, String at) {
        for (String pair : pairs.split(",", -1)) {
            int colon = pair.indexOf(':');
            String key = colon < 0 ? pair : pair.substring(0, colon);
            String value = colon < 0 ? "" : pair.substring(colon + 1);
            if (!COLOUR_KEYS.contains(key) || !HEX.matcher(value).matches()) {
                throw fail(where, at + ": '" + pair + "' is not a hex colour");
            }
        }
    }

    private static UnsafeDiagramException fail(String where, String why) {
        return new UnsafeDiagramException("Mermaid definition of " + where + " rejected: " + why);
    }
}
