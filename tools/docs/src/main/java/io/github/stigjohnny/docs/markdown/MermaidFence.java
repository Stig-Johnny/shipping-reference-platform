package io.github.stigjohnny.docs.markdown;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One Mermaid fence of a document as data (ADR 0024): a fenced code block, at any depth, whose info string's first
 * word is {@code mermaid} in any letter case.
 *
 * @param line the line its opening fence is on, counted from 1
 * @param info its info string, stripped
 * @param firstLine its first non-blank line, stripped, which names the diagram's type; empty when it has none
 * @param labelled whether the block right before it, in the same container, is the unchecked-design label: a paragraph
 *     whose first inline is strong emphasis that holds exactly {@link #LABEL}
 */
public record MermaidFence(int line, String info, String firstLine, boolean labelled) {

    /** The words that label a sequence or state diagram as one that no test compares with the code. */
    public static final String LABEL = "Unchecked design.";

    /** The info string of a Mermaid fence, as GitHub draws it and the site shows its source. */
    public static final String INFO = "mermaid";

    private static final Pattern FLOWCHART_HEADER = Pattern.compile("(?:flowchart|graph)(?: (?:TB|TD|BT|RL|LR))?");

    /** The diagram types that a Mermaid fence in current prose may have, as its first line names them exactly. */
    public enum Type {
        FLOWCHART,
        SEQUENCE,
        STATE;

        /** Whether the type shows behaviour, so that a test compares it with the code or the label disclaims it. */
        public boolean behavioural() {
            return this != FLOWCHART;
        }

        /** The type that a first line names exactly, if it names one. */
        public static Optional<Type> of(String firstLine) {
            return switch (firstLine) {
                case "sequenceDiagram" -> Optional.of(SEQUENCE);
                case "stateDiagram", "stateDiagram-v2" -> Optional.of(STATE);
                default -> FLOWCHART_HEADER.matcher(firstLine).matches() ? Optional.of(FLOWCHART) : Optional.empty();
            };
        }
    }

    /** Whether an info string marks a Mermaid fence: its first word is mermaid, in any letter case. */
    public static boolean mermaid(String info) {
        String word = info.strip().split("\\s", 2)[0];
        return word.toLowerCase(Locale.ROOT).equals(INFO);
    }

    /** The diagram's type, if its first line names one exactly. */
    public Optional<Type> type() {
        return Type.of(firstLine);
    }

    /** The first non-blank line of a fence's text, stripped; empty when every line is blank. */
    static String firstLineOf(String literal) {
        return literal.lines().map(String::strip).filter(line -> !line.isEmpty()).findFirst().orElse("");
    }
}
