package io.github.stigjohnny.docs.check;

import io.github.stigjohnny.docs.DocsException;
import java.util.List;
import java.util.TreeSet;

/** Collects every problem first, so that one run reports all of them, then fails once. */
final class Problems {

    private final TreeSet<String> problems = new TreeSet<>();

    void add(String problem) {
        problems.add(problem);
    }

    List<String> list() {
        return List.copyOf(problems);
    }

    void throwIfAny(String what) {
        if (!problems.isEmpty()) {
            throw new DocsException(what + " (" + problems.size() + " problem" + (problems.size() == 1 ? "" : "s")
                    + "):\n  - " + String.join("\n  - ", problems));
        }
    }
}
