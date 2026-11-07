package io.github.stigjohnny.shipping.quotes.internal.domain;

import io.github.stigjohnny.shipping.quotes.api.Money;
import io.github.stigjohnny.shipping.quotes.api.OfferId;
import io.github.stigjohnny.shipping.quotes.api.ServiceLevel;
import java.util.Objects;

/** One offer of a quote: a service level, its price and its simulated transit days. */
public record Offer(OfferId id, ServiceLevel level, Money price, TransitDays transitDays) {

    public Offer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(transitDays, "transitDays");
    }
}
