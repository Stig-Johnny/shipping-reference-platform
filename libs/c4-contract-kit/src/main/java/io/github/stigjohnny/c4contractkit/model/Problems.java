package io.github.stigjohnny.c4contractkit.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects model problems, each as {@code "<elements>: <message>"} (a parser error as the parser words it, with the
 * file and line), so that one exception lists all of them. The parser and the model checks share one collector.
 */
final class Problems {

    private final List<String> problems = new ArrayList<>();

    void add(String elements, String message) {
        problems.add(elements + ": " + message);
    }

    /**
     * Adds a problem after which nothing more can be checked.
     *
     * @return the exception to throw, which lists this problem and every one found before it
     */
    InvalidModelException fatal(String elements, String message) {
        return fatal(elements + ": " + message);
    }

    /** {@link #fatal(String, String)} for a parser error, which names its file and line itself. */
    InvalidModelException fatal(String parserError) {
        problems.add(parserError);
        return failure();
    }

    /** The exception that lists every problem found so far, for when nothing more can be checked. */
    InvalidModelException failure() {
        return new InvalidModelException(problems);
    }

    void throwIfAny() throws InvalidModelException {
        if (!problems.isEmpty()) {
            throw new InvalidModelException(problems);
        }
    }
}
