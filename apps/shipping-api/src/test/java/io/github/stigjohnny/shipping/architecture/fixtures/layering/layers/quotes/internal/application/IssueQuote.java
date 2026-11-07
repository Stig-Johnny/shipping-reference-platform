package io.github.stigjohnny.shipping.architecture.fixtures.layering.layers.quotes.internal.application;

import io.github.stigjohnny.shipping.architecture.fixtures.layering.layers.quotes.internal.adapter.out.jdbc.QuoteRows;

/** A negative fixture of HexagonalLayeringTest: application code that uses an adapter instead of a port. */
public record IssueQuote(QuoteRows rows) {
}
