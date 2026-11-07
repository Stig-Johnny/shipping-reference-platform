/**
 * The quotes module's public API: the only package of the module that other modules may use (ADR 0003). Shipments is
 * to book through {@link io.github.stigjohnny.shipping.quotes.api.QuoteOffers}.
 */
@NamedInterface("api")
@NullMarked
package io.github.stigjohnny.shipping.quotes.api;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.NamedInterface;
