package io.github.stigjohnny.shipping.shipments.internal.domain;

import static io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentFixtures.RECIPIENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A recipient is validated as the contract's Recipient schema says (invariant S13): name, addressLine1 and the optional
 * addressLine2 hold 1 to 70 characters and city 1 to 50, counted as the schema counts them, in Unicode code points, and
 * none of them a control character (U+0000 to U+001F and U+007F); their text is well-formed Unicode, without a lone
 * surrogate; postcode and countryCode follow the contract's Postcode and CountryCode patterns. A recipient is personal
 * data, so a rejection names the field and its rule, never the value.
 */
class RecipientTest {

    private static final List<String> TEXT_FIELDS = List.of("name", "addressLine1", "addressLine2", "city");
    /** U+1D538, one code point that Java holds as two chars. */
    private static final String WIDE = "𝔸";

    @Test
    void theContractsExampleIsARecipientWithOrWithoutASecondAddressLine() {
        assertThat(RECIPIENT.addressLine2()).isNull();
        assertThat(with("addressLine2", "Floor 3").addressLine2()).isEqualTo("Floor 3");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"name, 70", "addressLine1, 70", "addressLine2, 70", "city, 50"})
    void aTextFieldHoldsOneToItsMaximumOfCharactersCountedInCodePoints(String field, int max) {
        assertThat(with(field, "x")).isNotNull();
        assertThat(with(field, "x".repeat(max))).isNotNull();
        assertThat(with(field, WIDE.repeat(max))).isNotNull();
        for (String value : List.of("", "x".repeat(max + 1), WIDE.repeat(max + 1))) {
            assertThatThrownBy(() -> with(field, value)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(field + " must be 1 to " + max + " characters, none of them a control character.");
        }
    }

    @ParameterizedTest(name = "code point {0}")
    @ValueSource(ints = {0x00, 0x09, 0x0A, 0x0D, 0x1F, 0x7F})
    void aControlCharacterInATextFieldIsRejected(int control) {
        for (String field : TEXT_FIELDS) {
            String value = "Synthetic" + Character.toString(control) + "Value";
            assertThatThrownBy(() -> with(field, value)).as(field).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void lettersBeyondAsciiAreAccepted() {
        for (String field : TEXT_FIELDS) {
            assertThat(with(field, "Synthetic ÆØÅ æøå é")).as(field).isNotNull();
            // A character beyond U+FFFF is a surrogate pair, and a pair is one well-formed character.
            assertThat(with(field, "Synthetic " + WIDE + " Recipient").toString()).as(field).contains(WIDE);
        }
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("loneSurrogates")
    void aLoneSurrogateInATextFieldIsRejected(String field, String where, String value) {
        assertThatThrownBy(() -> with(field, value)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(field + " must be well-formed Unicode, without a lone surrogate.");
    }

    /**
     * Each text field with a lone high or low surrogate: half of a pair that encodes a character beyond U+FFFF, alone,
     * beside text or beside another surrogate. A JSON escape can carry one, and UTF-8 cannot encode it.
     */
    static Stream<Arguments> loneSurrogates() {
        return TEXT_FIELDS.stream().flatMap(field -> Stream.of(
                Arguments.of(field, "a high surrogate alone", "\uD800"),
                Arguments.of(field, "a low surrogate alone", "\uDC00"),
                Arguments.of(field, "a high surrogate at the end", "Synthetic\uD835"),
                Arguments.of(field, "a low surrogate at the start", "\uDD38Synthetic"),
                Arguments.of(field, "a low surrogate before a high one", "Synthetic \uDD38\uD835"),
                Arguments.of(field, "a high surrogate before a pair", "Synthetic \uD835" + WIDE)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"11122", "0150", "111 22", "AB-12", "1A", "1234567890"})
    void aPostcodeOfTheContractsPatternIsAccepted(String postcode) {
        assertThat(with("postcode", postcode).postcode()).isEqualTo(postcode);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1", "12345678901", "-1122", "1122-", " 1122", "1122 ", "a1122", "11122\n"})
    void aPostcodeOutsideTheContractsPatternIsRejected(String postcode) {
        assertThatThrownBy(() -> with("postcode", postcode)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("postcode must be 2 to 10 upper-case letters, digits, spaces or hyphens, starting and "
                        + "ending with a letter or digit.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "S", "se", "Se", "SWE", "S1", "SE "})
    void aCountryCodeOtherThanTwoUpperCaseLettersIsRejected(String countryCode) {
        assertThatThrownBy(() -> with("countryCode", countryCode)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("countryCode must be two upper-case letters (ISO 3166-1 alpha-2).");
    }

    @Test
    void anyWellFormedCountryCodeIsAcceptedNotOnlyTheServedOnes() {
        assertThat(with("countryCode", "GB").countryCode()).isEqualTo("GB");
    }

    @Test
    void aRejectionNamesTheFieldAndItsRuleButNeverTheValue() {
        for (String field : List.of("name", "addressLine1", "addressLine2", "postcode", "city", "countryCode")) {
            assertThatThrownBy(() -> with(field, "Synthetic Private Value\u0000")).as(field)
                    .isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith(field + " must be ")
                    .hasMessageNotContaining("Synthetic Private Value");
        }
        for (String field : TEXT_FIELDS) {
            assertThatThrownBy(() -> with(field, "Synthetic Private Value\uD800")).as(field)
                    .isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith(field + " must be ")
                    .hasMessageNotContaining("Synthetic Private Value");
        }
    }

    /** The example recipient with one field set to a value. */
    private static Recipient with(String field, String value) {
        Recipient r = RECIPIENT;
        return switch (field) {
            case "name" -> new Recipient(value, r.addressLine1(), r.addressLine2(), r.postcode(), r.city(),
                    r.countryCode());
            case "addressLine1" -> new Recipient(r.name(), value, r.addressLine2(), r.postcode(), r.city(),
                    r.countryCode());
            case "addressLine2" -> new Recipient(r.name(), r.addressLine1(), value, r.postcode(), r.city(),
                    r.countryCode());
            case "postcode" -> new Recipient(r.name(), r.addressLine1(), r.addressLine2(), value, r.city(),
                    r.countryCode());
            case "city" -> new Recipient(r.name(), r.addressLine1(), r.addressLine2(), r.postcode(), value,
                    r.countryCode());
            case "countryCode" -> new Recipient(r.name(), r.addressLine1(), r.addressLine2(), r.postcode(), r.city(),
                    value);
            default -> throw new IllegalArgumentException("no field " + field);
        };
    }
}
