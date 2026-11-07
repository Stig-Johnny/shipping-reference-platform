package io.github.stigjohnny.docs.repo;

import java.util.List;
import java.util.Optional;

/**
 * An owner's acceptance of the reviewed set, current or previous.
 *
 * @param at the acceptance time as recorded
 * @param record the pull request the acceptance was given for, for example {@code pull request #10}, or empty
 * @param reviewedSetSha256 the digest over every entry and review record, empty for acceptances before it existed
 * @param entriesSha256 the first checker's digest over the enforced fields only, empty when not recorded
 * @param notes further statements recorded with the acceptance, such as a confirmed project path
 */
public record Acceptance(String at, String record, String reviewedSetSha256, String entriesSha256,
        List<String> notes) {

    public Acceptance {
        notes = List.copyOf(notes);
    }

    public Optional<String> digest() {
        return reviewedSetSha256.isEmpty() ? Optional.empty() : Optional.of(reviewedSetSha256);
    }
}
