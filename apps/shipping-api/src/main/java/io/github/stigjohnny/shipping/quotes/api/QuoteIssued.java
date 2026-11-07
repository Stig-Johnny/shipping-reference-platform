package io.github.stigjohnny.shipping.quotes.api;

import io.github.stigjohnny.shipping.tenants.api.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Published when a quote is stored (ADR 0006): identifiers and the instant only, no personal data. No module consumes
 * it in v0.1. Changing it is a level 3 change, and an additive one only.
 */
public record QuoteIssued(UUID eventId, TenantId tenantId, QuoteId quoteId, Instant occurredAt) {

    public QuoteIssued {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(quoteId, "quoteId");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
