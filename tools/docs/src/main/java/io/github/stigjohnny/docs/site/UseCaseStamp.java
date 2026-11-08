package io.github.stigjohnny.docs.site;

import io.github.stigjohnny.docs.Html;
import io.github.stigjohnny.docs.repo.TestEvidence;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What the build that generated the site observed of the acceptance tests that docs/capabilities/capabilities.yaml
 * names for a use case. It is green only when every named test passed: an empty list never is, and a named test that
 * failed, was skipped, ran nothing, has no report here, is ambiguous, is missing or is written in a language whose
 * reports this build does not read never counts as passed. It says that named tests passed, never that the use case
 * ran as its diagram draws it.
 */
record UseCaseStamp(Kind kind, List<Named> tests) {

    /** A Java test class's simple name; anything else is a file in another language. */
    private static final Pattern JAVA_CLASS = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

    UseCaseStamp {
        tests = List.copyOf(tests);
    }

    /** The stamp in words, with the badge that repeats it: the double rule passing, the failure colour failing. */
    enum Kind {
        PASSED("All passed", Badge.SHIPPED),
        FAILING("Some failing", Badge.FAILING),
        NOT_RUN("Not run or missing", Badge.NONE),
        PLANNED("No tests yet", Badge.NONE);

        private final String words;
        private final Badge badge;

        Kind(String words, Badge badge) {
            this.words = words;
            this.badge = badge;
        }

        String words() {
            return words;
        }

        Badge badge() {
            return badge;
        }
    }

    /** What this build observed of one named test, in words: for the list of tests and for the count of each state. */
    enum State {
        PASSED("passed", "passed"),
        FAILED("failed in this build", "failed"),
        SKIPPED("skipped in this build", "skipped"),
        EMPTY("ran no test in this build", "ran no test"),
        NO_REPORT("no report in this build", "not run in this build"),
        AMBIGUOUS("ambiguous: more than one test class has this name", "ambiguous"),
        NOT_READ("in the repository, but this build reads no TypeScript or JavaScript reports",
                "not read by this build"),
        MISSING("not in the repository yet", "not in the repository yet");

        private final String words;
        private final String counted;

        State(String words, String counted) {
            this.words = words;
            this.counted = counted;
        }
    }

    /** One named test and its state. */
    record Named(String test, State state) {
    }

    static UseCaseStamp of(List<String> acceptanceTests, TestEvidence evidence) {
        List<Named> tests = acceptanceTests.stream().map(test -> new Named(test, state(test, evidence))).toList();
        Kind kind;
        if (tests.isEmpty()) {
            kind = Kind.PLANNED;
        } else if (tests.stream().anyMatch(t -> t.state() == State.FAILED)) {
            kind = Kind.FAILING;
        } else if (tests.stream().allMatch(t -> t.state() == State.PASSED)) {
            kind = Kind.PASSED;
        } else if (tests.stream().allMatch(t -> t.state() == State.MISSING)) {
            kind = Kind.PLANNED;
        } else {
            kind = Kind.NOT_RUN;
        }
        return new UseCaseStamp(kind, tests);
    }

    private static State state(String test, TestEvidence evidence) {
        if (!JAVA_CLASS.matcher(test).matches()) {
            return evidence.otherTestExists(test) ? State.NOT_READ : State.MISSING;
        }
        if (evidence.passed(test)) {
            return State.PASSED;
        }
        if (evidence.ambiguous(test)) {
            return State.AMBIGUOUS;
        }
        Optional<TestEvidence.Result> result = evidence.result(test);
        if (!evidence.exists(test)) {
            // A report without a test class in the repository is not this repository's test.
            return State.MISSING;
        }
        if (result.isEmpty()) {
            return State.NO_REPORT;
        }
        TestEvidence.Result counts = result.get();
        if (counts.failures() > 0 || counts.errors() > 0) {
            return State.FAILED;
        }
        return counts.skipped() > 0 ? State.SKIPPED : State.EMPTY;
    }

    /** The badge, one sentence with the counts, and the named tests with their states in a disclosure. */
    String html() {
        return kind.badge().html(kind.words()) + " " + Html.escape(sentence()) + (tests.isEmpty() ? ""
                : "\n<details class=\"evidence-tests\"><summary>Named tests (" + tests.size() + ")</summary><p>"
                        + Html.escape(String.join("; ", tests.stream().map(t -> t.test() + ": " + t.state().words)
                                .toList())) + "</p></details>");
    }

    private String sentence() {
        int named = tests.size();
        long passed = count(State.PASSED);
        return switch (kind) {
            case PASSED -> named == 1 ? "Its one named test passed in this build."
                    : "All " + named + " named tests passed in this build.";
            case FAILING -> count(State.FAILED) + " of " + named + " named tests failed in this build"
                    + (passed > 0 ? "; " + passed + " passed." : ".");
            case NOT_RUN -> passed + " of " + named + " named tests passed in this build; " + String.join(", ",
                    others()) + ".";
            case PLANNED -> named == 0 ? "No acceptance test is named yet." : named == 1
                    ? "Its one named test is not in the repository yet."
                    : "None of its " + named + " named tests is in the repository yet.";
        };
    }

    /** How many named tests are in each state but passed, in the states' order. */
    private List<String> others() {
        List<String> out = new ArrayList<>();
        for (State state : State.values()) {
            long count = count(state);
            if (state != State.PASSED && count > 0) {
                out.add(count + " " + state.counted);
            }
        }
        return out;
    }

    private long count(State state) {
        return tests.stream().filter(t -> t.state() == state).count();
    }
}
