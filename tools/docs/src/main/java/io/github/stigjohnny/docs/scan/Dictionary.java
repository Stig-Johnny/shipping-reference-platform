package io.github.stigjohnny.docs.scan;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The scan's dictionary, from {@code PUBLIC_SCAN_DICTIONARY}: its terms, normalised like the text they are compared
 * with, and its state. It is unset when the variable is missing or blank, and malformed when an entry holds a control
 * character or no entry is left after normalisation; both fail the scan. Nothing here is ever printed.
 */
record Dictionary(List<String> terms, State state) {

    /** Whether the scan has a usable dictionary. */
    enum State { PRESENT, UNSET, MALFORMED }

    Dictionary {
        terms = List.copyOf(terms);
    }

    static Dictionary parse(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return new Dictionary(List.of(), State.UNSET);
        }
        boolean malformed = false;
        Set<String> terms = new LinkedHashSet<>();
        for (String line : text.lines().toList()) {
            if (line.codePoints().anyMatch(codePoint -> Character.getType(codePoint) == Character.CONTROL)) {
                malformed = true;
                continue;
            }
            String term = Text.forDictionary(Text.normalize(line)).strip();
            if (!term.isEmpty()) {
                terms.add(term);
            }
        }
        return new Dictionary(List.copyOf(terms), malformed || terms.isEmpty() ? State.MALFORMED : State.PRESENT);
    }
}
