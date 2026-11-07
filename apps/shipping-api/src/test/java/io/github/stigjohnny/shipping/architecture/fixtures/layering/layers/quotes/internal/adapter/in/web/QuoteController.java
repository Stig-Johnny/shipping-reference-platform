package io.github.stigjohnny.shipping.architecture.fixtures.layering.layers.quotes.internal.adapter.in.web;

import io.github.stigjohnny.shipping.architecture.fixtures.layering.layers.quotes.internal.adapter.out.jdbc.QuoteRows;

/** A negative fixture of HexagonalLayeringTest: an inbound adapter that uses an outbound adapter. */
public record QuoteController(QuoteRows rows) {
}
