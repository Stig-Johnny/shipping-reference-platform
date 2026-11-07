package io.github.stigjohnny.shipping.quotes.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * An amount of money in one currency: never negative, and held at exactly its currency's fraction digits. 15.1 EUR is
 * 15.10 EUR; 15.005 EUR is rejected, never rounded: rounding happens once, in the tariff.
 */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        int digits = currency.getDefaultFractionDigits();
        if (digits < 0) {
            throw new IllegalArgumentException(currency + " has no fraction digits of its own, so it cannot hold an "
                    + "amount of money.");
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("An amount of money is never negative: " + amount.toPlainString() + " "
                    + currency + ".");
        }
        try {
            amount = amount.setScale(digits, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(amount.toPlainString() + " has more fraction digits than " + currency
                    + " has (" + digits + "); money is never rounded here.", e);
        }
    }
}
