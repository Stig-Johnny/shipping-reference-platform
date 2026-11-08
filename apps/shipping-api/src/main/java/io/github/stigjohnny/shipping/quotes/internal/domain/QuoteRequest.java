package io.github.stigjohnny.shipping.quotes.internal.domain;

import io.github.stigjohnny.shipping.quotes.api.Place;
import io.github.stigjohnny.shipping.quotes.api.ServiceLevel;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** What a clerk asks to be priced: a parcel, a route and, optionally, the one service level wanted. */
public record QuoteRequest(Parcel parcel, Place origin, Place destination, @Nullable ServiceLevel serviceLevel) {

    public QuoteRequest {
        Objects.requireNonNull(parcel, "parcel");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(destination, "destination");
    }
}
