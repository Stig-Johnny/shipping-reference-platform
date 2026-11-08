package io.github.stigjohnny.shipping.tenants.api;

import java.util.Objects;
import java.util.UUID;

/** A tenant's identity: a webshop that uses the platform. The synthetic tenants are in docs/modules/tenants.md. */
public record TenantId(UUID value) {

    public TenantId {
        Objects.requireNonNull(value, "value");
    }
}
