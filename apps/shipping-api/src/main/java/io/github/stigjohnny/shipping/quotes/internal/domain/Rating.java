package io.github.stigjohnny.shipping.quotes.internal.domain;

import io.github.stigjohnny.shipping.quotes.api.OfferId;
import io.github.stigjohnny.shipping.quotes.api.QuoteId;
import io.github.stigjohnny.shipping.quotes.api.ServiceLevel;
import io.github.stigjohnny.shipping.tenants.api.TenantId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Rates a request with the synthetic tariff: one offer per available service level in ServiceLevel order, or exactly
 * the requested level, or the reason why nothing can be offered. The quote takes the next identifier, then each offer
 * one in level order.
 */
public final class Rating {

    private final SyntheticTariff tariff;
    private final IdGenerator ids;

    public Rating(SyntheticTariff tariff, IdGenerator ids) {
        this.tariff = Objects.requireNonNull(tariff, "tariff");
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    /**
     * The quote for the tenant, or why there is none.
     *
     * @param issuedAt the injected clock's instant; the quote keeps it truncated to milliseconds and expires 30
     *     minutes later
     */
    public QuoteResult quote(TenantId tenant, QuoteRequest request, Instant issuedAt) {
        Optional<UnavailableReason> refusal = tariff.unavailability(request);
        if (refusal.isPresent()) {
            return new QuoteResult.Unavailable(refusal.get());
        }
        int billable = request.parcel().billableWeightGrams();
        WeightBand band = WeightBand.of(billable).orElseThrow();
        Zone zone = tariff.zone(request.origin(), request.destination());
        ServiceLevel requested = request.serviceLevel();
        List<ServiceLevel> levels = requested != null ? List.of(requested)
                : Stream.of(ServiceLevel.values()).filter(level -> tariff.available(level, billable)).toList();
        QuoteId id = new QuoteId(ids.next());
        List<Offer> offers = levels.stream().map(level -> new Offer(new OfferId(ids.next()), level,
                tariff.price(zone, band, level), tariff.transitDays(zone, level))).toList();
        Instant issued = issuedAt.truncatedTo(ChronoUnit.MILLIS);
        return new QuoteResult.Offered(new Quote(id, tenant, request, zone, billable, issued,
                issued.plus(Quote.VALIDITY), offers));
    }
}
