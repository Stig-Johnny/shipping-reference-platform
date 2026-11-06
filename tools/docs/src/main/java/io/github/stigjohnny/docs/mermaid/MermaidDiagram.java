package io.github.stigjohnny.docs.mermaid;

import java.util.List;

/**
 * A validated diagram: the definition the site renders and the committed files contain, and the nodes and edges it
 * draws, which the text equivalent lists.
 */
public record MermaidDiagram(String viewKey, String definition, List<Node> nodes, List<Edge> edges) {

    public MermaidDiagram {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    /** A node: the model element with this ID, and the label the diagram shows. */
    public record Node(String id, String label) {
    }

    /**
     * An edge in drawing order, with the model relationship it shows and its number in the relationship table: the
     * step's order in a dynamic view, its position among the view's relationships otherwise.
     */
    public record Edge(String from, String to, Arrow arrow, String label, String relationshipId, String number) {
    }

    /** The three line patterns: the legend explains each one in words. */
    public enum Arrow {
        /** A relationship that the Java check does not evaluate: people, systems, containers. */
        SOLID("-->", "solid line"),
        /** A REQUIRED module dependency: the code must reference it. */
        REQUIRED("==>", "thick line"),
        /** A PERMITTED module dependency, or a planned relationship. */
        DOTTED("-.->", "dotted line");

        private final String token;
        private final String words;

        Arrow(String token, String words) {
            this.token = token;
            this.words = words;
        }

        public String token() {
            return token;
        }

        public String words() {
            return words;
        }
    }
}
