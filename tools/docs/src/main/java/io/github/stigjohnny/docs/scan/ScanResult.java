package io.github.stigjohnny.docs.scan;

import java.util.ArrayList;
import java.util.List;

/**
 * The result of a publication scan, bound to the site's digest and the source revision. It holds rule IDs, file
 * paths and digests only: never a matched value, never a dictionary term.
 */
public record ScanResult(List<Finding> findings, List<ScanException> applied, String siteDigest, int files,
        String revision, Dictionary.State dictionary) {

    public ScanResult {
        findings = List.copyOf(findings);
        applied = List.copyOf(applied);
    }

    public boolean passed() {
        return findings.isEmpty();
    }

    /** The summary in Markdown, for the job summary and the build directory. */
    public String summary() {
        List<String> rules = new ArrayList<>(Rule.ids());
        rules.add(PublicationScanner.DICTIONARY_RULE);
        StringBuilder out = new StringBuilder("## Publication scan: ").append(passed() ? "passed" : "FAILED")
                .append("\n\n")
                .append("- Site: ").append(files).append(" files, digest `sha256:").append(siteDigest)
                .append("`: the SHA-256 of one `<sha256>  ./<path>` line per file, sorted by path, as ")
                .append("`find . -type f -print0 | LC_ALL=C sort -z | xargs -0 shasum -a 256` prints them in the site ")
                .append("directory.\n")
                .append(revision.isBlank() ? "- Source revision: not set (a local scan).\n"
                        : "- Source revision: `" + revision + "`.\n")
                .append(switch (dictionary) {
                    case PRESENT -> "- Dictionary: present.\n";
                    case UNSET -> "- Dictionary: unset or empty, so the scan fails closed.\n";
                    case MALFORMED -> "- Dictionary: malformed (an entry holds a control character, or no entry is "
                            + "valid), so the scan fails closed.\n";
                })
                .append("- Rules: ").append(String.join(", ", rules)).append(", on file contents and on file and ")
                .append("directory names.\n")
                .append("- A path whose name fails a rule is never printed: `[withheld path, sha256 <12 digits>]` ")
                .append("stands in for it, the start of the SHA-256 of the path relative to the site.\n");
        if (applied.isEmpty()) {
            out.append("- Exceptions applied: none.\n");
        } else {
            out.append("- Exceptions applied:\n");
            for (ScanException exception : applied) {
                out.append("  - ").append(exception.rule()).append(" in ").append(exception.file())
                        .append(" (value SHA-256 `").append(exception.sha256()).append("`)\n");
            }
        }
        if (findings.isEmpty()) {
            out.append("- Findings: none.\n");
        } else {
            out.append("- Findings (").append(findings.size()).append("), rule and file only:\n");
            for (Finding finding : findings) {
                out.append("  - ").append(finding.rule())
                        .append(finding.file().isEmpty() ? "" : " in " + finding.file()).append('\n');
            }
        }
        return out.toString();
    }
}
