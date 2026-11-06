package io.github.stigjohnny.c4contractkit.api;

import java.io.Serial;
import java.util.List;

/**
 * The C4 model is invalid, or it uses a construct that the restricted parser rejects (for example {@code !include},
 * {@code !script} or a theme). The exception lists every problem found, not only the first.
 */
public final class ModelException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Not serialized: the message carries the same problems. */
    private final transient List<String> problems;

    ModelException(String modelPath, List<String> problems) {
        super(message(modelPath, problems));
        this.problems = List.copyOf(problems);
    }

    /**
     * Every problem found, sorted. Each problem starts with the elements it concerns, by DSL identifier where the model
     * gives one (for example {@code "quotes: contract.status is missing"} or
     * {@code "quotes -> tenants: contract.mode 'MUST' is neither REQUIRED nor PERMITTED"}).
     *
     * @return the problems, never empty
     */
    public List<String> problems() {
        return problems;
    }

    private static String message(String modelPath, List<String> problems) {
        StringBuilder message = new StringBuilder("The C4 model ").append(modelPath).append(" is invalid:");
        for (String problem : problems) {
            message.append("\n  - ").append(problem);
        }
        return message.toString();
    }
}
