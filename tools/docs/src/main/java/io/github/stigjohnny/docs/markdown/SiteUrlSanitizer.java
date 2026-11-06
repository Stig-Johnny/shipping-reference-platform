package io.github.stigjohnny.docs.markdown;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.commonmark.renderer.html.UrlSanitizer;

/**
 * Allows only http, https, mailto, relative and fragment URLs; anything else becomes the empty string. CommonMark's
 * default sanitizer also allows {@code data:} (security/reviews/maven--docs-tool-scope-extension.md). URLs with
 * whitespace, control characters or backslashes are emptied as well, because browsers strip or reinterpret them, and
 * so are protocol-relative URLs, which would leave the site's origin.
 */
public final class SiteUrlSanitizer implements UrlSanitizer {

    private static final Set<String> SCHEMES = Set.of("http", "https", "mailto");
    private static final Pattern SCHEME = Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]*):");

    @Override
    public String sanitizeLinkUrl(String url) {
        return allowed(url) ? url : "";
    }

    @Override
    public String sanitizeImageUrl(String url) {
        return allowed(url) ? url : "";
    }

    static boolean allowed(String url) {
        if (url.isEmpty() || url.startsWith("//")) {
            return false;
        }
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c <= ' ' || c == 0x7f || c == '\\' || Character.isWhitespace(c) || Character.isISOControl(c)) {
                return false;
            }
        }
        Matcher scheme = SCHEME.matcher(url);
        if (scheme.find()) {
            return SCHEMES.contains(scheme.group(1).toLowerCase(Locale.ROOT));
        }
        // A colon before the first '/', '?' or '#' would make a browser read a scheme; without a valid one, refuse.
        int colon = url.indexOf(':');
        int end = firstOf(url, "/?#");
        return colon < 0 || (end >= 0 && end < colon);
    }

    private static int firstOf(String url, String characters) {
        int first = -1;
        for (int i = 0; i < characters.length(); i++) {
            int at = url.indexOf(characters.charAt(i));
            if (at >= 0 && (first < 0 || at < first)) {
                first = at;
            }
        }
        return first;
    }
}
