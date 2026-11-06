package io.github.stigjohnny.c4contractkit.model;

import java.util.ArrayList;
import java.util.List;

/** Collects model problems, each as {@code "<elements>: <message>"}, so one exception can list all of them. */
final class Problems {

    private final List<String> problems = new ArrayList<>();

    void add(String elements, String message) {
        problems.add(elements + ": " + message);
    }

    void throwIfAny() throws InvalidModelException {
        if (!problems.isEmpty()) {
            throw new InvalidModelException(problems);
        }
    }
}
