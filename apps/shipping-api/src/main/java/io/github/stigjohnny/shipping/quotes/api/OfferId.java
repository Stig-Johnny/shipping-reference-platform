package io.github.stigjohnny.shipping.quotes.api;

import java.util.Objects;
import java.util.UUID;

/** An offer's identity, unique within its tenant. */
public record OfferId(UUID value) {

    public OfferId {
        Objects.requireNonNull(value, "value");
    }
}
