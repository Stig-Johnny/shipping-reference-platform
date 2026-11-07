package io.github.stigjohnny.docs.testing;

/**
 * A sample milestone deck for the tests, valid as written against a copy of the repository: three main slides, one
 * appendix slide, notes on three of them and one recorded output with its full record in the notes. A test changes one
 * thing and expects the check, the site or the scan to say why it fails. It stands at the path of the repository's own
 * deck, whose sheet codes (X-D01 and X-N01) the site has, so that a test can build the site with it.
 */
public final class Decks {

    /** Where the sample stands: the path of the repository's own deck. */
    public static final String PATH = "docs/demos/deck-milestone.md";

    public static final String SAMPLE = """
            ---
            title: "Milestone: an example"
            description: "A sample deck: what is built, what is planned and one change the gate rejects."
            status: accepted
            updated: 2026-11-07
            kind: deck
            lang: en
            milestone: 2026-11-10
            mainSlides: 3
            appendixSlides: 1
            ---

            # Milestone: an example

            ## What is built

            The checker runs in every build.

            - The model governs the structure
            - The gate stops unreviewed dependencies

            ### Notes

            - Say first what is not built

            ## State today

            The controls are built; the product is planned.

            | Promise | Today |
            |---|---|
            | The checker | Built and tested |

            ### Notes

            - The numbers come from the sources

            ## One change the gate rejects

            ```text
            ### DIVERGENCE: report -> model
            ```

            Recorded output — not rerun by this site build. Full record in the notes.

            ### Notes

            - Recorded output — not rerun by this site build. Recorded 2026-11-07T14:56:19Z on revision 44ef312: \
            `./gradlew :libs:c4-contract-kit:test --rerun --tests '*SelfConformanceTest'` exited 1, and 0 before and after.

            ## Appendix: tools

            Java 25 and strict builds.
            """;

    private Decks() {
    }

    /** The sample with one part replaced; the part must be there, so that a test never passes by changing nothing. */
    public static String with(String from, String to) {
        if (!SAMPLE.contains(from)) {
            throw new IllegalArgumentException("the sample deck has no '" + from + "'");
        }
        return SAMPLE.replace(from, to);
    }
}
