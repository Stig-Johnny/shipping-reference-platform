package io.github.stigjohnny.shipping.shipments.internal.domain;

import java.util.UUID;

/**
 * Where new identifiers come from. It is injected, so that domain and application code never call
 * {@code UUID.randomUUID()} and tests get deterministic IDs. Each module has its own: there is no shared package.
 */
@FunctionalInterface
public interface IdGenerator {

    /** A new identifier. */
    UUID next();
}
