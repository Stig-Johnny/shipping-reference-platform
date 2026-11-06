package io.github.stigjohnny.c4contractkit.api;

import java.io.Serial;
import java.util.List;

/**
 * The C4 model is invalid, or it uses a construct that the restricted parser rejects (for example {@code !include},
 * {@code !script} or a theme). The exception lists every problem found, not only the first.
 */
public final class ModelException extends RuntimeException {

    /** Version 2 serializes the problems; a version 1 stream, which had none, is rejected. */
    @Serial
    private static final long serialVersionUID = 2L;

    /** An array, so that the problems are serialized with the exception; {@link #problems()} copies it. */
    private final String[] problems;

    ModelException(String modelPath, List<String> problems) {
        super(message(modelPath, problems));
        this.problems = problems.toArray(String[]::new);
    }

    /**
     * Every problem found, sorted. Each problem starts with the elements it concerns, by DSL identifier where the model
     * gives one (for example {@code "quotes: contract.status is missing"} or
     * {@code "quotes -> tenants: contract.mode 'MUST' is neither REQUIRED nor PERMITTED"}), or with its place in the
     * model file. The parser itself stops at the first line it rejects, and that problem, worded by the parser, names
     * the line.
     *
     * @return the problems, never empty
     */
    public List<String> problems() {
        return List.of(problems);
    }

    private static String message(String modelPath, List<String> problems) {
        StringBuilder message = new StringBuilder("The C4 model ").append(modelPath).append(" is invalid:");
        for (String problem : problems) {
            message.append("\n  - ").append(problem);
        }
        return message.toString();
    }
}
