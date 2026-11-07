package io.github.stigjohnny.shipping.quotes.internal.domain;

import java.util.Objects;

/** The expected outcomes of rating a request: a quote, or the reason why nothing can be offered. */
public sealed interface QuoteResult permits QuoteResult.Offered, QuoteResult.Unavailable {

    /** At least one level can be offered: the quote. */
    record Offered(Quote quote) implements QuoteResult {

        public Offered {
            Objects.requireNonNull(quote, "quote");
        }
    }

    /** Nothing can be offered, for the first rule that failed. */
    record Unavailable(UnavailableReason reason) implements QuoteResult {

        public Unavailable {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
