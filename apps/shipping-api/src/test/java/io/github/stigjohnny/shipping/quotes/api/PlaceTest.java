package io.github.stigjohnny.shipping.quotes.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** A place in the contract's format; a rejection never repeats the postcode, which belongs to an address. */
class PlaceTest {

    @ParameterizedTest
    @CsvSource({"NO, 0150", "SE, 11122", "NL, 1012 AB", "DE, 10-115", "FI, 00", "DK, A1B2C3D4E5"})
    void aPlaceInTheContractsFormatIsAccepted(String countryCode, String postcode) {
        Place place = new Place(countryCode, postcode);

        assertThat(place.countryCode()).isEqualTo(countryCode);
        assertThat(place.postcode()).isEqualTo(postcode);
    }

    @ParameterizedTest
    @CsvSource({"no", "N", "NOR", "N0", "''"})
    void aCountryCodeIsTwoUpperCaseLetters(String countryCode) {
        assertThatThrownBy(() -> new Place(countryCode, "0150")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("countryCode must be two upper-case letters (ISO 3166-1 alpha-2).");
    }

    @ParameterizedTest
    @CsvSource({"1", "'0150 '", "' 0150'", "-150", "0150-", "ab12", "A1B2C3D4E5F", "''"})
    void aPostcodeIsTwoToTenUpperCaseLettersDigitsSpacesOrHyphens(String postcode) {
        assertThatThrownBy(() -> new Place("NO", postcode)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("postcode must be 2 to 10 upper-case letters, digits, spaces or hyphens, starting and "
                        + "ending with a letter or digit.");
    }
}
