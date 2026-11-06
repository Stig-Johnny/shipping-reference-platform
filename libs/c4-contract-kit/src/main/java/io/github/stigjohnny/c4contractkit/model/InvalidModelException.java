package io.github.stigjohnny.c4contractkit.model;

import java.io.Serial;
import java.util.List;

/** The model could not be parsed or broke at least one rule; {@link #problems()} lists every problem, sorted. */
public final class InvalidModelException extends Exception {

    /** Version 2 serializes the problems; a version 1 stream, which had none, is rejected. */
    @Serial
    private static final long serialVersionUID = 2L;

    /** An array, so that the problems are serialized with the exception; {@link #problems()} copies it. */
    private final String[] problems;

    InvalidModelException(List<String> problems) {
        super(String.join("\n", sorted(problems)));
        this.problems = sorted(problems).toArray(String[]::new);
    }

    public List<String> problems() {
        return List.of(problems);
    }

    private static List<String> sorted(List<String> problems) {
        return problems.stream().distinct().sorted().toList();
    }
}
