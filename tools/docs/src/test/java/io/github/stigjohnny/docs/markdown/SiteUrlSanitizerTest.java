package io.github.stigjohnny.docs.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Only http, https, mailto, relative and fragment URLs (the default sanitizer also allows data:). */
class SiteUrlSanitizerTest {

    private final SiteUrlSanitizer sanitizer = new SiteUrlSanitizer();

    @ParameterizedTest
    @ValueSource(strings = {"https://www.rfc-editor.org/rfc/rfc9457.html", "http://example.com/a?b=c#d",
            "mailto:owner@example.com", "MAILTO:owner@example.com", "HTTPS://EXAMPLE.COM", "../trust/gaps.html",
            "gaps.html#7-documentation", "#top", "?q=1", "downloads/BRAIN.md"})
    void allowedUrlsPassUnchanged(String url) {
        assertThat(sanitizer.sanitizeLinkUrl(url)).isEqualTo(url);
        assertThat(sanitizer.sanitizeImageUrl(url)).isEqualTo(url);
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "JaVaScRiPt:alert(1)", "data:text/html,hi",
            "data:image/png;base64,AAAA", "vbscript:x", "file:///etc/passwd", "ftp://example.com", "//evil.example/x",
            " javascript:alert(1)", "java\nscript:alert(1)", "\\\\evil.example\\x", "", "a b.html", "x:y:z"})
    void everythingElseIsEmptied(String url) {
        assertThat(sanitizer.sanitizeLinkUrl(url)).isEmpty();
        assertThat(sanitizer.sanitizeImageUrl(url)).isEmpty();
    }
}
