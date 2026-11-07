package io.github.stigjohnny.docs.markdown;

/** What happens to one link of a Markdown document on the site. */
public sealed interface LinkDecision {

    /** The destination stays as written (external links and in-page anchors that exist). */
    record Keep() implements LinkDecision {
    }

    /** The destination becomes a site-relative URL. */
    record Rewrite(String href) implements LinkDecision {
    }

    /**
     * The link becomes plain text, because the public cannot open its target (source files, workflows, the private
     * repository). When the link's text is the URL itself, {@code text} replaces it.
     */
    record Unlink(String text) implements LinkDecision {
    }

    /** The link fails the build. */
    record Reject(String reason) implements LinkDecision {
    }
}
