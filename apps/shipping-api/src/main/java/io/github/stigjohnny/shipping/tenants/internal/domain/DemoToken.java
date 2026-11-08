package io.github.stigjohnny.shipping.tenants.internal.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A demo bearer token: {@code demo-} and a user handle, {@code ^demo-[a-z0-9-]{3,40}$} (docs/modules/tenants.md). The
 * synthetic users' tokens are public, guessable fixtures, not secrets. Even so, only the token's SHA-256 is stored and
 * looked up, and its string form never shows the token, so a token cannot reach a log through it.
 */
public record DemoToken(String value) {

    private static final Pattern FORMAT = Pattern.compile("^demo-[a-z0-9-]{3,40}$");

    public DemoToken {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("A demo token matches " + FORMAT.pattern() + ".");
        }
    }

    /** The token, if the presented text has the demo token format; empty for any other text. */
    public static Optional<DemoToken> parse(String presented) {
        return FORMAT.matcher(presented).matches() ? Optional.of(new DemoToken(presented)) : Optional.empty();
    }

    /** The SHA-256 of the token's UTF-8 bytes, which is what the database stores: 32 bytes, a new array each time. */
    public byte[] sha256() {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java platform supports SHA-256.", e);
        }
    }

    @Override
    public String toString() {
        return "DemoToken[redacted]";
    }
}
