package io.github.stigjohnny.c4contractkit.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The import cycles among files: strongly connected components with more than one file, or a file importing itself. */
class ImportCyclesTest {

    @Test
    void aChainHasNoCycle() {
        assertThat(ImportCycles.in(Map.of("a", Set.of("b"), "b", Set.of("c")))).isEmpty();
    }

    @Test
    void eachCycleIsReportedOnceWithItsSortedFilesAndTheCyclesBySortedFirstFile() {
        Map<String, Set<String>> graph = Map.of(
                "m", Set.of("n"), "n", Set.of("o"), "o", Set.of("m"),
                "b", Set.of("a"), "a", Set.of("b"),
                "z", Set.of("z"),
                "x", Set.of("a"));

        assertThat(ImportCycles.in(graph)).containsExactly(List.of("a", "b"), List.of("m", "n", "o"), List.of("z"));
    }

    @Test
    void anImportOfAFileWhoseCycleIsAlreadyFoundDoesNotMergeTheCycles() {
        // a <-> b is found first; c imports a (finished, no longer on the stack) and forms its own cycle with d.
        Map<String, Set<String>> graph = Map.of("a", Set.of("b"), "b", Set.of("a"), "c", Set.of("a", "d"),
                "d", Set.of("c"));

        assertThat(ImportCycles.in(graph)).containsExactly(List.of("a", "b"), List.of("c", "d"));
    }
}
