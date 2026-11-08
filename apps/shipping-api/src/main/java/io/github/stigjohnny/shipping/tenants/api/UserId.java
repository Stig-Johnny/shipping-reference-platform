package io.github.stigjohnny.shipping.tenants.api;

import java.util.Objects;
import java.util.UUID;

/** A synthetic user's identity. The synthetic users are in docs/modules/tenants.md. */
public record UserId(UUID value) {

    public UserId {
        Objects.requireNonNull(value, "value");
    }
}
