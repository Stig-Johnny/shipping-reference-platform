package io.github.stigjohnny.docs.check;

import io.github.stigjohnny.docs.repo.Sources;
import java.util.List;
import java.util.Locale;

/**
 * Which Markdown files are current prose (ADR 0023): the files whose inline references the documentation check reads,
 * and the only ones whose reference markers the site shows as labels, so that a label is always one the check has
 * proved. Generated files and records are not current prose: the brain, the architecture pages, the contribution
 * history and runs, the demonstrations other than their how-to, and the security reviews and verifications. Neither is
 * a superseded ADR, which its status says, not its path, nor anything in a directory the instruction check skips.
 * Records keep the names and markers they were written with, and nothing checks them.
 */
public final class CurrentProse {

    private static final List<String> NOT_READ = List.of("BRAIN.md", "docs/brain/", "docs/architecture/",
            "docs/contributions/", "security/reviews/", "security/verifications/");
    private static final String DEMOS = "docs/demos/";
    private static final String HOW_TO = "docs/demos/how-to.md";
    private static final String ADRS = "docs/adr/";

    private CurrentProse() {
    }

    /**
     * Whether a repository path, with forward slashes, names a Markdown file that is neither generated nor a record,
     * outside the directories the instruction check skips.
     */
    public static boolean path(String path) {
        if (!path.toLowerCase(Locale.ROOT).endsWith(".md") || inSkippedDirectory(path)) {
            return false;
        }
        if (path.startsWith(DEMOS)) {
            return path.equals(HOW_TO);
        }
        return NOT_READ.stream().noneMatch(entry -> entry.endsWith("/") ? path.startsWith(entry) : path.equals(entry));
    }

    /** Whether a file is current prose: its path is, and it is no superseded ADR. */
    public static boolean of(Sources sources, String path) {
        return path(path) && sources.docsUnder(ADRS).stream()
                .noneMatch(adr -> adr.path().equals(path) && "superseded".equals(adr.status()));
    }

    private static boolean inSkippedDirectory(String path) {
        for (int slash = path.indexOf('/'); slash > 0; slash = path.indexOf('/', slash + 1)) {
            if (AgentInstructions.skipped(path.substring(0, slash))) {
                return true;
            }
        }
        return false;
    }
}
