package io.github.stigjohnny.docs.repo;

import java.util.List;

/**
 * One entry of {@code security/allowlist.json}, as the site shows it.
 *
 * @param sha256 the reviewed SHA-256 of the artifact, or empty when the entry pins something else
 * @param scope the projects and configurations the review approved, in words
 */
public record AllowlistEntry(String kind, String coordinate, String version, String artifact, String sha256,
        String verdict, String scope, List<String> conditions, String reviewRecord, String reviewedAt,
        String expiresAt) {

    public AllowlistEntry {
        conditions = List.copyOf(conditions);
    }

    public boolean rejected() {
        return "REJECT".equals(verdict);
    }

    /** The verdict in words: approved, approved with conditions, or rejected. */
    public String verdictWords() {
        return switch (verdict) {
            case "APPROVE" -> "Approved";
            case "APPROVE-WITH-CONDITIONS" -> "Approved with conditions";
            case "REJECT" -> "Rejected";
            default -> "Unknown verdict " + verdict;
        };
    }
}
