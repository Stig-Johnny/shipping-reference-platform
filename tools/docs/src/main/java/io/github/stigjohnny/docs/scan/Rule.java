package io.github.stigjohnny.docs.scan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The pattern rules of the publication scan. Each finds values in a view of a file; the scanner reports the rule and
 * the file, never the value. The secret shapes follow the providers' documented token formats.
 */
enum Rule {
    /** A PEM private key of any kind. */
    PRIVATE_KEY("-----BEGIN (?:[A-Z0-9]+ )*PRIVATE KEY(?: BLOCK)?-----"),
    /** GitHub personal, OAuth, user-to-server, server-to-server and refresh tokens, and fine-grained tokens. */
    GITHUB_TOKEN("\\b(?:gh[pousr]_[A-Za-z0-9]{36,255}|github_pat_[A-Za-z0-9_]{22,255})\\b"),
    /** AWS access key IDs. */
    AWS_ACCESS_KEY_ID("\\b(?:AKIA|ASIA|ABIA|ACCA|AIDA|AROA|AIPA|ANPA|ANVA|AGPA)[A-Z0-9]{16}\\b"),
    /** Slack bot, user, app and refresh tokens. */
    SLACK_TOKEN("\\bxox[abposre]-[A-Za-z0-9-]{10,}"),
    /** Slack incoming webhooks and workflow triggers. */
    SLACK_WEBHOOK("hooks\\.slack\\.com/(?:services|workflows|triggers)/[A-Za-z0-9/_-]+"),
    /** Discord webhooks. */
    DISCORD_WEBHOOK("discord(?:app)?\\.com/api/webhooks/[0-9]+/[A-Za-z0-9_-]+"),
    /** Google API keys. */
    GOOGLE_API_KEY("\\bAIza[0-9A-Za-z_-]{35}(?![0-9A-Za-z_-])"),
    /** Stripe secret and restricted keys. */
    STRIPE_KEY("\\b(?:sk|rk)_(?:live|test)_[0-9A-Za-z]{16,}\\b"),
    /** npm access tokens. */
    NPM_TOKEN("\\bnpm_[A-Za-z0-9]{36}\\b"),
    /** JSON Web Tokens: three base64url parts, the first two JSON objects. */
    JWT("\\beyJ[A-Za-z0-9_-]{8,}\\.eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}"),
    /**
     * A bearer credential on one line, as in an Authorization header: eight or more token characters with at least
     * one digit or symbol among them, so that prose such as "bearer authentication" is not one.
     */
    BEARER_TOKEN("(?i)\\bbearer[ \\t]+(?=[A-Za-z0-9._~+/-]*[0-9._~+/-])[A-Za-z0-9._~+/-]{8,}=*"),
    /** A URL with a user name and password in it. */
    URL_CREDENTIALS("\\b[A-Za-z][A-Za-z0-9+.-]*://[^/\\s:@\"'<>]+:[^/\\s:@\"'<>]+@"),
    /** A quoted value of twelve or more characters assigned to a password, secret, key or token. */
    SECRET_ASSIGNMENT("(?i)\\b(?:password|passwd|pwd|secret|api[_-]?key|access[_-]?token|auth[_-]?token"
            + "|client[_-]?secret|private[_-]?key)\\b[\"']?[ \\t]*[:=][ \\t]*[\"'][^\"'\\s]{12,}[\"']"),
    /** The repository on GitHub, which the site never links (ADR 0010). */
    PRIVATE_REPOSITORY_LINK("(?i)github\\.com[/:]stig-johnny/shipping-reference-platform"),
    /** An absolute path of a build machine or a developer's computer. */
    LOCAL_PATH("(?:/Users/|/home/|/private/|\\b[A-Za-z]:\\\\)"),
    /** RFC 1918, link-local and the shared range that Tailscale uses, in IPv4; unique-local and link-local IPv6. */
    PRIVATE_IP("(?<![0-9.])((?:10|100|169|172|192)\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3})(?![0-9])(?!\\.[0-9])"
            + "|(?i)(?<![0-9a-z:])((?:f[cd][0-9a-f]{2}|fe[89ab][0-9a-f]):(?:[0-9a-f]{0,4}:){1,6}[0-9a-f]{0,4})"
            + "(?![0-9a-z:])"),
    /** A host name in Tailscale's MagicDNS or a private DNS zone. */
    INTERNAL_HOSTNAME("(?i)\\b[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)*"
            + "\\.(?:ts\\.net|home\\.arpa|internal|corp|lan)\\b(?![.-][a-z0-9])"),
    /** A comment in a page, which no generated page has; it is matched in the raw markup only. */
    HTML_COMMENT("<!--");

    private final Pattern pattern;

    Rule(String regex) {
        this.pattern = Pattern.compile(regex);
    }

    static List<String> ids() {
        return Arrays.stream(values()).map(Rule::name).toList();
    }

    /** The values this rule finds in the text. */
    List<String> matches(String text) {
        List<String> values = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            if (this != PRIVATE_IP || privateAddress(matcher)) {
                values.add(matcher.group());
            }
        }
        return values;
    }

    private static boolean privateAddress(Matcher matcher) {
        String v4 = matcher.group(1);
        if (v4 != null) {
            int[] octets = Arrays.stream(v4.split("\\.", -1)).mapToInt(Integer::parseInt).toArray();
            if (Arrays.stream(octets).anyMatch(octet -> octet > 255)) {
                return false;
            }
            return octets[0] == 10
                    || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31)
                    || (octets[0] == 192 && octets[1] == 168)
                    || (octets[0] == 100 && octets[1] >= 64 && octets[1] <= 127)
                    || (octets[0] == 169 && octets[1] == 254);
        }
        String v6 = String.valueOf(matcher.group(2));
        int groups = v6.split(":", -1).length;
        return v6.contains("::") ? groups <= 8 : groups == 8;
    }
}
