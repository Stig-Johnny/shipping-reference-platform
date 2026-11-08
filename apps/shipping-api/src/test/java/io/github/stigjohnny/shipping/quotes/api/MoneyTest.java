package io.github.stigjohnny.shipping.quotes.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;

/** Money: never negative, and exactly at its currency's fraction digits, which it never rounds to (invariant Q4). */
class MoneyTest {

    private static final Currency EUR = Currency.getInstance("EUR");
    private static final Currency JPY = Currency.getInstance("JPY");

    @Test
    void anAmountIsHeldAtItsCurrencysFractionDigits() {
        assertThat(new Money(new BigDecimal("15.1"), EUR)).isEqualTo(new Money(new BigDecimal("15.10"), EUR));
        assertThat(new Money(new BigDecimal("15"), EUR).amount()).hasToString("15.00");
        assertThat(new Money(new BigDecimal("15.050"), EUR).amount()).hasToString("15.05");
        assertThat(new Money(new BigDecimal("100"), JPY).amount()).hasToString("100");
        assertThat(new Money(BigDecimal.ZERO, EUR).amount()).hasToString("0.00");
    }

    @Test
    void aNegativeAmountIsRejected() {
        assertThatThrownBy(() -> new Money(new BigDecimal("-0.01"), EUR)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("An amount of money is never negative: -0.01 EUR.");
    }

    @Test
    void moreFractionDigitsThanTheCurrencyHasAreRejectedNotRounded() {
        assertThatThrownBy(() -> new Money(new BigDecimal("15.005"), EUR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("15.005 has more fraction digits than EUR has (2); money is never rounded here.");
        assertThatThrownBy(() -> new Money(new BigDecimal("100.5"), JPY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("100.5 has more fraction digits than JPY has (0); money is never rounded here.");
    }

    @Test
    void aCurrencyWithoutFractionDigitsOfItsOwnIsRejected() {
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, Currency.getInstance("XXX")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("XXX has no fraction digits of its own, so it cannot hold an amount of money.");
    }

    // NullAway keeps null out of @NullMarked callers at compile time; @NullSource passes it as a caller outside its
    // reach would, through reflection.
    @ParameterizedTest
    @NullSource
    void anAmountIsRequired(BigDecimal amount) {
        assertThatThrownBy(() -> new Money(amount, EUR)).isInstanceOf(NullPointerException.class).hasMessage("amount");
    }

    @ParameterizedTest
    @NullSource
    void aCurrencyIsRequired(Currency currency) {
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, currency)).isInstanceOf(NullPointerException.class)
                .hasMessage("currency");
    }
}
