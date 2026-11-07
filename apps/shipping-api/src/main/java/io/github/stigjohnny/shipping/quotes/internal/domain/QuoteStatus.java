package io.github.stigjohnny.shipping.quotes.internal.domain;

/** A quote's status at an instant, computed and never stored. */
public enum QuoteStatus {
    /** Before the quote's expiry: its offers may be booked. */
    OPEN,
    /** At or after the quote's expiry. */
    EXPIRED
}
