package io.github.stigjohnny.docs.repo;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stigjohnny.docs.repo.RepositoryLinks.External;
import io.github.stigjohnny.docs.repo.RepositoryLinks.Missing;
import io.github.stigjohnny.docs.repo.RepositoryLinks.PrivateRepository;
import io.github.stigjohnny.docs.repo.RepositoryLinks.RepoFile;
import io.github.stigjohnny.docs.repo.RepositoryLinks.Unsafe;
import io.github.stigjohnny.docs.testing.Repository;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Where a document's link points, relative to the document. */
class RepositoryLinksTest {

    private static final Path ROOT = Repository.root();

    @Test
    void relativeLinksResolveAgainstTheDocument() {
        assertThat(RepositoryLinks.classify(ROOT, "docs/modules/quotes.md", "../gaps.md#7-documentation"))
                .isEqualTo(new RepoFile("docs/gaps.md", "7-documentation"));
        assertThat(RepositoryLinks.classify(ROOT, "README.md", "docs/risk-levels.md"))
                .isEqualTo(new RepoFile("docs/risk-levels.md", ""));
        assertThat(RepositoryLinks.classify(ROOT, "docs/brain/quotes.md", "../../BRAIN.md"))
                .isInstanceOf(RepoFile.class);
        assertThat(RepositoryLinks.classify(ROOT, "docs/gaps.md", "missing.md"))
                .isEqualTo(new Missing("docs/missing.md"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://github.com/Stig-Johnny/shipping-reference-platform",
            "https://github.com/stig-johnny/shipping-reference-platform/pull/7",
            "https://www.github.com/Stig-Johnny/shipping-reference-platform/blob/main/README.md",
            "http://github.com/Stig-Johnny/shipping-reference-platform.git"})
    void thePrivateRepositoryIsRecognised(String url) {
        assertThat(RepositoryLinks.classify(ROOT, "README.md", url)).isInstanceOf(PrivateRepository.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://github.com/Stig-Johnny/shipping-reference-platform-notes",
            "https://docs.structurizr.com/dsl", "mailto:owner@example.com"})
    void otherUrlsAreExternal(String url) {
        assertThat(RepositoryLinks.classify(ROOT, "README.md", url)).isInstanceOf(External.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "data:text/html,x", "//evil.example/x", "../../../etc/passwd",
            "docs/gaps.md?x=1", "docs\\\\gaps.md"})
    void unsafeDestinationsAreRefused(String destination) {
        assertThat(RepositoryLinks.classify(ROOT, "docs/gaps.md", destination)).isInstanceOf(Unsafe.class);
    }
}
