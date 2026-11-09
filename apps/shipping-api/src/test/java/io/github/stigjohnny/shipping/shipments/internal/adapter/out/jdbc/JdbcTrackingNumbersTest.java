package io.github.stigjohnny.shipping.shipments.internal.adapter.out.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class JdbcTrackingNumbersTest {

    @ParameterizedTest
    @CsvSource({
            "1, SIM0000000001",
            "42, SIM0000000042",
            "1234567890, SIM1234567890",
            "9999999999, SIM9999999999"
    })
    void trackingNumbersKeepAsciiDigitsUnderAnArabicDefaultLocale(long sequence, String expected) {
        Locale previous = Locale.getDefault(Locale.Category.FORMAT);
        try {
            Locale.setDefault(Locale.Category.FORMAT, Locale.forLanguageTag("ar-EG"));
            assertThat(JdbcTrackingNumbers.format(sequence).value()).isEqualTo(expected);
        } finally {
            Locale.setDefault(Locale.Category.FORMAT, previous);
        }
    }
}
