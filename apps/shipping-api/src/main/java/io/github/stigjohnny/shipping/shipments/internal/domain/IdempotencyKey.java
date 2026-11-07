package io.github.stigjohnny.shipping.shipments.internal.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The client's key for one intended booking, the contract's Idempotency-Key: 16 to 64 letters, digits, underscores or
 * hyphens. Its scope is the tenant: two tenants may use the same key.
 */
public record IdempotencyKey(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9_-]{16,64}$");

    public IdempotencyKey {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("An Idempotency-Key is 16 to 64 letters, digits, underscores or "
                    + "hyphens.");
        }
    }
}
