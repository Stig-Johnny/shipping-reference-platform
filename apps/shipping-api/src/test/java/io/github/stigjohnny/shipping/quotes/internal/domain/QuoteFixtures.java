package io.github.stigjohnny.shipping.quotes.internal.domain;

import io.github.stigjohnny.shipping.quotes.api.Money;
import io.github.stigjohnny.shipping.quotes.api.Place;
import io.github.stigjohnny.shipping.quotes.api.ServiceLevel;
import io.github.stigjohnny.shipping.tenants.api.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;

/** Synthetic values for the quotes tests: Tenant Alpha and Tenant Beta, deterministic IDs and the contract's example. */
public final class QuoteFixtures {

    /** Tenant Alpha (synthetic), docs/modules/tenants.md. */
    public static final TenantId ALPHA = new TenantId(UUID.fromString("11111111-1111-4111-8111-111111111111"));
    /** Tenant Beta (synthetic), docs/modules/tenants.md. */
    public static final TenantId BETA = new TenantId(UUID.fromString("22222222-2222-4222-8222-222222222222"));
    public static final Currency EUR = Currency.getInstance("EUR");
    public static final Place OSLO = new Place("NO", "0150");
    public static final Place STOCKHOLM = new Place("SE", "11122");
    /** The contract's example: NO 0150 to SE 11122, 1200 g, 300 x 200 x 100 mm, billable 1200 g, any level. */
    public static final QuoteRequest EXAMPLE = new QuoteRequest(new Parcel(1200, 300, 200, 100), OSLO, STOCKHOLM, null);

    private QuoteFixtures() {
    }

    /** The example's route with another parcel and, when it is not null, a requested level. */
    public static QuoteRequest request(Parcel parcel, @Nullable ServiceLevel level) {
        return new QuoteRequest(parcel, OSLO, STOCKHOLM, level);
    }

    public static Money eur(String amount) {
        return new Money(new BigDecimal(amount), EUR);
    }

    /** Hands out {@link #id(long) id(1)}, {@code id(2)} and so on, in the order they are asked for. */
    public static IdGenerator sequentialIds() {
        AtomicLong last = new AtomicLong();
        return () -> id(last.incrementAndGet());
    }

    /** The n-th deterministic ID: 00000000-0000-4000-8000-000000000001 for 1. */
    public static UUID id(long n) {
        return UUID.fromString("00000000-0000-4000-8000-%012d".formatted(n));
    }

    /** The example's quote for a tenant, issued at the given instant, with the IDs id(1) to id(4). */
    public static Quote exampleQuote(TenantId tenant, Instant issuedAt) {
        return offered(new Rating(new SyntheticTariff(), sequentialIds()).quote(tenant, EXAMPLE, issuedAt));
    }

    /** The quote of an offered result; any other result fails the test. */
    public static Quote offered(QuoteResult result) {
        return switch (result) {
            case QuoteResult.Offered(Quote quote) -> quote;
            case QuoteResult.Unavailable(UnavailableReason reason) -> throw new AssertionError("not offered: " + reason);
        };
    }
}
