package io.github.stigjohnny.docs.repo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.testing.Repository;
import org.junit.jupiter.api.Test;

/** The public summary of a review is the first paragraph of its Verdict section, and every review has one. */
class ReviewRecordTest {

    @Test
    void everyReviewOnTheAllowlistHasAPublicSummary() {
        Allowlist allowlist = Allowlist.parse(Repository.read(Allowlist.PATH));

        assertThat(allowlist.reviewRecords()).isNotEmpty().allSatisfy(path -> {
            ReviewRecord review = ReviewRecord.parse(path, Repository.read(path));
            assertThat(review.summary()).as(path).isNotBlank().doesNotContain("\n\n").doesNotStartWith("#");
            assertThat(review.verdict()).as(path).isIn("APPROVE", "APPROVE-WITH-CONDITIONS", "REJECT");
        });
    }

    @Test
    void theSummaryIsTheFirstParagraphUnderTheVerdictHeading() {
        String review = """
                ---
                component: x
                verdict: REJECT
                ---

                # Review

                ## 1. Verdict
                **REJECT.** It prints instructions
                to agents.

                More detail that stays private.

                ## 2. Next
                """;

        assertThat(ReviewRecord.parse("security/reviews/x.md", review).summary())
                .isEqualTo("**REJECT.** It prints instructions\nto agents.");
    }

    @Test
    void aReviewWithoutAVerdictSectionFails() {
        assertThatThrownBy(() -> ReviewRecord.parse("security/reviews/x.md", "---\ncomponent: x\n---\n\n# Review\n"))
                .isInstanceOf(DocsException.class).hasMessageContaining("no Verdict section");
    }
}
