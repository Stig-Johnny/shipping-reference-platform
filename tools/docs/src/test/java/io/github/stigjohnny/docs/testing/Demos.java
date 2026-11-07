package io.github.stigjohnny.docs.testing;

/**
 * Sample demonstrations for the tests, one of each kind, each valid as written against a copy of the repository: a
 * test changes one thing and expects the check, the site or the scan to say why it fails. Their paths are
 * docs/demos/42-gate.md to docs/demos/46-feature.md.
 */
public final class Demos {

    public static final String GATE = """
            ---
            title: "An unreviewed dependency now fails the check"
            description: "A library without a review can no longer reach the build."
            status: accepted
            updated: 2026-11-07
            pr: 42
            kind: gate
            recorded: 2026-11-07
            tests: [BrainCheckTest]
            ---

            ## What changed

            Before, a dependency without a review was found only after the build had downloaded it. Now the check
            fails first.

            ## Try it

            From the repository root, with the reviewed JDK first on the path:

            ```sh
            java tools/policy/PolicyCheck.java .
            ```

            ## Evidence

            Recorded output \u2014 not rerun by this site build.
            Captured on 2026-11-07 on a synthetic machine with the reviewed JDK. The rejected case added a library
            without a review to a lockfile and exited with status 1; the accepted case, the clean repository, with 0.

            ```text
            ## Dependency allowlist: 1 finding
            ```

            ```text
            ## Dependency allowlist: clean
            ```

            Reviewed in pull request #42.

            ## Limits

            It covers the declared dependencies on the sanctioned path only.
            """;

    public static final String VISUAL = """
            ---
            title: "Every sheet now shows its code"
            description: "A reader can tell which sheet they are on."
            status: accepted
            updated: 2026-11-07
            pr: 43
            kind: visual
            recorded: 2026-11-07
            ---

            ## What changed

            Before, pages had no code. Now every sheet shows its code above its title.

            ## See it

            Open [how the site is drawn](../ui/documentation-site.md) and look above the title.

            ## Evidence

            Recorded output \u2014 not rerun by this site build.
            Inspected on 2026-11-07 in a desktop browser at 1280 by 900: [the design sheet](../ui/documentation-site.md)
            shows S-02 above its title.

            ## Limits

            One person looked, in one browser.
            """;

    public static final String DOCS = """
            ---
            title: "The README leads with the checker"
            description: "A first-time reader learns what runs today before what is planned."
            status: accepted
            updated: 2026-11-07
            pr: 44
            kind: docs
            recorded: 2026-11-06
            ---

            ## What changed

            The README now starts with what exists.

            ## See it

            Read [the README](../../README.md#what-exists-today).

            ## Evidence

            Recorded output \u2014 not rerun by this site build.
            Reviewed on 2026-11-06 against the repository at that time.

            ## Limits

            Whether the prose is right is a reader's judgement.
            """;

    public static final String BUILD = """
            ---
            title: "Edits of the model rerun the checker's tests"
            description: "A changed model can no longer pass on an earlier test result."
            status: accepted
            updated: 2026-11-07
            pr: 45
            kind: build
            recorded: 2026-11-05
            tests: [SelfConformanceTest]
            ---

            ## What changed

            The checker's tests declare the models they read as inputs.

            ## Try it

            ```sh
            ./gradlew :libs:c4-contract-kit:test --rerun \\
                --tests '*SelfConformanceTest' --tests '*ConformanceDivergenceTest'
            ```

            ## Evidence

            Recorded output \u2014 not rerun by this site build.
            Captured on 2026-11-05; exit status 0 both times.

            ```text
            BUILD SUCCESSFUL in 3s
            ```

            ## Limits

            Only the two models are declared.
            """;

    public static final String FEATURE = """
            ---
            title: "The site builds into one directory"
            description: "A reader can open the site without a server."
            status: accepted
            updated: 2026-11-07
            pr: 46
            kind: feature
            recorded: 2026-11-07
            ---

            ## What changed

            The site can be built locally.

            ## Try it

            ```sh
            ./gradlew :tools:docs:site
            ```

            ## Evidence

            Recorded output \u2014 not rerun by this site build.
            Captured on 2026-11-07.

            ## Limits

            Built on one machine.
            """;

    private Demos() {
    }

    /** The sample of a kind. */
    public static String text(String kind) {
        return switch (kind) {
            case "gate" -> GATE;
            case "visual" -> VISUAL;
            case "docs" -> DOCS;
            case "build" -> BUILD;
            case "feature" -> FEATURE;
            default -> throw new IllegalArgumentException(kind);
        };
    }

    /** The path of a kind's sample under docs/demos/. */
    public static String path(String kind) {
        return switch (kind) {
            case "gate" -> "docs/demos/42-gate.md";
            case "visual" -> "docs/demos/43-visual.md";
            case "docs" -> "docs/demos/44-docs.md";
            case "build" -> "docs/demos/45-build.md";
            case "feature" -> "docs/demos/46-feature.md";
            default -> throw new IllegalArgumentException(kind);
        };
    }
}
