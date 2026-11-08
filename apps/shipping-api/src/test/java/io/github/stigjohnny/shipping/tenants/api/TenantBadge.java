package io.github.stigjohnny.shipping.tenants.api;

import java.util.Objects;

/**
 * A test-only value of the tenants module's api with a method of its own, for the scenario checks' census fixture
 * (CoverageBypassTest): calling a record's method other than its accessors runs the other module's code, which no
 * proxy intercepts, so the census counts it as a gap and not as a value use.
 */
public record TenantBadge(TenantId tenant) {

    public TenantBadge {
        Objects.requireNonNull(tenant, "tenant");
    }

    /** Behaviour of the tenants module, behind a value. */
    public String describe() {
        return "tenant " + tenant.value();
    }
}
