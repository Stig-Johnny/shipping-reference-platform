package io.github.stigjohnny.c4contractkit.model;

import java.io.Serial;
import java.util.List;

/** The model could not be parsed or broke at least one rule; {@link #problems()} lists every problem, sorted. */
public final class InvalidModelException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Not serialized: the message carries the same problems. */
    private final transient List<String> problems;

    InvalidModelException(List<String> problems) {
        super(String.join("\n", sorted(problems)));
        this.problems = sorted(problems);
    }

    public List<String> problems() {
        return problems;
    }

    private static List<String> sorted(List<String> problems) {
        return problems.stream().distinct().sorted().toList();
    }
}
