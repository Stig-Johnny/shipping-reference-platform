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
            title: "Milepæl: et eksempel"
            description: "A sample deck in Norwegian: what is built, what is planned and one change the gate rejects."
            status: accepted
            updated: 2026-11-07
            kind: deck
            lang: nb
            milestone: 2026-11-10
            mainSlides: 3
            appendixSlides: 1
            ---

            # Milepæl: et eksempel

            ## Hva som er bygget

            Sjekkeren kjører i hvert bygg.

            - Modellen styrer strukturen
            - Porten stopper uvurderte avhengigheter

            ### Notater

            - Si først hva som ikke er bygget

            ## Status i dag

            Kontrollene er bygget; produktet er planlagt.

            | Løfte | I dag |
            |---|---|
            | Sjekkeren | Bygget og testet |

            ### Notater

            - Tallene kommer fra kildene

            ## Én endring porten avviser

            Utdrag:

            ```text
            ### DIVERGENCE: report -> model
            ```

            Recorded output — not rerun by this site build. Full record in the notes.

            ### Notater

            - Recorded output — not rerun by this site build. Recorded 2026-11-07T14:56:19Z on revision 44ef312: \
            `./gradlew :libs:c4-contract-kit:test --rerun --tests '*SelfConformanceTest'` exited 1, and 0 before and after.

            ## Vedlegg: verktøy

            Java 25 og strenge bygg.
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
