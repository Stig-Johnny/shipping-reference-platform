package io.github.stigjohnny.shipping.quotes.internal.domain;

import java.util.UUID;

/**
 * Where new identifiers come from. It is injected, so that domain and application code never call
 * {@code UUID.randomUUID()} and tests get deterministic IDs.
 */
@FunctionalInterface
public interface IdGenerator {

    /** A new identifier. */
    UUID next();
}
