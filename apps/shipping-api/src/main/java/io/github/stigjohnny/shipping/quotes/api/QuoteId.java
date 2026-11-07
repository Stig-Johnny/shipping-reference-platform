package io.github.stigjohnny.shipping.quotes.api;

import java.util.Objects;
import java.util.UUID;

/** A quote's identity. */
public record QuoteId(UUID value) {

    public QuoteId {
        Objects.requireNonNull(value, "value");
    }
}
