package io.github.stigjohnny.shipping.scenarios.support;

import io.github.stigjohnny.shipping.scenarios.support.Comparison.Finding;
import io.github.stigjohnny.shipping.scenarios.support.Comparison.FindingType;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Exact, reviewed coverage debt. Never changes detection, step comparison or the raw conformance result. */
final class CoverageLedger {

    static final Path FILE = Path.of("src/test/resources/scenario-coverage-gaps.tsv");
    private static final Set<String> CLASSES = Set.of("Fixed HTTP/security constants",
            "HTTP response/problem assembly", "Authentication construction and local state",
            "Credential erasure callback", "Framework filter superclass construction",
            "Security configuration/building and callbacks", "Authentication-provider composition",
            "Environment/profile dispatch", "Ambient security state", "Authentication/authority extraction",
            "Framework dispatch to application callbacks", "Foreign-service factory parameters");

    record Entry(String classification, String followUp, String finding) {
    }

    record Assessment(List<Entry> known, List<Entry> stale) {
        Assessment {
            known = List.copyOf(known);
            stale = List.copyOf(stale);
        }

        /** Build blockers only. The original comparison still contains every refusal and does not conform. */
        List<Finding> blocking(Comparison.Result result) {
            List<String> remaining = new ArrayList<>(known.stream().map(Entry::finding).toList());
            List<Finding> blocking = new ArrayList<>();
            for (Finding finding : result.failing()) {
                if (finding.type() != FindingType.COVERAGE_GAP || !finding.step().isEmpty()
                        || !finding.run().isEmpty() || finding.interaction() != 0
                        || !remaining.remove(finding.message())) {
                    blocking.add(finding);
                }
            }
            stale.forEach(e -> blocking.add(new Finding(FindingType.COVERAGE_GAP, "", "", 0,
                    "ledger entry no longer detected: " + e.finding())));
            return List.copyOf(blocking);
        }
    }

    private final List<Entry> entries;

    private CoverageLedger(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    static CoverageLedger read(Path path) {
        try {
            List<String> lines = Files.readAllLines(path);
            if (lines.isEmpty() || !lines.getFirst().equals("classification\tfollow-up\tfinding")) {
                throw new IllegalArgumentException("coverage ledger header: " + path);
            }
            List<Entry> entries = new ArrayList<>();
            Set<String> unique = new HashSet<>();
            for (String line : lines.subList(1, lines.size())) {
                String[] cells = line.split("\t", -1);
                if (cells.length != 3 || !CLASSES.contains(cells[0]) || !cells[1].equals("phase 3 / option 2")
                        || cells[2].isBlank()) {
                    throw new IllegalArgumentException("invalid coverage ledger row: " + line);
                }
                if (!unique.add(cells[2])) {
                    throw new IllegalArgumentException("duplicate finding in coverage ledger: " + cells[2]);
                }
                entries.add(new Entry(cells[0], cells[1], cells[2]));
            }
            return new CoverageLedger(entries);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    Assessment assess(List<String> actual) {
        List<String> remaining = new ArrayList<>(actual);
        List<Entry> known = new ArrayList<>();
        List<Entry> stale = new ArrayList<>();
        for (Entry entry : entries) {
            (remaining.remove(entry.finding()) ? known : stale).add(entry);
        }
        return new Assessment(known, stale);
    }
}
