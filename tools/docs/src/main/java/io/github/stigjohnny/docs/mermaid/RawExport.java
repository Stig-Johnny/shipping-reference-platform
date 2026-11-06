package io.github.stigjohnny.docs.mermaid;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Structurizr 6.2.4's Mermaid flowchart output, read statement by statement. Every line must have one of the forms
 * the exporter writes; labels must have the exporter's HTML template exactly, and the text inside it may contain no
 * markup except the exporter's own line breaks. Anything else, a {@code click} line or an icon included, fails.
 */
final class RawExport {

    /** The exporter's outer frame around every diagram; it is not a C4 boundary, so the adapter drops it. */
    static final String FRAME = "diagram";

    private static final Pattern HEADER = Pattern.compile("graph (LR|RL|TB|TD|BT)");
    private static final Pattern DEFAULT_LINK_STYLE = Pattern.compile("linkStyle default fill:#[0-9a-fA-F]{6}");
    private static final Pattern SUBGRAPH = Pattern.compile("subgraph ([A-Za-z0-9_]+) \\[\"([^\"<>]*)\"]");
    private static final Pattern STYLE = Pattern.compile("style ([A-Za-z0-9_]+) (\\S+)");
    private static final Pattern BOX = Pattern.compile("([A-Za-z0-9_]+)\\[\"(.*)\"]");
    private static final Pattern CYLINDER = Pattern.compile("([A-Za-z0-9_]+)\\[\\(\"(.*)\"\\)]");
    private static final Pattern DASHED = Pattern.compile("([A-Za-z0-9_]+)-\\. \"(.*)\" \\.->([A-Za-z0-9_]+)");
    private static final Pattern SOLID = Pattern.compile("([A-Za-z0-9_]+)-- \"(.*)\" -->([A-Za-z0-9_]+)");
    private static final Pattern NODE_LABEL = Pattern.compile("<div style='font-weight: bold'>(.*?)</div>"
            + "<div style='font-size: 70%; margin-top: 0px'>(.*?)</div>"
            + "(?:<div style='font-size: 80%; margin-top:10px'>(.*?)</div>)?");
    private static final Pattern EDGE_LABEL = Pattern.compile("<div>(.*?)</div><div style='font-size: 70%'>(.*?)</div>");
    private static final String LINE_BREAK = "<br />";

    /** One statement of the export, in order. */
    sealed interface Statement {
    }

    record Open(String id, String label) implements Statement {
    }

    record Close() implements Statement {
    }

    record Style(String id, String pairs) implements Statement {
    }

    record NodeDef(String id, boolean cylinder, String name, String type, String description) implements Statement {
    }

    record EdgeDef(String from, String to, String description, String technology) implements Statement {
    }

    record Export(String direction, List<Statement> statements) {
    }

    private RawExport() {
    }

    static Export parse(String definition, String where) {
        String direction = null;
        List<Statement> statements = new ArrayList<>();
        int lineNumber = 0;
        for (String raw : definition.split("\n", -1)) {
            lineNumber++;
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            String at = where + ", exporter line " + lineNumber;
            if (direction == null) {
                Matcher header = HEADER.matcher(line);
                if (!header.matches()) {
                    throw new UnsafeDiagramException(at + ": expected a 'graph' header");
                }
                direction = header.group(1);
                continue;
            }
            statements.add(statement(line, at));
        }
        if (direction == null) {
            throw new UnsafeDiagramException(where + ": the exporter wrote nothing");
        }
        return new Export(direction, statements);
    }

    private static Statement statement(String line, String at) {
        if ("end".equals(line)) {
            return new Close();
        }
        if (DEFAULT_LINK_STYLE.matcher(line).matches()) {
            return new Style("", "");
        }
        Matcher m = SUBGRAPH.matcher(line);
        if (m.matches()) {
            return new Open(m.group(1), plain(m.group(2), at));
        }
        m = STYLE.matcher(line);
        if (m.matches()) {
            return new Style(m.group(1), m.group(2));
        }
        m = CYLINDER.matcher(line);
        if (m.matches()) {
            return node(m.group(1), true, m.group(2), at);
        }
        m = BOX.matcher(line);
        if (m.matches()) {
            return node(m.group(1), false, m.group(2), at);
        }
        m = DASHED.matcher(line);
        if (!m.matches()) {
            m = SOLID.matcher(line);
        }
        if (m.matches()) {
            Matcher label = EDGE_LABEL.matcher(m.group(2));
            if (!label.matches()) {
                throw new UnsafeDiagramException(at + ": a relationship label is not in the exporter's template");
            }
            return new EdgeDef(m.group(1), m.group(3), plain(label.group(1), at), plain(label.group(2), at));
        }
        throw new UnsafeDiagramException(at + ": unexpected statement; only the exporter's own forms are adapted");
    }

    private static NodeDef node(String id, boolean cylinder, String html, String at) {
        Matcher label = NODE_LABEL.matcher(html);
        if (!label.matches()) {
            throw new UnsafeDiagramException(at + ": an element label is not in the exporter's template");
        }
        String description = label.group(3);
        return new NodeDef(id, cylinder, plain(label.group(1), at), plain(label.group(2), at),
                description == null ? "" : plain(description, at));
    }

    /** The text inside an exporter label: its line breaks become spaces; any other markup fails. */
    private static String plain(String html, String at) {
        String text = html.replace(LINE_BREAK, " ");
        if (text.indexOf('<') >= 0 || text.indexOf('>') >= 0) {
            throw new UnsafeDiagramException(at + ": a label contains markup");
        }
        return text.replaceAll("\\s+", " ").strip();
    }
}
