package io.github.stigjohnny.shipping.quotes.internal.domain;

import io.github.stigjohnny.shipping.quotes.api.OfferId;
import io.github.stigjohnny.shipping.quotes.api.QuoteId;
import io.github.stigjohnny.shipping.tenants.api.TenantId;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An issued quote, the module's aggregate: immutable and owned by one tenant, with at least one offer and at most one
 * per service level, in ServiceLevel order. It expires 30 minutes after its issue, which has millisecond precision.
 */
public record Quote(QuoteId id, TenantId tenantId, QuoteRequest request, Zone zone, int billableWeightGrams,
        Instant issuedAt, Instant expiresAt, List<Offer> offers) {

    /** How long a quote's offers may be booked. */
    public static final Duration VALIDITY = Duration.ofMinutes(30);

    public Quote {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        offers = List.copyOf(offers);
        if (!issuedAt.equals(issuedAt.truncatedTo(ChronoUnit.MILLIS))) {
            throw new IllegalArgumentException("A quote's issue instant has millisecond precision, not " + issuedAt
                    + ".");
        }
        if (!expiresAt.equals(issuedAt.plus(VALIDITY))) {
            throw new IllegalArgumentException("A quote expires 30 minutes after its issue: issued " + issuedAt
                    + ", so expires " + issuedAt.plus(VALIDITY) + ", not " + expiresAt + ".");
        }
        if (offers.isEmpty()) {
            throw new IllegalArgumentException("A quote has at least one offer.");
        }
        for (int i = 1; i < offers.size(); i++) {
            if (offers.get(i - 1).level().compareTo(offers.get(i).level()) >= 0) {
                throw new IllegalArgumentException("A quote's offers are in ServiceLevel order, one per level.");
            }
        }
    }

    /** OPEN before the expiry, EXPIRED from it on. */
    public QuoteStatus status(Instant now) {
        return now.isBefore(expiresAt) ? QuoteStatus.OPEN : QuoteStatus.EXPIRED;
    }

    /** The offer with this ID, if it is one of this quote's. */
    public Optional<Offer> offer(OfferId offerId) {
        return offers.stream().filter(offer -> offer.id().equals(offerId)).findFirst();
    }
}
